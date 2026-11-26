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
package com.github._1c_syntax.bsl.languageserver.types;

import com.github._1c_syntax.bsl.parser.description.VariableDescription;
import org.antlr.v4.runtime.Token;
import org.jspecify.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Разобранные комментарии к присваиваниям одного запроса вывода типов. */
public final class AssignmentCommentCache {
  private static final int MAX_COMMENTS = 64;
  private static final WeakReference<List<Token>> NO_SNAPSHOT = new WeakReference<>(null);

  private WeakReference<List<Token>> snapshot = NO_SNAPSHOT;
  private Map<Integer, Entry> entries = Map.of();

  @Nullable VariableDescription read(List<Token> tokens, Token stop, Token comment) {
    if (!snapshot.refersTo(tokens)) {
      entries = Map.of();
      snapshot = NO_SNAPSHOT;
    }

    var index = stop.getTokenIndex();
    var previous = entries.get(index);
    if (previous != null && previous.stop() == stop && previous.comment() == comment) {
      return previous.syntax();
    }

    var syntax = CommentTypeResolver.parseAssignmentComment(comment);
    if (syntax == null || (entries.size() >= MAX_COMMENTS && previous == null)) {
      return syntax;
    }
    if (entries.isEmpty()) {
      entries = new HashMap<>();
      snapshot = new WeakReference<>(tokens);
    }
    entries.put(index, new Entry(stop, comment, syntax));
    return syntax;
  }

  private record Entry(Token stop, Token comment, VariableDescription syntax) {
  }
}
