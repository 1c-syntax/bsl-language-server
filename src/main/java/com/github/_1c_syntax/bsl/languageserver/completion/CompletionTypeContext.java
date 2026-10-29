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
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver;
import com.github._1c_syntax.bsl.languageserver.types.model.Availability;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.PlatformMetadata;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import org.eclipse.lsp4j.Position;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import java.util.EnumMap;

import static com.github._1c_syntax.bsl.languageserver.completion.CompletionPriority.*;

/** Типы получателей, члены и платформенные ограничения одного запроса completion. */
final class CompletionTypeContext {
  private final DocumentContext document;
  private final Position position;
  private final CompilationContextResolver resolver;
  private final TypeService types;
  private final Map<ExecutionContext, TypeSet> receivers = new EnumMap<>(ExecutionContext.class);
  private final Map<TypeRef, Map<String, List<MemberDescriptor>>> members = new HashMap<>();
  private final Map<Set<Availability>, Set<ExecutionContext>> platformContexts = new HashMap<>();
  private final Map<TypeRef, Optional<Set<ExecutionContext>>> typeContexts = new HashMap<>();

  CompletionTypeContext(DocumentContext document, Position position, CompilationContextResolver resolver,
                        TypeService types) {
    this.document = document;
    this.position = position;
    this.resolver = resolver;
    this.types = types;
  }

  TypeSet receiverTypes(ExecutionContext context) {
    return receivers.computeIfAbsent(context, mode -> types.receiverTypesAt(document, position, mode));
  }

  Optional<TypeRef> resolve(String name) {
    return types.resolve(name, document.getFileType());
  }

  List<MemberDescriptor> alternatives(TypeRef owner, MemberKind kind, String name) {
    return members.computeIfAbsent(owner, this::indexMembers).getOrDefault(key(kind, name), List.of());
  }

  Set<ExecutionContext> platformContexts(Set<Availability> availability) {
    return platformContexts.computeIfAbsent(availability, resolver::platformContexts);
  }

  int memberRank(MemberDescriptor member, ExecutionContext context) {
    var rank = platformRank(member.metadata(), context);
    if (member.kind() == MemberKind.PROPERTY) {
      rank = Math.max(rank, combine(member.returnTypes().refs().stream()
        .mapToInt(type -> typeRank(type, context)).toArray()));
    }
    return rank;
  }

  /** Видимость общего модуля учитывает серверный вызов; прочие типы используют сведения СП. */
  int typeRank(TypeRef type, ExecutionContext context) {
    return typeContexts.computeIfAbsent(type, candidate -> resolver.typeContexts(document, candidate))
      .map(contexts -> contexts.contains(context) ? AVAILABLE : UNAVAILABLE).orElse(AVAILABLE);
  }

  int platformRank(PlatformMetadata metadata, ExecutionContext context) {
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
