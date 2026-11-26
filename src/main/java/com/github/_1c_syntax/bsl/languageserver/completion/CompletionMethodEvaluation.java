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
import com.github._1c_syntax.bsl.languageserver.completion.CompletionMethodTargets.MethodTarget;
import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CallAvailability;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver.MethodContext;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

import static com.github._1c_syntax.bsl.languageserver.completion.CompletionPriority.*;

/**
 * Исходные объявления методов, их профили и переходы для одного запроса completion.
 */
final class CompletionMethodEvaluation {
  private final DocumentContext document;
  private final CompilationContextResolver resolver;
  private final MethodContext scope;
  private final CompletionTypeContext typeContext;
  private final CompletionMethodTargets methodTargets;
  private final Map<MethodTarget, Map<ExecutionContext, CallEvaluation>> calls = new IdentityHashMap<>();
  private final Map<TypeRef, Map<MethodTarget, SharedRank>> sharedRanks = new HashMap<>();

  private record SharedRank(int rank, CompletionMethodHint hint) {
  }

  private record CallEvaluation(CallAvailability availability, boolean serverTransition) {
  }

  CompletionMethodEvaluation(DocumentContext document, CompilationContextResolver resolver,
                             MethodContext scope, CompletionTypeContext typeContext) {
    this.document = document;
    this.resolver = resolver;
    this.scope = scope;
    this.typeContext = typeContext;
    methodTargets = new CompletionMethodTargets(resolver);
  }

  ToIntFunction<ExecutionContext> localMethod(MethodSymbol method, CompletionMethodHint hint) {
    var targets = methodTargets.localMethod(method);
    return context -> methodRank(targets, context, hint);
  }

  /**
   * Платформенная доступность сама по себе не доказывает наличие или отсутствие серверного перехода.
   */
  int methodRank(MemberDescriptor member, ExecutionContext context, CompletionMethodHint hint) {
    if (member.sourceSymbol() instanceof MethodSymbol method) {
      return methodRank(methodTargets.forMethod(method), context, hint);
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

  @Nullable Integer sharedReceiverRank(MemberDescriptor member, TypeRef owner, CompletionMethodHint hint) {
    if (!(member.sourceSymbol() instanceof MethodSymbol method)) {
      return null;
    }
    var targets = methodTargets.forMethod(method);
    if (targets.size() != 1) {
      return null;
    }
    var byTarget = sharedRanks.computeIfAbsent(owner, ignored -> new IdentityHashMap<>());
    var target = targets.getFirst();
    var ready = byTarget.get(target);
    if (ready == null) {
      var contribution = new CompletionMethodHint();
      var available = false;
      var unavailable = false;
      for (var context : scope.contexts()) {
        var methodRank = methodRank(targets, context, contribution);
        var rank = Math.max(typeContext.typeRank(owner, context), methodRank);
        available |= rank != UNAVAILABLE;
        unavailable |= rank != AVAILABLE;
      }
      var rank = combine(available, unavailable);
      ready = new SharedRank(rank, contribution);
      byTarget.put(target, ready);
    }
    hint.merge(ready.hint());
    return ready.rank();
  }

  /**
   * Найденные исходные цели проверяются теми же правилами перехода, что и диагностика.
   */
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
      rank = Math.min(rank, rankTarget(target, context, hint));
    }
    if (rank == UNAVAILABLE) {
      hint.unavailableCall();
    }
    return rank;
  }

  private int rankTarget(MethodTarget target, ExecutionContext context, CompletionMethodHint hint) {
    if (target.contexts().isPresent()) {
      hint.include(target.contexts().orElseThrow());
    } else {
      hint.unknownContexts();
    }
    var contextCalls = calls.computeIfAbsent(target, ignored -> new EnumMap<>(ExecutionContext.class));
    var call = contextCalls.computeIfAbsent(context, selected -> evaluateCall(target, selected));
    return switch (call.availability()) {
      case UNKNOWN -> {
        hint.unknownCall();
        yield AVAILABLE;
      }
      case AVAILABLE -> {
        hint.knownCall(call.serverTransition());
        yield AVAILABLE;
      }
      default -> UNAVAILABLE;
    };
  }

  private CallEvaluation evaluateCall(MethodTarget target, ExecutionContext context) {
    var availability = resolver.availability(document, scope, target.owner(), target.declaration(),
      target.profile(), context);
    var serverTransition = availability == CallAvailability.AVAILABLE
      && CompilationContextResolver.isServerTransition(context,
      resolver.targetContext(document, target.owner(), target.profile().orElseThrow(), context));
    return new CallEvaluation(availability, serverTransition);
  }
}
