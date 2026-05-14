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
import com.github._1c_syntax.bsl.languageserver.context.events.WorkspaceAddedEvent;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.registry.GlobalScopeProvider;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Координатор индексации OneScript-библиотек workspace'а.
 * <p>
 * Связывает {@link LibConfigDiscovery} → {@link LibConfigParser} →
 * {@link OScriptLibraryFileParser} с {@link TypeRegistry} и
 * {@link GlobalScopeProvider}:
 * <ul>
 *   <li>записи {@code <module>} регистрируются как user-типы и
 *       объявляются как глобальные имена-namespace'ы (через
 *       {@link GlobalScopeProvider#registerLibraryModule(String, TypeRef)});</li>
 *   <li>записи {@code <class>} регистрируются как user-типы (доступны в
 *       {@code Новый MyClass(...)}) и публикуют сигнатуры конструктора
 *       в {@link GlobalScopeProvider#registerLibraryClass(String, List)}.</li>
 * </ul>
 * Запускается на {@link WorkspaceAddedEvent}; повторный {@link #reindex(ServerContext)}
 * допустим — все ранее зарегистрированные library-сущности предварительно
 * очищаются.
 */
@Slf4j
@Component
@Scope(value = WorkspaceScope.SCOPE_NAME, proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class OScriptLibraryIndex {

  private final LibConfigDiscovery libConfigDiscovery;
  private final LibConfigParser libConfigParser;
  private final OScriptLibraryFileParser libraryFileParser;
  private final ConventionalLibraryDiscovery conventionalLibraryDiscovery;
  private final TypeRegistry typeRegistry;
  private final GlobalScopeProvider globalScopeProvider;

  /**
   * Реакция на добавление workspace: разово (или повторно при ручном вызове
   * {@link #reindex(ServerContext)}) проиндексировать все его OneScript-библиотеки.
   */
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

  /**
   * Полная переиндексация OneScript-библиотек указанного workspace.
   */
  public void reindex(ServerContext serverContext) {
    globalScopeProvider.clearLibraryEntries();

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

  private void indexConventional(ConventionalLibraryDiscovery.ConventionalLibrary lib, ServerContext serverContext) {
    for (var classFile : lib.classFiles()) {
      registerClassFromFile(ConventionalLibraryDiscovery.entryName(classFile), classFile, serverContext);
    }
    for (var moduleFile : lib.moduleFiles()) {
      registerModuleFromFile(ConventionalLibraryDiscovery.entryName(moduleFile), moduleFile, serverContext);
    }
  }

  private void indexLibrary(Path libConfigPath, ServerContext serverContext) {
    var libRoot = libConfigPath.getParent();
    if (libRoot == null) {
      return;
    }
    var manifest = libConfigParser.parse(libConfigPath);

    for (var module : manifest.modules()) {
      registerModule(libRoot, module, serverContext);
    }
    for (var klass : manifest.classes()) {
      registerClass(libRoot, klass, serverContext);
    }
  }

  private void registerModule(Path libRoot, LibConfigParser.LibEntry entry, ServerContext serverContext) {
    var osFile = libRoot.resolve(entry.file()).toAbsolutePath().normalize();
    registerModuleFromFile(entry.name(), osFile, serverContext);
  }

  private void registerClass(Path libRoot, LibConfigParser.LibEntry entry, ServerContext serverContext) {
    var osFile = libRoot.resolve(entry.file()).toAbsolutePath().normalize();
    registerClassFromFile(entry.name(), osFile, serverContext);
  }

  private void registerModuleFromFile(String qualifiedName, Path osFile, ServerContext serverContext) {
    var parsed = libraryFileParser.parse(osFile, serverContext);
    if (parsed.isEmpty()) {
      return;
    }
    var ref = typeRegistry.registerUserType(qualifiedName, null);
    var members = collectMembers(parsed.get());
    typeRegistry.registerMemberSource(ref, () -> members);
    globalScopeProvider.registerLibraryModule(qualifiedName, ref);
  }

  private void registerClassFromFile(String qualifiedName, Path osFile, ServerContext serverContext) {
    var parsed = libraryFileParser.parse(osFile, serverContext);
    if (parsed.isEmpty()) {
      return;
    }
    var ref = typeRegistry.registerUserType(qualifiedName, null);
    var members = collectMembers(parsed.get());
    typeRegistry.registerMemberSource(ref, () -> members);

    var ctorSignatures = parsed.get().constructor()
      .map(c -> withReturnType(c.signatures(), ref))
      .orElse(List.<SignatureDescriptor>of());
    globalScopeProvider.registerLibraryClass(qualifiedName, ctorSignatures);
  }

  private static Collection<MemberDescriptor> collectMembers(OScriptLibraryFileParser.OScriptLibraryFile file) {
    var members = new ArrayList<MemberDescriptor>();
    for (var method : file.exportMethods()) {
      var ret = method.signatures().isEmpty()
        ? TypeRef.UNKNOWN
        : method.signatures().get(0).returnType();
      members.add(new MemberDescriptor(
        method.name(), MemberKind.METHOD, "", ret, method.signatures()
      ));
    }
    for (var v : file.exportVars()) {
      members.add(MemberDescriptor.property(v));
    }
    return members;
  }

  /**
   * Для конструктора возвращаемый тип — это сам класс; в распарсенной сигнатуре
   * метода {@code ПриСозданииОбъекта} он {@link TypeRef#UNKNOWN}.
   */
  private static List<SignatureDescriptor> withReturnType(List<SignatureDescriptor> raw, TypeRef classRef) {
    if (raw == null || raw.isEmpty()) {
      return List.of(new SignatureDescriptor(List.of(), classRef, ""));
    }
    var result = new ArrayList<SignatureDescriptor>(raw.size());
    for (var sig : raw) {
      result.add(new SignatureDescriptor(sig.parameters(), classRef, sig.description()));
    }
    return result;
  }
}
