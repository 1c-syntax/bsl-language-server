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
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CallAvailability;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver.Declaration;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver.MethodContext;
import com.github._1c_syntax.bsl.languageserver.types.model.Availability;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.PlatformMetadata;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import org.eclipse.lsp4j.Position;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;
import java.util.EnumMap;

/** Оценка доступности кандидатов и кеши разрешения типов одного запроса completion. */
final class CompletionContextEvaluation {
  static final int AVAILABLE = 0;
  static final int PARTIAL = 1;
  static final int UNAVAILABLE = 2;
  private final DocumentContext document;
  private final Position position;
  private final CompilationContextResolver resolver;
  private final TypeService types;
  private final Optional<MethodContext> scope;
  @SuppressWarnings("java:S6411") // IdentityHashMap сопоставляет исходные символы по ссылке, не через Comparable.
  private final Map<MethodSymbol, List<MethodTarget>> methodTargets = new IdentityHashMap<>();
  private final Map<ProfileKey, Optional<MethodContext>> profiles = new HashMap<>();
  private final Map<ExecutionContext, TypeSet> receivers = new EnumMap<>(ExecutionContext.class);
  private final Map<TypeRef, Map<String, List<MemberDescriptor>>> members = new HashMap<>();
  @SuppressWarnings("java:S6411") // Неизменяемые наборы конечных флагов СП используются по равенству, без порядка.
  private final Map<Set<Availability>, Set<ExecutionContext>> platformContexts = new HashMap<>();
  private final Map<TypeRef, Optional<Set<ExecutionContext>>> typeContexts = new HashMap<>();

  /** Порядок ключа согласован с URI и необязательной директивой. */
  private record ProfileKey(URI uri, Optional<CompilerDirectiveKind> directive) implements Comparable<ProfileKey> {
    @Override
    public int compareTo(ProfileKey other) {
      var byUri = uri.compareTo(other.uri);
      return byUri != 0 ? byUri : Integer.compare(directiveOrdinal(), other.directiveOrdinal());
    }

    private int directiveOrdinal() {
      return directive.isPresent() ? directive.orElseThrow().ordinal() : -1;
    }
  }

  /** Объявление и вычисленные один раз профиль и контексты его компиляции. */
  private record MethodTarget(DocumentContext owner, Declaration declaration, Optional<MethodContext> profile,
                              Optional<Set<ExecutionContext>> contexts) { }


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
    this.document = document;
    this.position = position;
    this.resolver = resolver;
    this.types = types;
    scope = resolver.contextAt(document, position).map((MethodContext profile) -> {
      var contexts = ExecutionContext.copyOf(profile.contexts());
      contexts.retainAll(resolver.automaticContexts(document));
      return new MethodContext(profile.directive(), Set.copyOf(contexts));
    }).filter(profile -> !profile.contexts().isEmpty());
  }

  boolean hasScope() {
    return scope.isPresent();
  }

  int rankMember(MemberDescriptor member) {
    return acrossContexts(context -> memberRank(member, context));
  }

  int rankMethod(MemberDescriptor member, CompletionMethodHint hint) {
    return acrossContexts(context -> methodRank(member, context, hint));
  }

  int rankLocalMethod(MethodSymbol method, CompletionMethodHint hint) {
    var owner = method.getOwner();
    var targets = List.of(target(owner, new Declaration(method.getCompilerDirectiveKind(),
      owner.getSymbolTreeNoLock().getMethodPreprocessorContexts(method), method.isExport())));
    return acrossContexts(context -> methodRank(targets, context, hint));
  }

  int rankType(String name) {
    return types.resolve(name, document.getFileType())
      .map(type -> acrossContexts(context -> typeRank(type, context))).orElse(AVAILABLE);
  }

  @SuppressWarnings("java:S3553") // У свойства нет методной подсказки; Optional сохраняет это различие явно.
  int rankDotMember(String name, MemberDescriptor member, Optional<CompletionMethodHint> hint) {
    return acrossContexts(context -> rankDotInContext(name, member, hint, context));
  }

  @SuppressWarnings("java:S3553") // Внутренний путь общ для методов и свойств; у свойства подсказка отсутствует.
  private int rankDotInContext(String name, MemberDescriptor member, Optional<CompletionMethodHint> hint,
                                ExecutionContext context) {
    var receiver = receivers.computeIfAbsent(context, mode -> types.receiverTypesAt(document, position, mode));
    if (receiver.isEmpty()) {
      hint.ifPresent(CompletionMethodHint::unknownReceiver);
      return AVAILABLE;
    }
    return combine(receiver.refs().stream().mapToInt(owner -> rankReceiver(name, member, hint, context, receiver, owner))
      .toArray());
  }

  @SuppressWarnings("java:S3553") // Пустая подсказка означает свойство и не требует создавать фиктивное состояние метода.
  private int rankReceiver(String name, MemberDescriptor member, Optional<CompletionMethodHint> hint,
                           ExecutionContext context, TypeSet receiver, TypeRef owner) {
    if (owner.equals(TypeRef.ANY) || owner.equals(TypeRef.UNKNOWN)) {
      hint.ifPresent(CompletionMethodHint::unknownReceiver);
      return AVAILABLE;
    }
    if (member.kind() == MemberKind.PROPERTY && receiver.getLocalFields(owner).keySet().stream()
      .anyMatch(name::equalsIgnoreCase)) {
      return typeRank(owner, context);
    }
    var alternatives = members.computeIfAbsent(owner, this::indexMembers)
      .getOrDefault(key(member.kind(), name), List.of());
    if (alternatives.isEmpty()) {
      hint.ifPresent(CompletionMethodHint::unavailableCall);
      return UNAVAILABLE;
    }
    var rank = alternatives.stream().mapToInt(candidate -> hint.isPresent()
      ? methodRank(candidate, context, hint.orElseThrow()) : memberRank(candidate, context)).min().orElseThrow();
    return Math.max(typeRank(owner, context), rank);
  }

  private int acrossContexts(ToIntFunction<ExecutionContext> rank) {
    return scope.map((MethodContext profile) -> combine(profile.contexts().stream().mapToInt(rank).toArray())).orElse(AVAILABLE);
  }

  /** Пустой набор означает отсутствие сведений; смешанная доступность — отдельную промежуточную группу. */
  private static int combine(int[] ranks) {
    var available = false;
    var unavailable = false;
    for (var rank : ranks) {
      available |= rank != UNAVAILABLE;
      unavailable |= rank != AVAILABLE;
    }
    if (!unavailable) {
      return AVAILABLE;
    }
    return available ? PARTIAL : UNAVAILABLE;
  }

  private int memberRank(MemberDescriptor member, ExecutionContext context) {
    var rank = platformRank(member.metadata(), context);
    if (member.kind() == MemberKind.PROPERTY) {
      rank = Math.max(rank, combine(member.returnTypes().refs().stream()
        .mapToInt(type -> typeRank(type, context)).toArray()));
    }
    return rank;
  }

  /** Платформенная доступность сама по себе не доказывает наличие или отсутствие серверного перехода. */
  private int methodRank(MemberDescriptor member, ExecutionContext context, CompletionMethodHint hint) {
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
      hint.include(platformContexts.computeIfAbsent(available, resolver::platformContexts));
    }
    var rank = platformRank(member.metadata(), context);
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
      var availability = resolver.availability(document, scope.orElseThrow(), target.owner(), target.declaration(),
        target.profile(), context);
      if (availability == CallAvailability.UNKNOWN) {
        hint.unknownCall();
        rank = AVAILABLE;
      } else if (availability == CallAvailability.AVAILABLE) {
        var destination = resolver.targetContext(document, target.owner(), target.profile().orElseThrow(), context);
        hint.knownCall(CompilationContextResolver.isServerTransition(context, destination));
        rank = AVAILABLE;
      } else {
        // Недоступная альтернатива не перекрывает уже найденную доступную.
      }
    }
    if (rank == UNAVAILABLE) {
      hint.unavailableCall();
    }
    return rank;
  }

  /** Видимость общего модуля учитывает серверный вызов; прочие типы используют сведения СП. */
  private int typeRank(TypeRef type, ExecutionContext context) {
    return typeContexts.computeIfAbsent(type, candidate -> resolver.typeContexts(document, candidate))
      .map(contexts -> contexts.contains(context) ? AVAILABLE : UNAVAILABLE).orElse(AVAILABLE);
  }

  private int platformRank(PlatformMetadata metadata, ExecutionContext context) {
    return metadata.availabilities().isEmpty()
      || platformContexts.computeIfAbsent(metadata.availabilities(), resolver::platformContexts).contains(context)
      ? AVAILABLE : UNAVAILABLE;
  }

  /** Имена обоих языков индексируются один раз на тип, локальные поля остаются в контекстном TypeSet. */
  private Map<String, List<MemberDescriptor>> indexMembers(TypeRef type) {
    Map<String, List<MemberDescriptor>> result = new HashMap<>();
    for (var member : types.getMembers(type, document.getFileType())) {
      for (var name : Stream.of(member.bilingualName().ru(), member.bilingualName().en())
          .filter(value -> !value.isEmpty()).distinct().toList()) {
        result.computeIfAbsent(key(member.kind(), name), ignored -> new ArrayList<>()).add(member);
      }
    }
    return result;
  }

  private static String key(MemberKind kind, String name) {
    return kind + ":" + name.toLowerCase(Locale.ROOT);
  }
}
