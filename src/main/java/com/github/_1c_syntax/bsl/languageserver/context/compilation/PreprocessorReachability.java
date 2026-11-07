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

import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.compilation.PreprocessorCondition.Value;
import com.github._1c_syntax.bsl.languageserver.context.compilation.PreprocessorCondition.Symbol;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.github._1c_syntax.bsl.languageserver.context.compilation.PreprocessorCondition.and;
import static com.github._1c_syntax.bsl.languageserver.context.compilation.PreprocessorCondition.or;
import static com.github._1c_syntax.bsl.languageserver.context.compilation.PreprocessorCondition.not;

/**
 * Проверка достижимости текущей ветки препроцессора при последовательном обходе файла.
 * <p>
 * Для BSL учитываются допустимые сочетания символов платформы из {@link PreprocessorContexts}.
 * В OneScript символы считаются независимыми: определять их может вызывающее приложение.
 * В обоих диалектах дополнительно проверяются логические противоречия, включая символы ОС.
 * Конкретная ОС и пользовательские определения не предполагаются.
 * <p>
 * Поиск ограничен числом вычислений узлов формулы. Исчерпание лимита и ошибки разбора
 * сохраняют потенциальную достижимость. Экземпляр принадлежит одному обходу файла.
 */
public final class PreprocessorReachability {
  private final Optional<PreprocessorContexts> platform;
  private final Deque<Branch> branches = new ArrayDeque<>();
  private final Map<String, Symbol> symbols = new HashMap<>();
  private boolean wellFormed = true;

  /**
   * Создать состояние для одного файла.
   *
   * @param fileType диалект; ограничения контекстов 1С применяются только к BSL.
   */
  public PreprocessorReachability(FileType fileType) {
    platform = fileType == FileType.BSL ? Optional.of(new PreprocessorContexts()) : Optional.empty();
  }

  /**
   * Обработать условную директиву при последовательном обходе файла.
   *
   * @param node инструкция препроцессора
   * @return {@code true} для условной инструкции; области и прочие директивы не меняют состояние
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
   * Открыть вложенную инструкцию, ограниченную текущей веткой.
   *
   * @param node директива с условием первой ветки.
   */
  public void enterIf(BSLParser.Preproc_ifContext node) {
    platform.ifPresent(contexts -> contexts.enterIf(node));
    var parent = branches.peek();
    var branch = new Branch(parent == null ? Value.TRUE : parent.active);
    enter(branch, node.preproc_expression());
    branches.push(branch);
  }

  /**
   * Перейти к альтернативе с учётом всех предыдущих условий этой инструкции.
   *
   * @param node директива с условием следующей ветки.
   */
  public void enterElsif(BSLParser.Preproc_elsifContext node) {
    platform.ifPresent(contexts -> contexts.enterElsif(node));
    var branch = branches.peek();
    if (branch == null) {
      wellFormed = false;
    } else {
      enter(branch, node.preproc_expression());
    }
  }

  /** Перейти в остаток инструкции; повторный или непарный {@code #Иначе} не доказывает недостижимость. */
  public void enterElse() {
    platform.ifPresent(PreprocessorContexts::enterElse);
    var branch = branches.peek();
    if (branch == null || branch.otherwise) {
      wellFormed = false;
    } else {
      branch.active = branch.remaining;
      branch.otherwise = true;
    }
  }

  /** Закрыть инструкцию и восстановить состояние родительской ветки. */
  public void enterEndif() {
    platform.ifPresent(PreprocessorContexts::enterEndif);
    if (branches.poll() == null) {
      wellFormed = false;
    }
  }

  /**
   * Проверить, исключена ли текущая ветка при любом допустимом наборе символов.
   *
   * @return {@code true} только при доказанной недостижимости; неизвестность даёт {@code false}.
   */
  public boolean isUnreachable() {
    return isUnreachable(ExecutionContext.ALL);
  }

  /**
   * Проверить ветку с учётом возможностей охватывающего модуля и директивы метода.
   *
   * @param possibleContexts возможные режимы окружающего кода; неизвестные ограничения
   *     не должны сужать это множество. В OneScript множество не используется.
   * @return {@code true}, если ветка исключена во всех возможных режимах.
   */
  public boolean isUnreachable(Set<ExecutionContext> possibleContexts) {
    var branch = branches.peek();
    if (!wellFormed || branch == null) {
      return false;
    }
    if (branch.active == Value.FALSE) {
      return true;
    }
    if (platform.isPresent()) {
      var contexts = platform.get().active();
      if (contexts != null) {
        return Collections.disjoint(contexts, possibleContexts);
      }
    }
    return !new PreprocessorSatisfiability(symbols.size()).canBeTrue(branch.active);
  }

  /**
   * Получить ограничения текущей ветки по режимам платформы.
   *
   * @return множество режимов BSL, включая пустое для исключённой ветки; {@code null}
   *     для OneScript, неизвестного условия или повреждённой структуры. Множество нельзя изменять.
   */
  public @Nullable Set<ExecutionContext> activeContexts() {
    return wellFormed && platform.isPresent() ? platform.orElseThrow().active() : null;
  }

  /**
   * Проверить структуру после обхода всего файла.
   *
   * @return все инструкции закрыты, альтернативы упорядочены и условия не содержат ошибок парсера.
   */
  public boolean isWellFormed() {
    return wellFormed && branches.isEmpty();
  }

  private void enter(Branch branch, BSLParser.@Nullable Preproc_expressionContext expression) {
    if (branch.otherwise || expression == null || hasErrors(expression)) {
      wellFormed = false;
      return;
    }
    var condition = parse(expression);
    branch.active = and(branch.remaining, condition);
    branch.remaining = and(branch.remaining, not(condition));
  }

  /** Ошибки восстановления парсера не превращаются в предположения о значениях символов. */
  private static boolean hasErrors(ParseTree tree) {
    if (tree instanceof ErrorNode || (tree instanceof ParserRuleContext rule && rule.exception != null)) {
      return true;
    }
    for (var i = 0; i < tree.getChildCount(); i++) {
      if (hasErrors(tree.getChild(i))) {
        return true;
      }
    }
    return false;
  }

  private PreprocessorCondition parse(BSLParser.Preproc_expressionContext expression) {
    var nested = expression.preproc_expression();
    var result = nested == null ? parse(expression.preproc_logicalExpression()) : parse(nested);
    return expression.PREPROC_NOT_KEYWORD() == null ? result : not(result);
  }

  private PreprocessorCondition parse(BSLParser.Preproc_logicalOperandContext operand) {
    PreprocessorCondition result;
    if (operand.preproc_symbol() != null) {
      var name = operand.preproc_symbol().getText().toLowerCase(Locale.ROOT);
      result = symbols.computeIfAbsent(name, ignored -> new Symbol(symbols.size()));
    } else if (operand.preproc_logicalOperand() != null) {
      result = parse(operand.preproc_logicalOperand());
    } else {
      result = parse(operand.preproc_logicalExpression());
    }
    return operand.PREPROC_NOT_KEYWORD() == null ? result : not(result);
  }

  /** Грамматика хранит И/ИЛИ плоским списком; сначала сворачиваются группы И. */
  private PreprocessorCondition parse(BSLParser.Preproc_logicalExpressionContext expression) {
    var operands = expression.preproc_logicalOperand();
    var operations = expression.preproc_boolOperation();
    var conjunction = parse(operands.getFirst());
    PreprocessorCondition disjunction = Value.FALSE;
    for (var i = 0; i < operations.size(); i++) {
      var next = parse(operands.get(i + 1));
      if (operations.get(i).getStart().getType() == BSLParser.PREPROC_AND_KEYWORD) {
        conjunction = and(conjunction, next);
      } else {
        disjunction = or(disjunction, conjunction);
        conjunction = next;
      }
    }
    return or(disjunction, conjunction);
  }

  /** Условия текущей и ещё не выбранных веток одной инструкции. */
  private static final class Branch {
    private PreprocessorCondition active = Value.UNKNOWN;
    private PreprocessorCondition remaining;
    private boolean otherwise;

    private Branch(PreprocessorCondition parent) {
      remaining = parent;
    }
  }

}
