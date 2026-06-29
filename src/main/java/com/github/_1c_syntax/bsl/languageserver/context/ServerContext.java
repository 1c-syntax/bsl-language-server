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
package com.github._1c_syntax.bsl.languageserver.context;

import com.github._1c_syntax.bsl.languageserver.client.WorkDoneProgressHelper;
import com.github._1c_syntax.bsl.languageserver.configuration.GlobalLanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.utils.BSLFiles;
import com.github._1c_syntax.bsl.languageserver.configuration.Resources;
import com.github._1c_syntax.bsl.mdclasses.CF;
import com.github._1c_syntax.bsl.mdclasses.MDCReadSettings;
import com.github._1c_syntax.bsl.mdclasses.MDClasses;
import com.github._1c_syntax.bsl.mdo.CommonModule;
import com.github._1c_syntax.bsl.types.ModuleType;
import com.github._1c_syntax.utils.Absolute;
import com.github._1c_syntax.utils.Lazy;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Контекст сервера - центральное хранилище информации о рабочей области.
 * <p>
 * Управляет коллекцией всех документов проекта, метаданными конфигурации 1С,
 * обеспечивает доступ к контекстам отдельных документов и их синхронизацию.
 */
@Slf4j
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class ServerContext {
  private static final MDCReadSettings SOLUTION_READ_SETTINGS = MDCReadSettings.builder()
    .skipDataCompositionSchema(true)
    .skipXdtoPackage(true)
    .build();

  private final ObjectProvider<DocumentContext> documentContextProvider;
  private final WorkDoneProgressHelper workDoneProgressHelper;
  private final GlobalLanguageServerConfiguration globalConfiguration;
  @Qualifier("computeConfigurationExecutor")
  private final ExecutorService computeConfigurationExecutor;
  @Qualifier("populateContextExecutor")
  private final ExecutorService populateContextExecutor;

  /**
   * Ограниченный кэш резолва общего модуля по имени ({@code имя -> Optional<CommonModule>},
   * кэшируются и промахи) — workspace-scoped бин (см. {@code CacheConfiguration#commonModuleCache}).
   * Резолв зависит только от конфигурации воркспейса, а {@code findCommonModule} вызывается на
   * каждый идентификатор при заполнении индекса ссылок — memo снимает повторное сворачивание
   * регистра в {@code CaseInsensitiveMap} конфигурации. Сбрасывается в {@link #clear()}.
   */
  private final Cache<String, Optional<CommonModule>> commonModuleCache;

  @Getter
  @Setter
  @SuppressWarnings("NullAway.Init")
  private LanguageServerConfiguration languageServerConfiguration;

  @Getter
  @Setter
  @SuppressWarnings("NullAway.Init")
  private URI workspaceUri;

  private final Map<URI, DocumentContext> documents = new ConcurrentHashMap<>();
  private final Lazy<CF> configurationMetadata = new Lazy<>(this::computeConfigurationMetadata);
  @Nullable
  @Setter
  @Getter
  private Path configurationRoot;
  private final Map<URI, String> mdoRefs = new ConcurrentHashMap<>();
  /**
   * Внутренний EnumMap не потокобезопасен; создаётся обёрнутый в
   * {@link Collections#synchronizedMap(Map)} (см. {@link #addMdoRefByUri(URI, DocumentContext)}).
   */
  private final Map<String, Map<ModuleType, DocumentContext>> documentsByMDORef
    = new ConcurrentHashMap<>();
  /**
   * Каталог библиотечных сущностей OneScript: {@code nameKey → (каноничное имя, тип модуля, URI)}.
   * Наполняется извне (индексатором OneScript-библиотек) и служит резолву имени класса/модуля
   * библиотеки в каноничный mdoRef — аналогично тому, как {@link #findCommonModule(String)}
   * резолвит общий модуль из метаданных конфигурации. Регистронезависимый (ключ — {@code nameKey}).
   */
  private final Map<String, OScriptLibrarySymbol> oscriptLibrariesByNameKey = new ConcurrentHashMap<>();
  /** Обратный индекс {@code URI → nameKey'и записей} для снятия при удалении/пере-индексации файла. */
  private final Map<URI, Set<String>> oscriptLibraryNameKeysByUri = new ConcurrentHashMap<>();
  private final Map<URI, ReadWriteLock> documentLocks = new ConcurrentHashMap<>();

  private final Map<DocumentContext, State> states = new ConcurrentHashMap<>();
  private final Set<DocumentContext> openedDocuments = ConcurrentHashMap.newKeySet();


  public void populateContext() {
    if (configurationRoot == null) {
      LOGGER.info("Can't populate server context. Configuration root is not defined.");
      return;
    }

    var workDoneProgressReporter = workDoneProgressHelper.createProgress(0, "");
    workDoneProgressReporter.beginProgress(getMessage("populateFindFiles"));

    LOGGER.debug("Finding files to populate context...");
    var files = BSLFiles.listBslFiles(configurationRoot, languageServerConfiguration.getExcludePaths());
    workDoneProgressReporter.endProgress("");
    populateContext(files);
  }

  public void populateContext(Collection<File> files) {
    var workDoneProgressReporter = workDoneProgressHelper.createProgress(
      files.size(),
      getMessage("populateFilesPostfix")
    );
    workDoneProgressReporter.beginProgress(getMessage("populatePopulatingContext"));

    LOGGER.debug("Populating context...");

    try {
      populateContextExecutor.submit(() ->
        files.parallelStream().forEach((File file) -> {
          workDoneProgressReporter.tick();

          var uri = Absolute.uri(file.toURI());
          var lock = getDocumentLock(uri);
          lock.writeLock().lock();
          try {
            var documentContext = documents.get(uri);
            if (documentContext == null) {
              documentContext = createDocumentContext(uri);
              rebuildDocument(documentContext);
              documentContext.freezeComputedData();
              tryClearDocument(documentContext);
            }
          } finally {
            lock.writeLock().unlock();
          }
        })
      ).get();
    } catch (ExecutionException e) {
      throw new IllegalStateException("Error populating context", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while populating context", e);
    }

    workDoneProgressReporter.endProgress(getMessage("populateContextPopulated"));
    LOGGER.debug("Context populated.");
  }

  public Map<URI, DocumentContext> getDocuments() {
    return Collections.unmodifiableMap(documents);
  }

  public Optional<DocumentContext> getDocument(String mdoRef, ModuleType moduleType) {
    var documentsGroup = documentsByMDORef.get(mdoRef);
    if (documentsGroup != null) {
      return Optional.ofNullable(documentsGroup.get(moduleType));
    }
    return Optional.empty();
  }

  /**
   * Получить документ по URI.
   * <p>
   * URI должен быть уже нормализован (например, получен из DocumentContext или через Absolute.uri).
   *
   * @param uri нормализованный URI документа
   * @return Контекст документа или {@code null}, если документ не найден
   */
  @Nullable
  public DocumentContext getDocument(URI uri) {
    var lock = getDocumentLock(uri);
    lock.readLock().lock();
    try {
      return documents.get(uri);
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Получить документ по URI без захвата блокировки.
   * <p>
   * Этот метод предоставляет прямой доступ к хранилищу документов без синхронизации.
   * Используйте его только в случаях, когда:
   * <ul>
   *   <li>блокировка уже захвачена вызывающим кодом;</li>
   *   <li>операция только на чтение и допускается eventual consistency;</li>
   *   <li>критична производительность и накладные расходы на блокировку недопустимы.</li>
   * </ul>
   * <p>
   * <b>Внимание:</b> использование этого метода без внешней синхронизации может привести
   * к состояниям гонки, если другой поток одновременно модифицирует документ или коллекцию.
   *
   * @param uri нормализованный URI документа
   * @return Контекст документа или {@code null}, если документ не найден
   * @see #getDocument(URI) безопасная версия с захватом блокировки
   * @see #getDocumentLock(URI) получение блокировки для внешней синхронизации
   */
  @Nullable
  public DocumentContext getDocumentNoLock(URI uri) {
    return documents.get(uri);
  }

  /**
   * Получить документ по URI с нормализацией.
   * <p>
   * Используется для внешних вызовов (CLI, Service), где URI может быть не нормализован.
   *
   * @param uri URI документа (будет нормализован)
   * @return Контекст документа или {@code null}, если документ не найден
   */
  @Nullable
  public DocumentContext getDocumentUnsafe(URI uri) {
    return getDocument(Absolute.uri(uri));
  }


  /**
   * Получить документ по строковому URI с нормализацией.
   * <p>
   * Используется для внешних вызовов (CLI, Service), где URI может быть не нормализован.
   *
   * @param uri строковый URI документа
   * @return Контекст документа или {@code null}, если документ не найден
   */
  @Nullable
  public DocumentContext getDocumentUnsafe(String uri) {
    return getDocument(Absolute.uri(uri));
  }

  public Map<ModuleType, DocumentContext> getDocuments(String mdoRef) {
    return documentsByMDORef.getOrDefault(mdoRef, Collections.emptyMap());
  }

  /**
   * Добавить документ в контекст.
   * <p>
   * URI должен быть уже нормализован.
   *
   * @param uri нормализованный URI документа
   * @return Контекст документа
   */
  public DocumentContext addDocument(URI uri) {
    var lock = getDocumentLock(uri);
    lock.writeLock().lock();
    try {
      var documentContext = documents.get(uri);
      if (documentContext == null) {
        documentContext = createDocumentContext(uri);
      }
      return documentContext;
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * Удалить документ из контекста.
   * <p>
   * URI должен быть уже нормализован.
   *
   * @param uri нормализованный URI документа
   */
  public void removeDocument(URI uri) {
    var documentContext = documents.get(uri);
    if (openedDocuments.contains(documentContext)) {
      throw new IllegalStateException("Document %s is opened".formatted(uri));
    }

    removeDocumentMdoRefByUri(uri);
    removeOScriptDocumentMdoRefs(uri);
    states.remove(documentContext);
    documents.remove(uri);
    documentLocks.remove(uri);
  }

  /**
   * Удалить все документы из контекста.
   * <p>
   * Каждый документ удаляется через {@link #removeDocument(URI)} — это даёт
   * {@code ServerContextDocumentRemovedEvent} через AOP-аспект, на который
   * подписаны downstream-индексы ({@link com.github._1c_syntax.bsl.languageserver.references.ReferenceIndex},
   * {@link com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex}
   * и прочие singleton'ы со state, индексирующим документы).
   * <p>
   * Open-документы сначала «закрываются» сбросом {@code openedDocuments}, чтобы
   * {@link #removeDocument(URI)} не падал на guard'е «document is opened».
   * После — финальная очистка карт, не привязанных к конкретному URI
   * ({@code configurationMetadata}).
   */
  public void clear() {
    openedDocuments.clear();
    for (var uri : new ArrayList<>(documents.keySet())) {
      removeDocument(uri);
    }
    // safety net: если какие-то карты не очистились через removeDocument'ы
    // (например, документ был оторван от mdoRef'а), убираем что осталось.
    documents.clear();
    states.clear();
    documentsByMDORef.clear();
    mdoRefs.clear();
    oscriptLibrariesByNameKey.clear();
    oscriptLibraryNameKeysByUri.clear();
    documentLocks.clear();
    commonModuleCache.invalidateAll();
    configurationMetadata.clear();
  }

  /**
   * Получить блокировку для выполнения операций над документом по URI.
   * <p>
   * Может использоваться для операций, требующих долгосрочной установки блокировки на изменение объекта.
   *
   * @param uri URI документа
   * @return блокировка, связанная с URI документа.
   */
  public ReadWriteLock getDocumentLock(URI uri) {
    return documentLocks.computeIfAbsent(uri, k -> new ReentrantReadWriteLock());
  }

  /**
   * Помечает документ как открытый и перестраивает его содержимое
   * <p>
   * Документы, помеченные как открытые, не будут удаляться из контекста сервера при вызове {@link #removeDocument(URI)},
   * а так же не будут очищаться при вызове {@link #tryClearDocument(DocumentContext)}.
   * <p>
   * Если вспомогательные данные документа был в "замороженном" состоянии, то перед перестроением документа
   * они будут разморожены.
   *
   * @param documentContext документ, который необходимо открыть.
   * @param content         новое содержимое документа.
   * @param version         версия документа.
   */
  public void openDocument(DocumentContext documentContext, String content, Integer version) {
    openedDocuments.add(documentContext);
    documentContext.unfreezeComputedData();
    rebuildDocument(documentContext, content, version);
  }

  /**
   * Проверяет, открыт ли документ в редакторе.
   * <p>
   * Открытые документы управляются клиентом через события textDocument/didOpen,
   * textDocument/didChange и textDocument/didClose. Для таких документов содержимое
   * хранится в памяти сервера и может отличаться от содержимого файла на диске.
   * <p>
   * Открытые документы не будут удалены при вызове {@link #removeDocument(URI)}
   * и не будут очищены при вызове {@link #tryClearDocument(DocumentContext)}.
   *
   * @param documentContext документ для проверки
   * @return {@code true}, если документ открыт в редакторе, {@code false} в противном случае
   */
  public boolean isDocumentOpened(DocumentContext documentContext) {
    return openedDocuments.contains(documentContext);
  }

  /**
   * Возвращает иммутабельный снимок открытых документов workspace
   * (т.е. тех, для которых клиент прислал {@code textDocument/didOpen}
   * и не прислал {@code textDocument/didClose}).
   */
  public Set<DocumentContext> getOpenedDocuments() {
    return Set.copyOf(openedDocuments);
  }

  /**
   * Перестроить документ. В качестве содержимого будут использоваться данные,
   * прочитанные из файла, с которым связан документ.
   *
   * @param documentContext документ, который необходимо перестроить.
   */
  public void rebuildDocument(DocumentContext documentContext) {
    if (states.get(documentContext) == State.WITH_CONTENT) {
      return;
    }

    documentContext.rebuildFromFileSystem();
    states.put(documentContext, State.WITH_CONTENT);
  }

  /**
   * Перестроить документ, используя новое содержимое.
   *
   * @param documentContext документ, который необходимо перестроить.
   * @param content         новое содержимое документа.
   * @param version         версия документа.
   */
  public void rebuildDocument(DocumentContext documentContext, String content, Integer version) {
    documentContext.rebuild(content, version);
    states.put(documentContext, State.WITH_CONTENT);
  }

  /**
   * Попытаться очистить документ, если он не открыт.
   *
   * @param documentContext документ, который необходимо попытаться закрыть.
   * @return {@code true}, если вторичные данные документа были реально освобождены;
   *         {@code false}, если документ открыт в редакторе и очистка пропущена.
   */
  public boolean tryClearDocument(DocumentContext documentContext) {
    if (openedDocuments.contains(documentContext)) {
      return false;
    }

    states.put(documentContext, State.WITHOUT_CONTENT);
    documentContext.clearSecondaryData();
    return true;
  }

  /**
   * Закрыть документ и очистить его содержимое.
   *
   * @param documentContext документ, который необходимо закрыть.
   */
  public void closeDocument(DocumentContext documentContext) {
    openedDocuments.remove(documentContext);
    states.put(documentContext, State.WITHOUT_CONTENT);
    documentContext.clearSecondaryData();
  }

  public CF getConfiguration() {
    return configurationMetadata.getOrCompute();
  }

  /**
   * Найти общий модуль по имени с мемоизацией (ограниченный кэш {@link #commonModuleCache}).
   * Эквивалентно {@code getConfiguration().findCommonModule(name)}, но без повторного прохода
   * по case-insensitive карте конфигурации на каждый вызов.
   * <p>
   * Ключ кэша — сырой текст идентификатора (намеренно не нормализуется): на попадании это дешёвый
   * lookup без сворачивания регистра — ровно то, ради чего кэш и нужен. {@code toLowerCase} на
   * каждый вызов вернул бы посимвольное сворачивание + аллокацию строки на горячий путь, а
   * экономия (схлопывание редких регистровых вариантов одного имени) — околонулевая. Сам резолв
   * внутри остаётся регистронезависимым.
   *
   * @param name имя общего модуля
   * @return общий модуль или {@link Optional#empty()}, если такого нет
   */
  public Optional<CommonModule> findCommonModule(String name) {
    return commonModuleCache.get(name, key -> getConfiguration().findCommonModule(key));
  }

  /**
   * Зарегистрировать библиотечную сущность OneScript (класс/модуль) в каталоге.
   * Вызывается индексатором OneScript-библиотек.
   *
   * @param qualifiedName каноничное имя сущности
   * @param moduleType    {@link ModuleType#OScriptClass} или {@link ModuleType#OScriptModule}
   * @param uri           URI .os-файла
   */
  public void registerOScriptLibrarySymbol(String qualifiedName, ModuleType moduleType, URI uri) {
    var key = oscriptNameKey(qualifiedName);
    oscriptLibrariesByNameKey.put(key, new OScriptLibrarySymbol(qualifiedName, moduleType, uri));
    oscriptLibraryNameKeysByUri.computeIfAbsent(uri, k -> ConcurrentHashMap.newKeySet()).add(key);
    registerOScriptDocumentMdoRefs(uri);
  }

  /**
   * Снять все библиотечные записи OneScript, связанные с файлом (при удалении/пере-индексации).
   */
  public void removeOScriptLibrarySymbolsByUri(URI uri) {
    // сначала снимаем регистрации документа в индексе по mdoRef (пока каталог ещё населён),
    // потом чистим сам каталог.
    removeOScriptDocumentMdoRefs(uri);
    var keys = oscriptLibraryNameKeysByUri.remove(uri);
    if (keys != null) {
      keys.forEach(oscriptLibrariesByNameKey::remove);
    }
  }

  /**
   * Зарегистрировать .os-документ в индексе по mdoRef под каждым его каноничным lib-именем
   * (если документ уже создан). Идемпотентно; вызывается и при создании документа, и при
   * наполнении каталога — порядок этих событий не фиксирован.
   */
  private void registerOScriptDocumentMdoRefs(URI uri) {
    var documentContext = documents.get(uri);
    var keys = oscriptLibraryNameKeysByUri.get(uri);
    if (documentContext == null || keys == null) {
      return;
    }
    for (var key : keys) {
      var symbol = oscriptLibrariesByNameKey.get(key);
      if (symbol != null) {
        documentsByMDORef
          .computeIfAbsent(symbol.qualifiedName(),
            k -> Collections.synchronizedMap(new EnumMap<>(ModuleType.class)))
          .put(symbol.moduleType(), documentContext);
      }
    }
  }

  /**
   * Полностью очистить каталог библиотечных сущностей OneScript и снять связанные регистрации
   * документов в индексе по mdoRef. Вызывается перед полной пере-индексацией библиотек.
   */
  public void clearOScriptLibrarySymbols() {
    for (var uri : new ArrayList<>(oscriptLibraryNameKeysByUri.keySet())) {
      removeOScriptDocumentMdoRefs(uri);
    }
    oscriptLibrariesByNameKey.clear();
    oscriptLibraryNameKeysByUri.clear();
  }

  /**
   * Снять регистрации .os-документа в индексе по mdoRef под его lib-именами.
   */
  private void removeOScriptDocumentMdoRefs(URI uri) {
    var keys = oscriptLibraryNameKeysByUri.get(uri);
    if (keys == null) {
      return;
    }
    for (var key : keys) {
      var symbol = oscriptLibrariesByNameKey.get(key);
      if (symbol != null) {
        var group = documentsByMDORef.get(symbol.qualifiedName());
        if (group != null) {
          group.remove(symbol.moduleType());
          if (group.isEmpty()) {
            documentsByMDORef.remove(symbol.qualifiedName());
          }
        }
      }
    }
  }

  /**
   * Найти каноничное имя зарегистрированного library-класса OneScript по имени из исходного кода.
   *
   * @param name имя из кода (произвольный регистр)
   * @return каноничное имя класса либо {@code empty}
   */
  public Optional<String> findLibraryClass(String name) {
    return findOScriptLibrarySymbol(name, ModuleType.OScriptClass);
  }

  /**
   * Найти каноничное имя зарегистрированного library-модуля OneScript по имени из исходного кода.
   *
   * @param name имя из кода (произвольный регистр)
   * @return каноничное имя модуля либо {@code empty}
   */
  public Optional<String> findLibraryModule(String name) {
    return findOScriptLibrarySymbol(name, ModuleType.OScriptModule);
  }

  private Optional<String> findOScriptLibrarySymbol(String name, ModuleType moduleType) {
    if (name == null || name.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(oscriptLibrariesByNameKey.get(oscriptNameKey(name)))
      .filter(symbol -> symbol.moduleType() == moduleType)
      .map(OScriptLibrarySymbol::qualifiedName);
  }

  /**
   * Нормализация имени OneScript-сущности для регистронезависимого сравнения.
   * Должна совпадать с {@code OScriptLibraryIndex.nameKey}: NFC + lower-case (Locale.ROOT) —
   * имена в .os-файлах хранятся в NFD, а в коде набираются в NFC.
   */
  private static String oscriptNameKey(String name) {
    return Normalizer.normalize(name, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
  }

  /**
   * Запись каталога библиотечных сущностей OneScript.
   *
   * @param qualifiedName каноничное имя
   * @param moduleType    тип модуля (класс/модуль)
   * @param uri           URI .os-файла
   */
  public record OScriptLibrarySymbol(String qualifiedName, ModuleType moduleType, URI uri) {
  }

  private DocumentContext createDocumentContext(URI uri) {
    var documentContext = documentContextProvider.getObject(uri, this);

    documents.put(uri, documentContext);
    addMdoRefByUri(uri, documentContext);
    // .os-документы библиотек дополнительно индексируются по своим каноничным lib-именам,
    // если каталог уже населён индексатором (порядок не фиксирован — см. метод).
    registerOScriptDocumentMdoRefs(uri);

    return documentContext;
  }

  private CF computeConfigurationMetadata() {
    if (configurationRoot == null) {
      return (CF) MDClasses.createConfiguration();
    }

    var progress = workDoneProgressHelper.createProgress(0, "");
    progress.beginProgress(getMessage("computeConfigurationMetadata"));

    CF configuration;
    try {
      configuration = (CF) computeConfigurationExecutor.submit(
        () -> MDClasses.createSolution(configurationRoot, SOLUTION_READ_SETTINGS)).get();
    } catch (ExecutionException e) {
      LOGGER.error("Can't parse configuration metadata. Execution exception: {}", e.getMessage(), e);
      configuration = (CF) MDClasses.createConfiguration();
    } catch (InterruptedException e) {
      LOGGER.error("Can't parse configuration metadata. Interrupted exception: {}", e.getMessage(), e);
      configuration = (CF) MDClasses.createConfiguration();
      Thread.currentThread().interrupt();
    }

    progress.endProgress(getMessage("computeConfigurationMetadataDone"));

    return configuration;
  }

  /**
   * Регистрирует документ в индексе по {@code mdoRef}.
   * Внутренний {@link EnumMap} оборачивается в
   * {@link Collections#synchronizedMap(Map)}, чтобы конкурентные {@code put}
   * для разных типов модулей одного объекта были безопасны.
   */
  private void addMdoRefByUri(URI uri, DocumentContext documentContext) {
    var mdoRef = documentContext.getMdoRef();

    mdoRefs.put(uri, mdoRef);
    documentsByMDORef.computeIfAbsent(
      mdoRef,
      k -> Collections.synchronizedMap(new EnumMap<>(ModuleType.class))
    ).put(documentContext.getModuleType(), documentContext);
  }

  private void removeDocumentMdoRefByUri(URI uri) {
    var mdoRef = mdoRefs.get(uri);
    if (mdoRef != null) {
      var documentsGroup = documentsByMDORef.get(mdoRef);
      if (documentsGroup != null) {
        documentsGroup.remove(documents.get(uri).getModuleType());
        if (documentsGroup.isEmpty()) {
          documentsByMDORef.remove(mdoRef);
        }
      }
      mdoRefs.remove(uri);
    }
  }

  private String getMessage(String key) {
    return Resources.getResourceString(globalConfiguration.getLanguage(), getClass(), key);
  }

  /**
   * Состояние документа в контексте.
   */
  private enum State {
    /**
     * В документе отсутствует контент или он был очищен.
     */
    WITHOUT_CONTENT,
    /**
     * В документе присутствует контент.
     */
    WITH_CONTENT
  }

}
