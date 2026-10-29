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
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import java.net.URI;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToIntFunction;

import static com.github._1c_syntax.bsl.languageserver.completion.CompletionPriority.*;

/** Исходные объявления методов, их профили и переходы для одного запроса completion. */
final class CompletionMethodEvaluation {
  private final DocumentContext document;
  private final CompilationContextResolver resolver;
  private final MethodContext scope;
  private final CompletionTypeContext typeContext;
  private final Map<MethodSymbol, List<MethodTarget>> methodTargets = new IdentityHashMap<>();
  private final Map<ProfileKey, Optional<MethodContext>> profiles = new HashMap<>();

  /** Порядок ключа согласован с URI и необязательной директивой. */
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

  /** Объявление и вычисленные один раз профиль и контексты его компиляции. */
  private record MethodTarget(DocumentContext owner, Declaration declaration, Optional<MethodContext> profile,
                              Optional<Set<ExecutionContext>> contexts) { }

  CompletionMethodEvaluation(DocumentContext document, CompilationContextResolver resolver,
                             MethodContext scope, CompletionTypeContext typeContext) {
    this.document = document;
    this.resolver = resolver;
    this.scope = scope;
    this.typeContext = typeContext;
  }

  ToIntFunction<ExecutionContext> localMethod(MethodSymbol method, CompletionMethodHint hint) {
    var owner = method.getOwner();
    var targets = List.of(target(owner,
      new Declaration(method.getCompilerDirectiveKind(),
        owner.getSymbolTreeNoLock().getMethodPreprocessorContexts(method), method.isExport())));
    return context -> methodRank(targets, context, hint);
  }

  /** Платформенная доступность сама по себе не доказывает наличие или отсутствие серверного перехода. */
  int methodRank(MemberDescriptor member, ExecutionContext context, CompletionMethodHint hint) {
    if (member.getSourceSymbol().orElse(null) instanceof MethodSymbol method) {
      var targets = methodTargets.computeIfAbsent(method, symbol -> resolver.declarations(symbol.getOwner())
        .getOrDefault(symbol.getName(), List.of()).stream()
        .map(declaration -> target(symbol.getOwner(), declaration)).toList());
      return methodRank(targets, context, hint);
    }
    var available = member.metadata().availabilities();
    if (available.isEmpty()) {
      hint.unknownContexts();
    } else {
      hint.include(typeContext.platformContexts(available));
    }
    var rank = typeContext.platformRank(member.metadata(), context);
    if (rank == UNAVAILABLE) {
      hint.unavailableCall();
    } else {
      hint.unknownCall();
    }
    return rank;
  }

  /** Профиль кешируется на директиву, пересечение с веткой — на объявление, без повторного вывода типов. */
  private MethodTarget target(DocumentContext owner, Declaration declaration) {
    var profile = profiles.computeIfAbsent(new ProfileKey(owner.getUri(), declaration.directive()),
      ignored -> resolver.methodContext(owner, declaration.directive()));
    var contexts = declaration.branch() == null ? Optional.<Set<ExecutionContext>>empty() : profile.map((MethodContext value) -> {
      var declared = ExecutionContext.copyOf(value.contexts());
      declared.retainAll(declaration.branch());
      return Set.copyOf(declared);
    });
    return new MethodTarget(owner, declaration, profile, contexts);
  }

  /** Найденные исходные цели проверяются теми же правилами перехода, что и диагностика. */
  private int methodRank(List<MethodTarget> targets, ExecutionContext context, CompletionMethodHint hint) {
    if (targets.isEmpty()) {
      hint.unknownReceiver();
      return AVAILABLE;
    }
    var rank = UNAVAILABLE;
    for (var target : targets) {
      if (!target.owner().getUri().equals(document.getUri()) && !target.declaration().exported()) {
        continue;
      }
      target.contexts().ifPresentOrElse(hint::include, hint::unknownContexts);
      var availability = resolver.availability(document, scope, target.owner(), target.declaration(),
        target.profile(), context);
      switch (availability) {
        case UNKNOWN -> {
          hint.unknownCall();
          rank = AVAILABLE;
        }
        case AVAILABLE -> {
          var destination = resolver.targetContext(document, target.owner(), target.profile().orElseThrow(), context);
          hint.knownCall(CompilationContextResolver.isServerTransition(context, destination));
          rank = AVAILABLE;
        }
        default -> {
          // Недоступная альтернатива не перекрывает уже найденную доступную.
        }
      }
    }
    if (rank == UNAVAILABLE) {
      hint.unavailableCall();
    }
    return rank;
  }
}
