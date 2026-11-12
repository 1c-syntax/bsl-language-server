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
package com.github._1c_syntax.bsl.languageserver.completion;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.CompilerDirectiveKind;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver.Declaration;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver.MethodContext;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Объявления и профили исходных целей в пределах одного запроса completion. */
final class CompletionMethodTargets {
  private final CompilationContextResolver resolver;
  private final Map<MethodSymbol, List<MethodTarget>> methodTargets = new IdentityHashMap<>();
  private final Map<ProfileKey, Optional<MethodContext>> profiles = new HashMap<>();
  private final Map<DocumentContext, Map<String, List<Declaration>>> declarationsByOwner = new IdentityHashMap<>();
  private final Map<DocumentContext, Map<Declaration, MethodTarget>> targetsByOwner = new IdentityHashMap<>();

  /**
   * Порядок ключа согласован с URI и необязательной директивой.
   */
  private record ProfileKey(URI uri, Optional<CompilerDirectiveKind> directive) implements Comparable<ProfileKey> {
    @Override
    public int compareTo(ProfileKey other) {
      var byUri = uri.compareTo(other.uri);
      return byUri == 0 ? Integer.compare(directiveOrdinal(), other.directiveOrdinal()) : byUri;
    }

    private int directiveOrdinal() {
      return directive.isPresent() ? directive.orElseThrow().ordinal() : -1;
    }
  }

  /**
   * Объявление и вычисленные один раз профиль и контексты его компиляции.
   */
  record MethodTarget(DocumentContext owner, Declaration declaration, Optional<MethodContext> profile,
                              Optional<Set<ExecutionContext>> contexts) {
  }

  CompletionMethodTargets(CompilationContextResolver resolver) {
    this.resolver = resolver;
  }

  List<MethodTarget> localMethod(MethodSymbol method) {
    var owner = method.getOwner();
    return List.of(target(owner,
      new Declaration(method.getCompilerDirectiveKind(),
        owner.getSymbolTreeNoLock().getMethodPreprocessorContexts(method), method.isExport())));
  }

  List<MethodTarget> forMethod(MethodSymbol method) {
    return methodTargets.computeIfAbsent(method, this::computeTargets);
  }

  private List<MethodTarget> computeTargets(MethodSymbol method) {
    List<MethodTarget> targets;
    var owner = method.getOwner();
    var declarations = declarationsByOwner.computeIfAbsent(owner, resolver::declarations);
    var named = declarations.getOrDefault(method.getName(), List.of());
    if (named.isEmpty()) {
      targets = List.of();
    } else if (named.size() == 1) {
      targets = List.of(target(owner, named.getFirst()));
    } else {
      var values = new ArrayList<MethodTarget>(named.size());
      for (var declaration : named) {
        values.add(target(owner, declaration));
      }
      targets = List.copyOf(values);
    }
    return targets;
  }

  /**
   * Профиль кешируется на директиву, пересечение с веткой — на объявление, без повторного вывода типов.
   */
  private MethodTarget target(DocumentContext owner, Declaration declaration) {
    var targets = targetsByOwner.computeIfAbsent(owner, ignored -> new HashMap<>());
    return targets.computeIfAbsent(declaration, candidate -> createTarget(owner, candidate));
  }

  private MethodTarget createTarget(DocumentContext owner, Declaration declaration) {
    var profile = profiles.computeIfAbsent(new ProfileKey(owner.getUri(), declaration.directive()),
      ignored -> resolver.methodContext(owner, declaration.directive()));
    var contexts = declaration.branch() == null ? Optional.<Set<ExecutionContext>>empty() : profile.map((MethodContext value) -> {
      var declared = ExecutionContext.copyOf(value.contexts());
      declared.retainAll(declaration.branch());
      return Set.copyOf(declared);
    });
    return new MethodTarget(owner, declaration, profile, contexts);
  }

}
