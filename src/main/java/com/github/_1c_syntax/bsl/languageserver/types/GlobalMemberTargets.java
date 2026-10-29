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
package com.github._1c_syntax.bsl.languageserver.types;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver;
import com.github._1c_syntax.bsl.languageserver.types.registry.GlobalScopeProvider;
import com.github._1c_syntax.bsl.languageserver.types.TypeService.TypedMember;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.types.ModuleType;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Экспортные и платформенные цели голого имени в рамках проверки одного файла. */
final class GlobalMemberTargets {
  private final DocumentContext documentContext;
  private final CompilationContextResolver contextResolver;
  private final GlobalScopeProvider globalScopeProvider;
  private final Map<String, List<MemberCallTarget>> globalTargets = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
  private final List<DocumentContext> globalModules = new ArrayList<>();
  private boolean globalsCollected;

  GlobalMemberTargets(DocumentContext document, CompilationContextResolver contexts, GlobalScopeProvider globals) {
    documentContext = document;
    contextResolver = contexts;
    globalScopeProvider = globals;
  }

  TypedMember platformFunction(String name, TerminalNode node) {
    var member = globalScopeProvider.globalFunction(name, documentContext.getFileType()).orElseThrow();
    return new TypedMember(null, member, Ranges.create(node));
  }

  /** Найти альтернативные экспортные объявления глобальных модулей и модулей приложения/соединения. */
  List<MemberCallTarget> sourceTargets(String name) {
    if (!globalsCollected) {
      collectGlobalModules();
      globalsCollected = true;
    }
    return globalTargets.computeIfAbsent(name, this::exportedGlobalTargets);
  }

  private void collectGlobalModules() {
    var serverContext = documentContext.getServerContext();
    for (var module : serverContext.getConfiguration().getCommonModules()) {
      if (module.isGlobal()) {
        serverContext.getDocument(module.getMdoRef(), ModuleType.CommonModule).ifPresent(globalModules::add);
      }
    }
    var configuration = serverContext.getConfiguration().getMergedConfiguration();
    for (var type : List.of(ModuleType.OrdinaryApplicationModule, ModuleType.ManagedApplicationModule,
      ModuleType.ExternalConnectionModule)) {
      serverContext.getDocument(configuration.getMdoRef(), type).ifPresent(globalModules::add);
    }
  }

  private List<MemberCallTarget> exportedGlobalTargets(String name) {
    List<MemberCallTarget> targets = new ArrayList<>();
    for (var owner : globalModules) {
      for (var definition : contextResolver.declarations(owner).getOrDefault(name, List.of())) {
        if (definition.exported()) {
          targets.add(new MemberCallTarget(owner, definition));
        }
      }
    }
    return List.copyOf(targets);
  }

  boolean isPlatformFunction(String name) {
    return globalScopeProvider.globalFunction(name, documentContext.getFileType())
      .filter(member -> member.getSourceSymbol().isEmpty()).isPresent();
  }
}
