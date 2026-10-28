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
 * Состояние вложенных веток препроцессора при последовательном обходе файла.
 * <p>
 * Директивы передаются в порядке исходного текста через {@link #enterIf}, {@link #enterElsif},
 * {@link #enterElse} и {@link #enterEndif}. Ветки используют контексты, оставшиеся после
 * предыдущих веток инструкции; вложенные инструкции ограничены активной веткой родителя.
 * Перед повторным обходом нужен {@link #clear()}.
 * <p>
 * Пустое множество означает известную недостижимость, {@code null} — неизвестное условие,
 * например символ ОС. Неизвестность распространяется на остаток инструкции и вложенные ветки.
 * Диагностика в этом случае пропускает проверку, а фильтрация CFG сохраняет обе ветки.
 * Экземпляр относится к одному обходу и не предназначен для совместного использования потоками.
 * Статический {@link #evaluate} не использует состояние обхода.
 */
public final class PreprocessorContexts {

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

  /**
   * Открытые инструкции {@code #Если}, внутренняя - сверху.
   */
  private final Deque<Branch> branches = new ArrayDeque<>();

  /**
   * Сбросить стек перед новым обходом файла.
   * <p>
   * После сброса {@link #active()} возвращает все контексты.
   */
  public void clear() {
    branches.clear();
  }

  /**
   * Открыть инструкцию {@code #Если} внутри текущей ветки.
   *
   * @param ctx директива с условием первой ветки.
   */
  public void enterIf(BSLParser.Preproc_ifContext ctx) {
    var parent = branches.peek();
    var branch = new Branch(parent == null ? ExecutionContext.ALL : parent.active);
    branch.enter(evaluate(ctx.preproc_expression()));
    branches.push(branch);
  }

  /**
   * Перейти к {@code #ИначеЕсли}, исключив контексты пройденных веток этой инструкции.
   * При отсутствии открытой инструкции состояние не меняется.
   *
   * @param ctx директива с условием следующей ветки.
   */
  public void enterElsif(BSLParser.Preproc_elsifContext ctx) {
    var branch = branches.peek();
    if (branch != null) {
      branch.enter(evaluate(ctx.preproc_expression()));
    }
  }

  /**
   * Перейти к {@code #Иначе}: активными становятся оставшиеся контексты.
   * Неизвестное предыдущее условие сохраняет неизвестность. Без открытой инструкции вызов ничего не меняет.
   */
  public void enterElse() {
    var branch = branches.peek();
    if (branch != null) {
      branch.enterElse();
    }
  }

  /**
   * Закрыть ближайшую инструкцию {@code #Если} и восстановить ветку родителя.
   * При пустом стеке вызов ничего не меняет.
   */
  public void enterEndif() {
    branches.poll();
  }

  /**
   * Получить ограничения препроцессора в текущей точке обхода.
   * <p>
   * Результат не пересекается с контекстами модуля, директивой метода или настройками диагностики.
   * Возвращаемое множество представляет состояние обхода; вызывающему не следует его изменять.
   *
   * @return активные контексты, все контексты вне инструкций или пустое множество для исключённой
   *     ветки; {@code null}, если условие окружающей ветки не удалось вычислить.
   */
  public @Nullable Set<ExecutionContext> active() {
    var branch = branches.peek();
    return branch == null ? ExecutionContext.ALL : branch.active;
  }

  /**
   * Вычислить условие как множество контекстов, в которых оно истинно.
   * <p>
   * Поддерживаются символы контекстов 1С, скобки и операции {@code НЕ}, {@code И}, {@code ИЛИ}
   * с обычным приоритетом. Символы ОС, неизвестные лексемы и неполное выражение дают {@code null}.
   * Неизвестные части не упрощаются по правилам короткого замыкания.
   *
   * @param expression условие без окружающего стека директив; может быть {@code null}.
   * @return самостоятельное изменяемое множество истинных контекстов, в том числе пустое;
   *     {@code null} для отсутствующего или невычислимого условия.
   */
  public static @Nullable Set<ExecutionContext> evaluate(BSLParser.@Nullable Preproc_expressionContext expression) {
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
      remaining = parentActive == null ? null : ExecutionContext.copyOf(parentActive);
    }

    private void enter(@Nullable Set<ExecutionContext> condition) {
      if (remaining == null || condition == null) {
        active = null;
        remaining = null;
        return;
      }
      var newActive = ExecutionContext.copyOf(remaining);
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
        return operand == null ? null : EnumSet.complementOf(ExecutionContext.copyOf(operand));
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
}
