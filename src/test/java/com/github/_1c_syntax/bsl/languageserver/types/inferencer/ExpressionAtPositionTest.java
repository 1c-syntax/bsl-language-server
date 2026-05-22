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
package com.github._1c_syntax.bsl.languageserver.types.inferencer;

import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тесты для {@link ExpressionAtPosition} — поиска assignment/forEach/expression
 * по позиции курсора.
 */
@SpringBootTest
class ExpressionAtPositionTest {

  @Test
  void findAssignmentRhsReturnsRhsExpression() {
    // given
    var content = "А = 10 + 20;\n";
    var dc = TestUtils.getDocumentContext(content);

    // when — позиция внутри «10»
    var rhs = ExpressionAtPosition.findAssignmentRhs(dc, new Position(0, 5));

    // then
    assertThat(rhs).isPresent();
    assertThat(rhs.get().getText()).contains("10").contains("20");
  }

  @Test
  void findAssignmentReturnsAssignmentNode() {
    // given
    var content = "ИмяПеременной = \"строка\";\n";
    var dc = TestUtils.getDocumentContext(content);

    // when
    var assignment = ExpressionAtPosition.findAssignment(dc, new Position(0, 5));

    // then
    assertThat(assignment).isPresent();
    assertThat(assignment.get().getText()).contains("ИмяПеременной");
  }

  @Test
  void findAssignmentEmptyWhenPositionNotInAssignment() {
    // given — позиция в декларации процедуры, не в assignment.
    var content = "Процедура Тест()\nКонецПроцедуры\n";
    var dc = TestUtils.getDocumentContext(content);

    // when
    var assignment = ExpressionAtPosition.findAssignment(dc, new Position(0, 12));

    // then
    assertThat(assignment).isEmpty();
  }

  @Test
  void findForEachBindingAtReturnsForEachWhenPositionOnIterator() {
    // given
    var content = """
                Процедура Тест(Коллекция)
                  Для Каждого Элемент Из Коллекция Цикл
                  КонецЦикла;
                КонецПроцедуры
                """;
    var dc = TestUtils.getDocumentContext(content);
    int iteratorOffset = content.indexOf("Элемент");
    int line = content.substring(0, iteratorOffset).split("\n").length - 1;
    int lineStart = content.lastIndexOf('\n', iteratorOffset) + 1;
    var position = new Position(line, iteratorOffset - lineStart + 1);

    // when
    var forEach = ExpressionAtPosition.findForEachBindingAt(dc, position);

    // then
    assertThat(forEach).isPresent();
  }

  @Test
  void findForEachBindingAtEmptyWhenPositionOutsideIterator() {
    // given
    var content = """
                Процедура Тест()
                  Для Каждого Элемент Из Коллекция Цикл
                  КонецЦикла;
                КонецПроцедуры
                """;
    var dc = TestUtils.getDocumentContext(content);
    // позиция на ключевом слове «Для»
    var position = new Position(1, 2);

    // when
    var forEach = ExpressionAtPosition.findForEachBindingAt(dc, position);

    // then
    assertThat(forEach).isEmpty();
  }

  @Test
  void findExpressionContextReturnsNearestExpression() {
    // given
    var content = "ПервыйРезультат = 100 + 200;\n";
    var dc = TestUtils.getDocumentContext(content);

    // when — позиция внутри литерала «100».
    var expr = ExpressionAtPosition.findExpressionContext(dc, new Position(0, 20));

    // then
    assertThat(expr).isPresent();
  }

  @Test
  void findCallStatementContextReturnsCallStatement() {
    // given — standalone vyzov Сообщить("привет");
    var content = "Сообщить(\"привет\");\n";
    var dc = TestUtils.getDocumentContext(content);

    // when — позиция внутри идентификатора Сообщить.
    var callStmt = ExpressionAtPosition.findCallStatementContext(dc, new Position(0, 3));

    // then
    assertThat(callStmt).isPresent();
  }

  @Test
  void findCallStatementContextEmptyForAssignment() {
    // given — assignment, не callStatement.
    var content = "А = 100;\n";
    var dc = TestUtils.getDocumentContext(content);

    // when
    var callStmt = ExpressionAtPosition.findCallStatementContext(dc, new Position(0, 0));

    // then
    assertThat(callStmt).isEmpty();
  }

  @Test
  void findLValueContextReturnsLValue() {
    // given — assignment с lValue dot-chain.
    var content = "Объект.Поле = 100;\n";
    var dc = TestUtils.getDocumentContext(content);

    // when — позиция внутри `Поле`.
    var lValue = ExpressionAtPosition.findLValueContext(dc, new Position(0, 8));

    // then
    assertThat(lValue).isPresent();
  }

  @Test
  void findComplexIdentifierContextReturnsComplex() {
    // given — RHS с dot-chain.
    var content = "А = Объект.Свойство;\n";
    var dc = TestUtils.getDocumentContext(content);

    // when — позиция внутри `Свойство`.
    var ci = ExpressionAtPosition.findComplexIdentifierContext(dc, new Position(0, 12));

    // then
    assertThat(ci).isPresent();
  }

  @Test
  void findExpressionTreeReturnsBslExpressionForAssignmentRhs() {
    // given
    var content = "Х = 1 + 2;\n";
    var dc = TestUtils.getDocumentContext(content);

    // when — позиция внутри «1».
    var tree = ExpressionAtPosition.findExpressionTree(dc, new Position(0, 4));

    // then — построено выражение поверх ExpressionContext.
    assertThat(tree).isPresent();
  }

  @Test
  void findExpressionTreeReturnsBslExpressionForLValue() {
    // given
    var content = "Объект.Свойство = 1;\n";
    var dc = TestUtils.getDocumentContext(content);

    // when — позиция внутри `Свойство` (lValue, не expression).
    var tree = ExpressionAtPosition.findExpressionTree(dc, new Position(0, 10));

    // then — fallback на findLValueContext сработал.
    assertThat(tree).isPresent();
  }
}
