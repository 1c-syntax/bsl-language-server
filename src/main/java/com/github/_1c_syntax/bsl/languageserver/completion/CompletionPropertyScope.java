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

import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import static com.github._1c_syntax.bsl.languageserver.completion.CompletionPriority.*;

/**
 * Оценки свойств одного известного владельца в неизменном наборе режимов запроса.
 */
final class CompletionPropertyScope {
  private final CompletionTypeContext types;
  private final TypeRef owner;
  private final EnumSet<ExecutionContext> selected;
  private final EnumSet<ExecutionContext> active;
  private final boolean ownerEverywhere;

  CompletionPropertyScope(CompletionTypeContext types, TypeRef owner, Set<ExecutionContext> contexts) {
    this.types = types;
    this.owner = owner;
    selected = types.contextMask(contexts);
    active = selected.clone();
    active.retainAll(types.typeMask(owner));
    ownerEverywhere = active.size() == selected.size();
  }

  int rank(String name) {
    if (selected.isEmpty()) {
      return AVAILABLE;
    }
    if (active.isEmpty()) {
      return UNAVAILABLE;
    }
    var candidates = types.alternatives(owner, MemberKind.PROPERTY, name);
    if (candidates.isEmpty()) {
      return UNAVAILABLE;
    }
    if (candidates.size() == 1) {
      var ranks = types.propertyRanksFor(candidates.getFirst());
      return reduce(ranks.available(), ranks.possible());
    }
    var available = EnumSet.noneOf(ExecutionContext.class);
    var possible = EnumSet.noneOf(ExecutionContext.class);
    for (var candidate : candidates) {
      var ranks = types.propertyRanksFor(candidate);
      available.addAll(ranks.available());
      possible.addAll(ranks.possible());
    }
    return reduce(available, possible);
  }

  private int reduce(EnumSet<ExecutionContext> available, EnumSet<ExecutionContext> possible) {
    if (ownerEverywhere && available.containsAll(selected)) {
      return AVAILABLE;
    }
    return Collections.disjoint(active, possible) ? UNAVAILABLE : PARTIAL;
  }
}
