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
package com.github._1c_syntax.bsl.languageserver.context.compilation;

import com.github._1c_syntax.bsl.parser.BSLParser;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Вычисление условия препроцессора по типам его лексем: {@code НЕ} сильнее {@code И}, {@code И} сильнее
 * {@code ИЛИ}.
 */
final class PreprocessorExpressionEvaluator {
  /**
   * Контексты, в которых символ препроцессора истинен. Символа нет в таблице - условие не вычисляется.
   */
  private static final Map<Integer, Set<ExecutionContext>> SYMBOL_CONTEXTS = Map.ofEntries(
    Map.entry(BSLParser.PREPROC_CLIENT_SYMBOL, ExecutionContext.CLIENT_PREPROCESSOR),
    Map.entry(BSLParser.PREPROC_ATCLIENT_SYMBOL, ExecutionContext.CLIENTS),
    Map.entry(BSLParser.PREPROC_SERVER_SYMBOL, ExecutionContext.SERVER_PREPROCESSOR),
    Map.entry(BSLParser.PREPROC_ATSERVER_SYMBOL, ExecutionContext.SERVER_CONTEXTS),
    Map.entry(BSLParser.PREPROC_THINCLIENT_SYMBOL, EnumSet.of(ExecutionContext.THIN_CLIENT)),
    Map.entry(BSLParser.PREPROC_WEBCLIENT_SYMBOL, EnumSet.of(ExecutionContext.WEB_CLIENT)),
    Map.entry(BSLParser.PREPROC_MOBILECLIENT_SYMBOL, EnumSet.of(ExecutionContext.MOBILE_CLIENT)),
    Map.entry(BSLParser.PREPROC_THICKCLIENTMANAGEDAPPLICATION_SYMBOL, ExecutionContext.THICK_MANAGED_PREPROCESSOR),
    Map.entry(BSLParser.PREPROC_THICKCLIENTORDINARYAPPLICATION_SYMBOL, ExecutionContext.THICK_ORDINARY_PREPROCESSOR),
    Map.entry(BSLParser.PREPROC_EXTERNALCONNECTION_SYMBOL, ExecutionContext.EXTERNAL_PREPROCESSOR),
    Map.entry(BSLParser.PREPROC_MOBILEAPPCLIENT_SYMBOL, EnumSet.of(ExecutionContext.MOBILE_APPLICATION_CLIENT)),
    Map.entry(BSLParser.PREPROC_MOBILEAPPSERVER_SYMBOL, EnumSet.of(ExecutionContext.MOBILE_APPLICATION_SERVER)),
    Map.entry(BSLParser.PREPROC_MOBILE_STANDALONE_SERVER, EnumSet.of(ExecutionContext.MOBILE_STANDALONE_SERVER))
  );


  private final List<Integer> tokens;
  private int position;

  private PreprocessorExpressionEvaluator(List<Integer> tokens) {
    this.tokens = List.copyOf(tokens);
  }

  static @Nullable Set<ExecutionContext> evaluate(List<Integer> tokens) {
    return new PreprocessorExpressionEvaluator(tokens).evaluate();
  }

  private @Nullable Set<ExecutionContext> evaluate() {
    var result = or();
    return position == tokens.size() ? result : null;
  }

  private @Nullable Set<ExecutionContext> or() {
    var result = and();
    while (result != null && accept(BSLParser.PREPROC_OR_KEYWORD)) {
      var right = and();
      if (right == null) {
        return null;
      }
      result.addAll(right);
    }
    return result;
  }

  private @Nullable Set<ExecutionContext> and() {
    var result = not();
    while (result != null && accept(BSLParser.PREPROC_AND_KEYWORD)) {
      var right = not();
      if (right == null) {
        return null;
      }
      result.retainAll(right);
    }
    return result;
  }

  private @Nullable Set<ExecutionContext> not() {
    if (accept(BSLParser.PREPROC_NOT_KEYWORD)) {
      var operand = not();
      return operand == null ? null : ExecutionContext.complementOf(operand);
    }
    return operand();
  }

  private @Nullable Set<ExecutionContext> operand() {
    if (accept(BSLParser.PREPROC_LPAREN)) {
      var result = or();
      return accept(BSLParser.PREPROC_RPAREN) ? result : null;
    }
    if (position >= tokens.size()) {
      return null;
    }
    var contexts = SYMBOL_CONTEXTS.get(tokens.get(position));
    position++;
    return contexts == null ? null : ExecutionContext.copyOf(contexts);
  }

  private boolean accept(int tokenType) {
    if (position < tokens.size() && tokens.get(position) == tokenType) {
      position++;
      return true;
    }
    return false;
  }
}
