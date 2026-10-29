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
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver.MethodContext;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import org.eclipse.lsp4j.Position;
import org.jspecify.annotations.Nullable;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToIntFunction;

import static com.github._1c_syntax.bsl.languageserver.completion.CompletionPriority.*;

/** Объединяет оценки по контекстам вызова и возможным типам получателя. */
final class CompletionContextEvaluation {
  private final Optional<MethodContext> scope;
  private final CompletionTypeContext typeContext;
  private final Optional<CompletionMethodEvaluation> methods;

  /**
   * Подготовить контекст один раз для всех предлагаемых имён.
   *
   * @param document документ запроса.
   * @param position позиция курсора, в том числе после незавершённой точки.
   * @param resolver общая модель компиляции и серверных переходов.
   * @param types служба вывода типов и поиска членов.
   */
  CompletionContextEvaluation(DocumentContext document, Position position, CompilationContextResolver resolver,
                                 TypeService types) {
    typeContext = new CompletionTypeContext(document, position, resolver, types);
    scope = resolver.contextAt(document, position).map((MethodContext profile) -> {
      var contexts = ExecutionContext.copyOf(profile.contexts());
      contexts.retainAll(resolver.automaticContexts(document));
      return new MethodContext(profile.directive(), Set.copyOf(contexts));
    }).filter(profile -> !profile.contexts().isEmpty());
    methods = scope.map(profile -> new CompletionMethodEvaluation(document, resolver, profile, typeContext));
  }

  boolean hasScope() {
    return scope.isPresent();
  }

  int rankMember(MemberDescriptor member) {
    return acrossContexts(context -> typeContext.memberRank(member, context));
  }

  int rankMethod(MemberDescriptor member, CompletionMethodHint hint) {
    return acrossContexts(context -> methods.orElseThrow().methodRank(member, context, hint));
  }

  int rankLocalMethod(MethodSymbol method, CompletionMethodHint hint) {
    return acrossContexts(methods.orElseThrow().localMethod(method, hint));
  }

  int rankType(String name) {
    return typeContext.resolve(name)
      .map(type -> acrossContexts(context -> typeContext.typeRank(type, context))).orElse(AVAILABLE);
  }

  int rankDotMember(String name, MemberDescriptor member, @Nullable CompletionMethodHint hint) {
    return acrossContexts(context -> rankDotInContext(name, member, hint, context));
  }

  private int rankDotInContext(String name, MemberDescriptor member, @Nullable CompletionMethodHint hint,
                                ExecutionContext context) {
    var receiver = typeContext.receiverTypes(context);
    if (receiver.isEmpty()) {
      if (hint != null) {
        hint.unknownReceiver();
      }
      return AVAILABLE;
    }
    return combine(receiver.refs().stream().mapToInt(owner -> rankReceiver(name, member, hint, context, receiver, owner))
      .toArray());
  }

  private int rankReceiver(String name, MemberDescriptor member, @Nullable CompletionMethodHint hint,
                           ExecutionContext context, TypeSet receiver, TypeRef owner) {
    if (owner.equals(TypeRef.ANY) || owner.equals(TypeRef.UNKNOWN)) {
      if (hint != null) {
        hint.unknownReceiver();
      }
      return AVAILABLE;
    }
    if (member.kind() == MemberKind.PROPERTY && receiver.getLocalFields(owner).keySet().stream()
      .anyMatch(name::equalsIgnoreCase)) {
      return typeContext.typeRank(owner, context);
    }
    var alternatives = typeContext.alternatives(owner, member.kind(), name);
    if (alternatives.isEmpty()) {
      if (hint != null) {
        hint.unavailableCall();
      }
      return UNAVAILABLE;
    }
    var rank = alternatives.stream().mapToInt(candidate -> hint != null
      ? methods.orElseThrow().methodRank(candidate, context, hint) : typeContext.memberRank(candidate, context)).min().orElseThrow();
    return Math.max(typeContext.typeRank(owner, context), rank);
  }

  private int acrossContexts(ToIntFunction<ExecutionContext> rank) {
    return scope.map((MethodContext profile) -> combine(profile.contexts().stream().mapToInt(rank).toArray())).orElse(AVAILABLE);
  }
}
