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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Контексты компиляции модулей управляемого приложения - сервер, тонкий, веб- и мобильный клиент - и открытые
 * инструкции препроцессора {@code #Если}: в каких контекстах компилируется текущая строка модуля.
 * <br/>
 * Экземпляр получает события {@code #Если}, {@code #ИначеЕсли}, {@code #Иначе}, {@code #КонецЕсли} в порядке обхода
 * модуля. Символы толстого клиента, внешнего соединения и мобильного приложения в этих контекстах ложны; условие с
 * символом операционной системы или неизвестным символом не вычисляется.
 */
final class PreprocessorContexts {

  /**
   * Контекст компиляции модуля.
   */
  enum ExecutionContext {
    SERVER,
    THIN_CLIENT,
    WEB_CLIENT,
    MOBILE_CLIENT
  }

  static final Set<ExecutionContext> ALL_CONTEXTS = EnumSet.allOf(ExecutionContext.class);
  static final Set<ExecutionContext> SERVER_CONTEXTS = EnumSet.of(ExecutionContext.SERVER);
  static final Set<ExecutionContext> CLIENT_CONTEXTS = EnumSet.complementOf(EnumSet.of(ExecutionContext.SERVER));
  private static final Set<ExecutionContext> NO_CONTEXTS = EnumSet.noneOf(ExecutionContext.class);

  /**
   * Контексты, в которых символ препроцессора истинен. Символа нет в таблице - условие не вычисляется.
   */
  private static final Map<Integer, Set<ExecutionContext>> SYMBOL_CONTEXTS = Map.ofEntries(
    Map.entry(BSLParser.PREPROC_CLIENT_SYMBOL, CLIENT_CONTEXTS),
    Map.entry(BSLParser.PREPROC_ATCLIENT_SYMBOL, CLIENT_CONTEXTS),
    Map.entry(BSLParser.PREPROC_SERVER_SYMBOL, SERVER_CONTEXTS),
    Map.entry(BSLParser.PREPROC_ATSERVER_SYMBOL, SERVER_CONTEXTS),
    Map.entry(BSLParser.PREPROC_THINCLIENT_SYMBOL, EnumSet.of(ExecutionContext.THIN_CLIENT)),
    Map.entry(BSLParser.PREPROC_WEBCLIENT_SYMBOL, EnumSet.of(ExecutionContext.WEB_CLIENT)),
    Map.entry(BSLParser.PREPROC_MOBILECLIENT_SYMBOL, EnumSet.of(ExecutionContext.MOBILE_CLIENT)),
    Map.entry(BSLParser.PREPROC_THICKCLIENTMANAGEDAPPLICATION_SYMBOL, NO_CONTEXTS),
    Map.entry(BSLParser.PREPROC_THICKCLIENTORDINARYAPPLICATION_SYMBOL, NO_CONTEXTS),
    Map.entry(BSLParser.PREPROC_EXTERNALCONNECTION_SYMBOL, NO_CONTEXTS),
    Map.entry(BSLParser.PREPROC_MOBILEAPPCLIENT_SYMBOL, NO_CONTEXTS),
    Map.entry(BSLParser.PREPROC_MOBILEAPPSERVER_SYMBOL, NO_CONTEXTS),
    Map.entry(BSLParser.PREPROC_MOBILE_STANDALONE_SERVER, NO_CONTEXTS)
  );

  /**
   * Открытые инструкции {@code #Если}, внутренняя - сверху.
   */
  private final Deque<Branch> branches = new ArrayDeque<>();

  void clear() {
    branches.clear();
  }

  void enterIf(BSLParser.Preproc_ifContext ctx) {
    var parent = branches.peek();
    var branch = new Branch(parent == null ? ALL_CONTEXTS : parent.active);
    branch.enter(evaluate(ctx.preproc_expression()));
    branches.push(branch);
  }

  void enterElsif(BSLParser.Preproc_elsifContext ctx) {
    var branch = branches.peek();
    if (branch != null) {
      branch.enter(evaluate(ctx.preproc_expression()));
    }
  }

  void enterElse() {
    var branch = branches.peek();
    if (branch != null) {
      branch.enterElse();
    }
  }

  void enterEndif() {
    branches.poll();
  }

  /**
   * Контексты, в которых компилируется текущая строка; null - условие окружающей инструкции не вычисляется.
   */
  @Nullable Set<ExecutionContext> active() {
    var branch = branches.peek();
    return branch == null ? ALL_CONTEXTS : branch.active;
  }

  /**
   * Контексты, в которых истинно условие препроцессора; null - условие не вычисляется.
   */
  private static @Nullable Set<ExecutionContext> evaluate(BSLParser.@Nullable Preproc_expressionContext expression) {
    if (expression == null) {
      return null;
    }
    var tokens = new ArrayList<Integer>();
    collectTokenTypes(expression, tokens);
    return new ExpressionEvaluator(tokens).evaluate();
  }

  private static void collectTokenTypes(ParseTree tree, List<Integer> tokens) {
    if (tree instanceof TerminalNode terminal) {
      tokens.add(terminal.getSymbol().getType());
      return;
    }
    for (var i = 0; i < tree.getChildCount(); i++) {
      collectTokenTypes(tree.getChild(i), tokens);
    }
  }

  /**
   * Ветка открытой инструкции {@code #Если}: контексты, где активна текущая ветка, и контексты, где ни одна
   * из прошлых веток не сработала. null - условие не вычисляется.
   */
  private static final class Branch {
    private @Nullable Set<ExecutionContext> active;
    private @Nullable Set<ExecutionContext> remaining;

    private Branch(@Nullable Set<ExecutionContext> parentActive) {
      remaining = parentActive == null ? null : EnumSet.copyOf(parentActive);
    }

    private void enter(@Nullable Set<ExecutionContext> condition) {
      if (remaining == null || condition == null) {
        active = null;
        remaining = null;
        return;
      }
      var newActive = EnumSet.copyOf(remaining);
      newActive.retainAll(condition);
      remaining.removeAll(newActive);
      active = newActive;
    }

    private void enterElse() {
      active = remaining;
      remaining = remaining == null ? null : EnumSet.noneOf(ExecutionContext.class);
    }
  }

  /**
   * Вычисление условия препроцессора по типам его лексем: {@code НЕ} сильнее {@code И}, {@code И} сильнее
   * {@code ИЛИ}.
   */
  private static final class ExpressionEvaluator {
    private final List<Integer> tokens;
    private int position;

    private ExpressionEvaluator(List<Integer> tokens) {
      this.tokens = tokens;
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
        return operand == null ? null : EnumSet.complementOf(EnumSet.copyOf(operand));
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
      var contexts = SYMBOL_CONTEXTS.get(tokens.get(position++));
      return contexts == null ? null : EnumSet.copyOf(contexts);
    }

    private boolean accept(int tokenType) {
      if (position < tokens.size() && tokens.get(position) == tokenType) {
        position++;
        return true;
      }
      return false;
    }
  }
}
