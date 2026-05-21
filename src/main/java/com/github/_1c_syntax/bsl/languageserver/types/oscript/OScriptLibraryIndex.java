/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2026
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Fedkin <nixel2007@gmail.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * BSL Language Server is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * BSL Language Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with BSL Language Server.
 */
package com.github._1c_syntax.bsl.languageserver.types.oscript;

import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentRemovedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.WorkspaceAddedEvent;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.types.ModuleType;
import com.github._1c_syntax.utils.Absolute;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Discovery-слой OneScript-библиотек workspace.
 * <p>
 * Никакой собственной системы типов или членов: .os-файлы библиотек
 * добавляются в {@link ServerContext} как обычные документы и обрабатываются
 * стандартными пайплайнами (SymbolTree, ReferenceIndex,
 * {@code OScriptModuleMembersProvider}).
 * <p>
 * Эта компонента отвечает только за:
 * <ul>
 *   <li>обход {@code lib.config} и convention-based библиотек,</li>
 *   <li>добавление найденных .os-файлов в {@link ServerContext},</li>
 *   <li>сопоставление {@code URI .os-файла → ModuleType} в
 *       {@link OScriptModuleTypeResolver} (нужно потому что Configuration
 *       ничего не знает про библиотечные файлы),</li>
 *   <li>хранение каталога библиотечных записей: qualifiedName, EntryKind
 *       (CLASS/MODULE), libOrigin — чтобы провайдеры completion могли
 *       применять gating по {@code #Использовать}.</li>
 * </ul>
 */
@Slf4j
@Component
@Scope(value = WorkspaceScope.SCOPE_NAME, proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class OScriptLibraryIndex {

  private final LibConfigDiscovery libConfigDiscovery;
  private final LibConfigParser libConfigParser;
  private final ConventionalLibraryDiscovery conventionalLibraryDiscovery;
  private final OScriptModuleTypeResolver oScriptModuleTypeResolver;
  // Материализуем members-provider в том же workspace-scope: иначе его
  // @EventListener'ы не подпишутся до того, как мы начнём rebuildDocument().
  private final OScriptModuleMembersProvider oScriptModuleMembersProvider;

  /** URI .os-файла → список записей о его регистрации. У одного .os-файла может быть
   * одновременно несколько ролей: например, в {@code lib.config} тот же файл может
   * экспортироваться и как {@code <module>}, и как {@code <class>}. */
  private final Map<URI, List<LibraryEntry>> entriesByUri = new ConcurrentHashMap<>();
  /** qualifiedName (lowercase) → запись о библиотечной сущности. */
  private final Map<String, LibraryEntry> entriesByName = new ConcurrentHashMap<>();

  /** Тип записи: класс ({@code <class>}) или модуль ({@code <module>}). */
  public enum EntryKind { MODULE, CLASS }

  /**
   * Описание зарегистрированной библиотечной записи.
   *
   * @param uri           URI .os-файла (абсолютный, нормализованный)
   * @param qualifiedName объявленное в {@code lib.config} имя
   *                      (или basename файла для convention-based)
   * @param kind          класс или модуль
   * @param libOrigin     имя библиотеки (имя каталога с {@code lib.config}),
   *                      используется gating'ом {@code #Использовать}
   */
  public record LibraryEntry(URI uri, String qualifiedName, EntryKind kind, String libOrigin) {
  }

  @EventListener
  public void handleWorkspaceAdded(WorkspaceAddedEvent event) {
    var serverContext = event.getServerContext();
    if (serverContext == null) {
      return;
    }
    try {
      reindex(serverContext);
    } catch (RuntimeException e) {
      LOGGER.warn("OScript library indexing failed for workspace {}", event.getWorkspaceUri(), e);
    }
  }

  /** Полная переиндексация OneScript-библиотек workspace. */
  public void reindex(ServerContext serverContext) {
    oScriptModuleTypeResolver.clear();
    entriesByUri.clear();
    entriesByName.clear();

    var configs = libConfigDiscovery.discover(serverContext);
    if (!configs.isEmpty()) {
      LOGGER.debug("Indexing {} OneScript library manifest(s)", configs.size());
      for (var libConfigPath : configs) {
        indexLibrary(libConfigPath, serverContext);
      }
    }

    var conventional = conventionalLibraryDiscovery.discover(serverContext, configs);
    if (!conventional.isEmpty()) {
      LOGGER.debug("Indexing {} convention-based OneScript librar(y/ies)", conventional.size());
      for (var lib : conventional) {
        indexConventional(lib, serverContext);
      }
    }

    if (configs.isEmpty() && conventional.isEmpty()) {
      LOGGER.debug("No OneScript libraries discovered for workspace");
    }
  }

  @EventListener
  public void handleDocumentRemoved(ServerContextDocumentRemovedEvent event) {
    var uri = event.getUri();
    var entries = entriesByUri.remove(uri);
    if (entries == null) {
      return;
    }
    oScriptModuleTypeResolver.unregister(uri);
    for (var entry : entries) {
      entriesByName.remove(entry.qualifiedName().toLowerCase(Locale.ROOT));
    }
    oScriptModuleMembersProvider.unregister(uri);
  }

  /** @return первая запись о .os-файле, если он зарегистрирован как библиотечный.
   *  Для случаев, когда нужны все записи (один файл = и модуль, и класс), используйте {@link #findEntriesByUri(URI)}. */
  public Optional<LibraryEntry> findByUri(URI uri) {
    var entries = entriesByUri.get(uri);
    if (entries == null || entries.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(entries.get(0));
  }

  /** @return все записи о библиотечной регистрации .os-файла. */
  public Collection<LibraryEntry> findEntriesByUri(URI uri) {
    var entries = entriesByUri.get(uri);
    return entries == null ? List.of() : List.copyOf(entries);
  }

  /** @return запись о библиотечной сущности по её qualifiedName (регистронезависимо). */
  public Optional<LibraryEntry> findByName(String qualifiedName) {
    if (qualifiedName == null || qualifiedName.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(entriesByName.get(qualifiedName.toLowerCase(Locale.ROOT)));
  }

  /** @return URI .os-файла зарегистрированного library-класса/модуля. */
  public Optional<URI> findUri(String qualifiedName) {
    return findByName(qualifiedName).map(LibraryEntry::uri);
  }

  /** @return URI .os-файла зарегистрированного library-класса. */
  public Optional<URI> findClassUri(String qualifiedName) {
    return findByName(qualifiedName)
      .filter(e -> e.kind() == EntryKind.CLASS)
      .map(LibraryEntry::uri);
  }

  /** @return URI .os-файла зарегистрированного library-модуля. */
  public Optional<URI> findModuleUri(String qualifiedName) {
    return findByName(qualifiedName)
      .filter(e -> e.kind() == EntryKind.MODULE)
      .map(LibraryEntry::uri);
  }

  /**
   * @param kind фильтр по типу записи (CLASS/MODULE)
   * @return все зарегистрированные записи указанного типа
   */
  public Collection<LibraryEntry> findEntries(EntryKind kind) {
    return entriesByName.values().stream()
      .filter(e -> e.kind() == kind)
      .toList();
  }

  /** @return все зарегистрированные записи (классы и модули вперемешку). */
  public Collection<LibraryEntry> allEntries() {
    return List.copyOf(entriesByName.values());
  }

  private void indexConventional(ConventionalLibraryDiscovery.ConventionalLibrary lib, ServerContext serverContext) {
    var libOrigin = libOriginOf(lib.root());
    for (var classFile : lib.classFiles()) {
      registerEntry(ConventionalLibraryDiscovery.entryName(classFile), classFile, EntryKind.CLASS, serverContext, libOrigin);
    }
    for (var moduleFile : lib.moduleFiles()) {
      registerEntry(ConventionalLibraryDiscovery.entryName(moduleFile), moduleFile, EntryKind.MODULE, serverContext, libOrigin);
    }
  }

  private void indexLibrary(Path libConfigPath, ServerContext serverContext) {
    var libRoot = libConfigPath.getParent();
    if (libRoot == null) {
      return;
    }
    var libOrigin = libOriginOf(libRoot);
    var manifest = libConfigParser.parse(libConfigPath);
    for (var module : manifest.modules()) {
      var osFile = libRoot.resolve(module.file()).toAbsolutePath().normalize();
      registerEntry(module.name(), osFile, EntryKind.MODULE, serverContext, libOrigin);
    }
    for (var klass : manifest.classes()) {
      var osFile = libRoot.resolve(klass.file()).toAbsolutePath().normalize();
      registerEntry(klass.name(), osFile, EntryKind.CLASS, serverContext, libOrigin);
    }
  }

  private void registerEntry(String qualifiedName, Path osFile, EntryKind kind, ServerContext serverContext, String libOrigin) {
    var uri = Absolute.uri(osFile.toUri());
    var moduleType = kind == EntryKind.CLASS ? ModuleType.OScriptClass : ModuleType.OScriptModule;
    // Сначала сообщаем резолверу тип модуля — это нужно, чтобы при первом
    // событии DocumentContextContentChangedEvent документ уже знал свой
    // ModuleType (через DocumentContext.computeModuleType фолбэк).
    // Если одна и та же URI регистрируется и как модуль, и как класс,
    // OScriptModuleTypeResolver сохранит ПЕРВЫЙ зарегистрированный тип
    // (см. register(...)) — это намеренно: иначе документ переменно бы
    // менял свою идентичность в ServerContext.
    oScriptModuleTypeResolver.register(uri, moduleType);

    var entry = new LibraryEntry(uri, qualifiedName, kind, libOrigin);
    entriesByUri.computeIfAbsent(uri, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(entry);
    entriesByName.put(qualifiedName.toLowerCase(Locale.ROOT), entry);

    // Добавляем .os-файл в ServerContext как обычный документ. SymbolTreeComputer,
    // ReferenceIndexFiller, OScriptModuleMembersProvider и прочие подхватят его
    // через события.
    try {
      var dc = serverContext.addDocument(uri);
      serverContext.rebuildDocument(dc);
      // Явный вызов: гарантирует регистрацию USER-типа в актуальном
      // workspace-scope (event-listener тоже сработает, но он не
      // обязан выполняться в том же scope/потоке, что и reindex).
      oScriptModuleMembersProvider.register(dc);
      // Освобождаем тяжёлые secondary-данные (content, tokens, tokenizer, queries, moduleType).
      // SymbolTree сохраняется в DocumentContext (clearSecondaryData его не трогает) и доступен
      // последующим collectMembers/collectConstructors. Без этого на больших oscript_modules
      // (десятки/сотни файлов на rebuild) JVM удерживает все токенизаторы и тесты падают по OOM.
      serverContext.tryClearDocument(dc);
    } catch (RuntimeException e) {
      LOGGER.warn("Failed to load oscript library file: {}", osFile, e);
    }
  }

  @Nullable
  private static String libOriginOf(@Nullable Path libRoot) {
    if (libRoot == null) {
      return null;
    }
    var fileName = libRoot.getFileName();
    return fileName == null ? null : fileName.toString();
  }
}
