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
   * Обработать условную директиву в последовательном обходе файла.
   *
   * @param node инструкция препроцессора, включая незавершённую
   * @return {@code true} для #Если/#ИначеЕсли/#Иначе/#КонецЕсли;
   *     остальные инструкции не меняют состояние и дают {@code false}
   */
  public boolean enter(BSLParser.PreprocessorContext node) {
    if (node.preproc_if() != null) {
      enterIf(node.preproc_if());
    } else if (node.preproc_elsif() != null) {
      enterElsif(node.preproc_elsif());
    } else if (node.preproc_else() != null) {
      enterElse();
    } else if (node.preproc_endif() != null) {
      enterEndif();
    } else {
      return false;
    }
    return true;
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
    return PreprocessorExpressionEvaluator.evaluate(tokens);
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

}
