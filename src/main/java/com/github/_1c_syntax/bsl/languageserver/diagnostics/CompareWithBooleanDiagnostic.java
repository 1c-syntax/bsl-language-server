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

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.ExpressionTypeInferencer;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BinaryOperationNode;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BslExpression;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BslOperator;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.ExpressionNodeType;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.RequiredArgsConstructor;

/**
 * Диагностика, выявляющая избыточное сравнение выражений с булевой константой
 * {@code Истина}/{@code Ложь} через операторы {@code =} и {@code <>}
 * (например {@code Если Значение = Истина Тогда}).
 * <p>
 * Срабатывает только если тип второго операнда выведен и он <b>однозначно</b>
 * {@code Булево} (не объединение типов), иначе сравнение может быть обоснованным.
 */
@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MINOR,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.CLUMSY,
    DiagnosticTag.SUSPICIOUS
  }
)
@RequiredArgsConstructor
public class CompareWithBooleanDiagnostic extends AbstractExpressionTreeDiagnostic {

  private static final TypeRef BOOLEAN = new TypeRef(TypeKind.PRIMITIVE, "Булево");

  private final ExpressionTypeInferencer expressionTypeInferencer;
  private final TypeRegistry typeRegistry;

  /**
   * Проверяет бинарную операцию на избыточное сравнение с булевой константой.
   * Диагностика срабатывает, если операция является сравнением ({@code =} или {@code <>}),
   * один из её операндов — булева константа ({@code Истина}/{@code Ложь}),
   * а тип второго операнда однозначно {@code Булево}.
   *
   * @param node узел бинарной операции дерева выражений
   */
  @Override
  protected void visitBinaryOperation(BinaryOperationNode node) {

    if (node.getOperator() != BslOperator.EQUAL && node.getOperator() != BslOperator.NOT_EQUAL) {
      super.visitBinaryOperation(node);
      return;
    }

    if (isRedundantComparison(node.getLeft(), node.getRight())) {
      addDiagnostic(node);
    }

    super.visitBinaryOperation(node);
  }

  /**
   * Определяет, является ли сравнение операндов избыточным: один из операндов —
   * булева константа, а второй гарантированно имеет тип {@code Булево}.
   * Если булевы константы стоят с обеих сторон, сравнение избыточно само по себе.
   *
   * @param left  левый операнд сравнения
   * @param right правый операнд сравнения
   * @return {@code true}, если сравнение с булевой константой избыточно
   */
  private boolean isRedundantComparison(BslExpression left, BslExpression right) {
    if (isBooleanLiteral(left)) {
      return isBooleanLiteral(right) || isBoolean(right);
    }
    return isBooleanLiteral(right) && isBoolean(left);
  }

  /**
   * Определяет, является ли узел выражения булевой константой.
   * Распознаются как русские ({@code Истина}/{@code Ложь}), так и английские
   * ({@code True}/{@code False}) литералы.
   *
   * @param expression проверяемый узел дерева выражений
   * @return {@code true}, если узел — литерал {@code Истина}/{@code Ложь}, иначе {@code false}
   */
  private static boolean isBooleanLiteral(BslExpression expression) {
    if (expression.getNodeType() != ExpressionNodeType.LITERAL) {
      return false;
    }

    var ast = expression.getRepresentingAst();
    if (!(ast instanceof BSLParser.ConstValueContext constValue)) {
      return false;
    }

    return constValue.getToken(BSLParser.TRUE, 0) != null
      || constValue.getToken(BSLParser.FALSE, 0) != null;
  }

  /**
   * Проверяет, что выведенный тип выражения однозначно {@code Булево}.
   * Если тип вывести не удалось либо это объединение типов, метод возвращает
   * {@code false} — такое сравнение не считается ошибочным.
   *
   * @param expression проверяемый операнд сравнения
   * @return {@code true}, если тип операнда однозначно {@code Булево}
   */
  private boolean isBoolean(BslExpression expression) {
    // Материализуем workspace-scoped TypeRegistry (bootstrap глобального скоупа),
    // прежде чем выводить тип выражения. См. TypeService#expressionTypesAt.
    typeRegistry.resolve("");
    var types = expressionTypeInferencer.infer(expression, documentContext);
    return types.size() == 1 && types.refs().contains(BOOLEAN);
  }

  /**
   * Регистрирует замечание на всё выражение сравнения — от первого токена левого операнда
   * до последнего токена правого операнда.
   *
   * @param node узел бинарной операции, на которую добавляется замечание
   */
  private void addDiagnostic(BinaryOperationNode node) {
    var startToken = Trees.getTokens(node.getLeft().getRepresentingAst())
      .stream()
      .findFirst()
      .orElseThrow();

    var endToken = Trees.getTokens(node.getRight().getRepresentingAst())
      .stream()
      .reduce((first, second) -> second)
      .orElseThrow();

    diagnosticStorage.addDiagnostic(startToken, endToken);
  }
}
