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

/**
 * Объединяет оценки по контекстам вызова и возможным типам получателя.
 */
final class CompletionContextEvaluation {
  private final Optional<MethodContext> scope;
  private final CompletionTypeContext typeContext;
  private final Optional<CompletionMethodEvaluation> methods;
  private boolean receiverChecked;
  private @Nullable TypeRef sharedReceiver;
  private @Nullable Set<TypeRef> sharedReceiverUnion;
  private boolean propertyReceiverChecked;
  private CompletionTypeContext.@Nullable PropertyScope propertyScope;

  /**
   * Подготовить контекст один раз для всех предлагаемых имён.
   *
   * @param document документ запроса.
   * @param position позиция курсора, в том числе после незавершённой точки.
   * @param resolver общая модель компиляции и серверных переходов.
   * @param types    служба вывода типов и поиска членов.
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

  int rankDotPropertyMember(String name, MemberDescriptor member) {
    var prepared = propertyScope;
    if (prepared != null) {
      return prepared.rank(name);
    }
    var rank = rankDotMember(name, member, null);
    if (!propertyReceiverChecked) {
      checkSharedPropertyReceiver();
    }
    return rank;
  }

  private void checkSharedPropertyReceiver() {
    propertyReceiverChecked = true;
    if (scope.isEmpty()) {
      return;
    }
    @Nullable TypeRef owner = null;
    for (var context : scope.orElseThrow().contexts()) {
      var receiver = typeContext.receiverTypes(context);
      if (receiver.refs().size() != 1) {
        return;
      }
      var candidate = receiver.refs().iterator().next();
      if (candidate.equals(TypeRef.ANY) || candidate.equals(TypeRef.UNKNOWN)
        || !receiver.getLocalFields(candidate).isEmpty()) {
        return;
      }
      if (owner != null && !owner.equals(candidate)) {
        return;
      }
      owner = candidate;
    }
    if (owner != null) {
      propertyScope = typeContext.propertyScope(owner, scope.orElseThrow().contexts());
    }
  }

  int rankDotSourceMember(String name, MemberDescriptor member, CompletionMethodHint hint) {
    if (!(member.sourceSymbol() instanceof MethodSymbol)) {
      return acrossContexts(context -> rankDotInContext(name, member, hint, context));
    }
    var shared = sharedReceiver;
    if (shared != null) {
      var candidates = typeContext.alternatives(shared, member.kind(), name);
      if (candidates.size() == 1) {
        var rank = methods.orElseThrow().sharedReceiverRank(candidates.getFirst(), shared, hint);
        if (rank != null) {
          return rank;
        }
      }
    }
    if (sharedReceiverUnion != null && member.kind() == MemberKind.METHOD) {
      var rank = rankSharedReceiverUnion(name, member, hint);
      if (rank != null) {
        return rank;
      }
    }
    var rank = acrossContexts(context -> rankDotInContext(name, member, hint, context));
    if (!receiverChecked) {
      checkSharedReceiver();
      if (sharedReceiver == null) {
        checkSharedReceiverUnion();
      }
    }
    return rank;
  }

  /**
   * Тот же набор известных владельцев во всех режимах; локальные поля не меняют поиск методов.
   */
  private void checkSharedReceiverUnion() {
    if (scope.isEmpty()) {
      return;
    }
    Set<TypeRef> owners = null;
    for (var context : scope.orElseThrow().contexts()) {
      var refs = typeContext.receiverTypes(context).refs();
      if (refs.size() < 2 || refs.contains(TypeRef.ANY) || refs.contains(TypeRef.UNKNOWN)) {
        return;
      }
      if (owners != null && !owners.equals(refs)) {
        return;
      }
      owners = refs;
    }
    if (owners != null) {
      sharedReceiverUnion = Set.copyOf(owners);
    }
  }

  /**
   * Обе свёртки AVAILABLE/PARTIAL/UNAVAILABLE сохраняют признаки доступной и недоступной альтернативы.
   */
  private @Nullable Integer rankSharedReceiverUnion(String name, MemberDescriptor member,
                                                    CompletionMethodHint hint) {
    var contribution = new CompletionMethodHint();
    var available = false;
    var unavailable = false;
    for (var owner : sharedReceiverUnion) {
      var candidates = typeContext.alternatives(owner, member.kind(), name);
      if (candidates.size() != 1) {
        return null;
      }
      var rank = methods.orElseThrow().sharedReceiverRank(candidates.getFirst(), owner, contribution);
      if (rank == null) {
        return null;
      }
      available |= rank != UNAVAILABLE;
      unavailable |= rank != AVAILABLE;
    }
    hint.merge(contribution);
    return combine(available, unavailable);
  }

  private void checkSharedReceiver() {
    receiverChecked = true;
    if (scope.isEmpty()) {
      return;
    }
    @Nullable TypeRef owner = null;
    for (var context : scope.orElseThrow().contexts()) {
      var receiver = typeContext.receiverTypes(context);
      if (receiver.refs().size() != 1) {
        return;
      }
      var candidate = receiver.refs().iterator().next();
      if (candidate.equals(TypeRef.ANY) || candidate.equals(TypeRef.UNKNOWN)
        || !receiver.getLocalFields(candidate).isEmpty()) {
        return;
      }
      if (owner != null && !owner.equals(candidate)) {
        return;
      }
      owner = candidate;
    }
    sharedReceiver = owner;
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
    var available = false;
    var unavailable = false;
    for (var owner : receiver.refs()) {
      var rank = rankReceiver(name, member, hint, context, receiver, owner);
      available |= rank != UNAVAILABLE;
      unavailable |= rank != AVAILABLE;
    }
    return combine(available, unavailable);
  }

  private int rankReceiver(String name, MemberDescriptor member, @Nullable CompletionMethodHint hint,
                           ExecutionContext context, TypeSet receiver, TypeRef owner) {
    if (owner.equals(TypeRef.ANY) || owner.equals(TypeRef.UNKNOWN)) {
      if (hint != null) {
        hint.unknownReceiver();
      }
      return AVAILABLE;
    }
    if (member.kind() == MemberKind.PROPERTY && hasLocalProperty(receiver, owner, name)) {
      return typeContext.typeRank(owner, context);
    }
    var alternatives = typeContext.alternatives(owner, member.kind(), name);
    if (alternatives.isEmpty()) {
      if (hint != null) {
        hint.unavailableCall();
      }
      return UNAVAILABLE;
    }
    var rank = UNAVAILABLE;
    for (var candidate : alternatives) {
      var candidateRank = hint != null ? methods.orElseThrow().methodRank(candidate, context, hint)
        : typeContext.memberRank(candidate, context);
      rank = Math.min(rank, candidateRank);
    }
    return Math.max(typeContext.typeRank(owner, context), rank);
  }

  private static boolean hasLocalProperty(TypeSet receiver, TypeRef owner, String name) {
    for (var field : receiver.getLocalFields(owner).keySet()) {
      if (name.equalsIgnoreCase(field)) {
        return true;
      }
    }
    return false;
  }

  private int acrossContexts(ToIntFunction<ExecutionContext> rank) {
    if (scope.isEmpty()) {
      return AVAILABLE;
    }
    var available = false;
    var unavailable = false;
    for (var context : scope.orElseThrow().contexts()) {
      var value = rank.applyAsInt(context);
      available |= value != UNAVAILABLE;
      unavailable |= value != AVAILABLE;
    }
    return combine(available, unavailable);
  }
}
