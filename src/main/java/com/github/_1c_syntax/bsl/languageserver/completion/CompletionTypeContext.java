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
import java.util.Collections;
import org.jspecify.annotations.Nullable;

import static com.github._1c_syntax.bsl.languageserver.completion.CompletionPriority.*;

/** Типы получателей, члены и платформенные ограничения одного запроса completion. */
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

  private record PropertyRanks(EnumSet<ExecutionContext> available, EnumSet<ExecutionContext> possible) {
    int rank(ExecutionContext context) {
      return available.contains(context) ? AVAILABLE : (possible.contains(context) ? PARTIAL : UNAVAILABLE);
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
    var names = members.get(owner);
    if (names == null) {
      names = indexMembers(owner);
      members.put(owner, names);
    }
    var keys = memberKeys.get(kind);
    if (keys == null) {
      keys = new IdentityHashMap<>();
      memberKeys.put(kind, keys);
    }
    var lookupKey = keys.get(name);
    if (lookupKey == null) {
      lookupKey = key(kind, name);
      keys.put(name, lookupKey);
    }
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

  private PropertyRanks propertyRanksFor(MemberDescriptor member) {
    if (propertyRanks == null) propertyRanks = new IdentityHashMap<>();
    var known = propertyRanks.get(member);
    if (known == null) {
      known = computePropertyRanks(member);
      propertyRanks.put(member, known);
    }
    return known;
  }

  PropertyScope propertyScope(TypeRef owner, Set<ExecutionContext> contexts) {
    return new PropertyScope(owner, contexts);
  }

  /** Оценки свойств одного известного владельца в неизменном наборе режимов запроса. */
  final class PropertyScope {
    private final TypeRef owner;
    private final EnumSet<ExecutionContext> selected;
    private final EnumSet<ExecutionContext> active;
    private final boolean ownerEverywhere;

    private PropertyScope(TypeRef owner, Set<ExecutionContext> contexts) {
      this.owner = owner;
      selected = contextMask(contexts);
      active = selected.clone();
      active.retainAll(typeMask(owner));
      ownerEverywhere = active.size() == selected.size();
    }

    int rank(String name) {
      if (selected.isEmpty()) return AVAILABLE;
      if (active.isEmpty()) return UNAVAILABLE;
      var candidates = alternatives(owner, MemberKind.PROPERTY, name);
      if (candidates.isEmpty()) return UNAVAILABLE;
      if (candidates.size() == 1) {
        var ranks = propertyRanksFor(candidates.getFirst());
        return reduce(ranks.available(), ranks.possible());
      }
      var available = EnumSet.noneOf(ExecutionContext.class);
      var possible = EnumSet.noneOf(ExecutionContext.class);
      for (var candidate : candidates) {
        var ranks = propertyRanksFor(candidate);
        available.addAll(ranks.available());
        possible.addAll(ranks.possible());
      }
      return reduce(available, possible);
    }

    private int reduce(EnumSet<ExecutionContext> available, EnumSet<ExecutionContext> possible) {
      if (ownerEverywhere && available.containsAll(selected)) return AVAILABLE;
      return Collections.disjoint(active, possible) ? UNAVAILABLE : PARTIAL;
    }
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

  private EnumSet<ExecutionContext> typeMask(TypeRef type) {
    var available = typeContexts.get(type);
    if (available == null) {
      available = resolver.typeContexts(document, type);
      typeContexts.put(type, available);
    }
    return available.isEmpty() ? EnumSet.allOf(ExecutionContext.class) : contextMask(available.orElseThrow());
  }

  /** Снимок неизменяемых метаданных; содержимое общих масок никогда не изменяется. */
  private EnumSet<ExecutionContext> contextMask(Set<ExecutionContext> contexts) {
    if (contextMasks == null) contextMasks = new IdentityHashMap<>();
    var known = contextMasks.get(contexts);
    if (known == null) {
      known = EnumSet.noneOf(ExecutionContext.class);
      known.addAll(contexts);
      contextMasks.put(contexts, known);
    }
    return known;
  }

  /** Видимость общего модуля учитывает серверный вызов; прочие типы используют сведения СП. */
  int typeRank(TypeRef type, ExecutionContext context) {
    var available = typeContexts.get(type);
    if (available == null) {
      available = resolver.typeContexts(document, type);
      typeContexts.put(type, available);
    }
    return available.isEmpty() || available.orElseThrow().contains(context) ? AVAILABLE : UNAVAILABLE;
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
