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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.EnumMap;
import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import static com.github._1c_syntax.bsl.languageserver.completion.CompletionPriority.*;

/**
 * Типы получателей, члены и платформенные ограничения одного запроса completion.
 */
final class CompletionTypeContext {
  private final DocumentContext document;
  private final Position position;
  private final CompilationContextResolver resolver;
  private final TypeService types;
  private @Nullable Function<@Nullable ExecutionContext, TypeSet> receiverQuery;
  private final Map<ExecutionContext, TypeSet> receivers = new EnumMap<>(ExecutionContext.class);
  private final Map<TypeRef, Map<String, List<MemberDescriptor>>> members = new HashMap<>();
  private final Map<Set<Availability>, Set<ExecutionContext>> platformContexts = new HashMap<>();
  private final Map<TypeRef, Optional<Set<ExecutionContext>>> typeContexts = new HashMap<>();
  private final Map<MemberKind, Map<String, String>> memberKeys = new EnumMap<>(MemberKind.class);
  private @Nullable Map<MemberDescriptor, PropertyRanks> propertyRanks;
  private @Nullable Map<Set<ExecutionContext>, EnumSet<ExecutionContext>> contextMasks;

  record PropertyRanks(EnumSet<ExecutionContext> available, EnumSet<ExecutionContext> possible) {
    int rank(ExecutionContext context) {
      if (available.contains(context)) {
        return AVAILABLE;
      }
      return possible.contains(context) ? PARTIAL : UNAVAILABLE;
    }
  }

  CompletionTypeContext(DocumentContext document, Position position, CompilationContextResolver resolver,
                        TypeService types) {
    this.document = document;
    this.position = position;
    this.resolver = resolver;
    this.types = types;
  }

  TypeSet receiverTypes(ExecutionContext context) {
    var receiver = receivers.get(context);
    if (receiver == null) {
      var query = receiverQuery;
      if (query == null) {
        query = types.receiverTypesQuery(document, position);
        receiverQuery = query;
      }
      receiver = query.apply(context);
      receivers.put(context, receiver);
    }
    return receiver;
  }

  Optional<TypeRef> resolve(String name) {
    return types.resolve(name, document.getFileType());
  }

  List<MemberDescriptor> alternatives(TypeRef owner, MemberKind kind, String name) {
    var names = members.computeIfAbsent(owner, this::indexMembers);
    var keys = memberKeys.computeIfAbsent(kind, ignored -> new IdentityHashMap<>());
    var lookupKey = keys.computeIfAbsent(name, candidate -> key(kind, candidate));
    return names.getOrDefault(lookupKey, List.of());
  }

  Set<ExecutionContext> platformContexts(Set<Availability> availability) {
    return platformContexts.computeIfAbsent(availability, resolver::platformContexts);
  }

  int memberRank(MemberDescriptor member, ExecutionContext context) {
    return member.kind() == MemberKind.PROPERTY ? propertyRank(member, context) : platformRank(member.metadata(), context);
  }

  private int propertyRank(MemberDescriptor member, ExecutionContext context) {
    return propertyRanksFor(member).rank(context);
  }

  PropertyRanks propertyRanksFor(MemberDescriptor member) {
    if (propertyRanks == null) {
      propertyRanks = new IdentityHashMap<>();
    }
    return propertyRanks.computeIfAbsent(member, this::computePropertyRanks);
  }

  CompletionPropertyScope propertyScope(TypeRef owner, Set<ExecutionContext> contexts) {
    return new CompletionPropertyScope(this, owner, contexts);
  }

  private PropertyRanks computePropertyRanks(MemberDescriptor member) {
    var refs = member.returnTypes().refs();
    var platform = member.metadata().availabilities();
    if (refs.isEmpty()) {
      var allowed = platform.isEmpty() ? EnumSet.allOf(ExecutionContext.class) : contextMask(platformContexts(platform));
      return new PropertyRanks(allowed, allowed);
    }
    if (refs.size() == 1 && platform.isEmpty()) {
      var allowed = typeMask(refs.iterator().next());
      return new PropertyRanks(allowed, allowed);
    }
    var available = EnumSet.allOf(ExecutionContext.class);
    var possible = EnumSet.noneOf(ExecutionContext.class);
    for (var type : refs) {
      var allowed = typeMask(type);
      available.retainAll(allowed);
      possible.addAll(allowed);
    }
    if (!platform.isEmpty()) {
      var allowed = contextMask(platformContexts(platform));
      available.retainAll(allowed);
      possible.retainAll(allowed);
    }
    return new PropertyRanks(available, possible);
  }

  EnumSet<ExecutionContext> typeMask(TypeRef type) {
    var available = typeContexts(type);
    return available.isEmpty() ? EnumSet.allOf(ExecutionContext.class) : contextMask(available.orElseThrow());
  }

  /**
   * Снимок неизменяемых метаданных; содержимое общих масок никогда не изменяется.
   */
  EnumSet<ExecutionContext> contextMask(Set<ExecutionContext> contexts) {
    if (contextMasks == null) {
      contextMasks = new IdentityHashMap<>();
    }
    return contextMasks.computeIfAbsent(contexts, (Set<ExecutionContext> selected) -> {
      var known = EnumSet.noneOf(ExecutionContext.class);
      known.addAll(selected);
      return known;
    });
  }

  /**
   * Видимость общего модуля учитывает серверный вызов; прочие типы используют сведения СП.
   */
  int typeRank(TypeRef type, ExecutionContext context) {
    var available = typeContexts(type);
    return available.isEmpty() || available.orElseThrow().contains(context) ? AVAILABLE : UNAVAILABLE;
  }

  private Optional<Set<ExecutionContext>> typeContexts(TypeRef type) {
    return typeContexts.computeIfAbsent(type, candidate -> resolver.typeContexts(document, candidate));
  }

  int platformRank(PlatformMetadata metadata, ExecutionContext context) {
    return metadata.availabilities().isEmpty()
      || platformContexts.computeIfAbsent(metadata.availabilities(), resolver::platformContexts).contains(context)
      ? AVAILABLE : UNAVAILABLE;
  }

  /**
   * Имена обоих языков индексируются один раз на тип, локальные поля остаются в контекстном TypeSet.
   */
  private Map<String, List<MemberDescriptor>> indexMembers(TypeRef type) {
    Map<String, List<MemberDescriptor>> result = new HashMap<>();
    for (var member : types.getMembers(type, document.getFileType())) {
      var ru = member.bilingualName().ru();
      var en = member.bilingualName().en();
      if (!ru.isEmpty()) {
        result.computeIfAbsent(key(member.kind(), ru), ignored -> new ArrayList<>()).add(member);
      }
      if (!en.isEmpty() && !en.equals(ru)) {
        result.computeIfAbsent(key(member.kind(), en), ignored -> new ArrayList<>()).add(member);
      }
    }
    return result;
  }

  private static String key(MemberKind kind, String name) {
    return CompletionMemberKeys.of(kind, name);
  }
}
