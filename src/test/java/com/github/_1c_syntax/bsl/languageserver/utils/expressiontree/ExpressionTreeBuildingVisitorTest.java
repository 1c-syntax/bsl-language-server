/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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
package com.github._1c_syntax.bsl.languageserver.utils.expressiontree;

import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.parser.BSLParser.IfStatementContext;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.antlr.v4.runtime.tree.Trees;

@SpringBootTest
class ExpressionTreeBuildingVisitorTest {

  @Test
  void testEmptyParenthesesHandling() {
    // Create test code with empty parentheses
    var code = """
      Процедура ТестПроцедура()
        Если () Тогда
          Возврат;
        КонецЕсли;
      КонецПроцедуры
      """;

    var documentContext = TestUtils.getDocumentContext(code);

    // Assert that no exception is thrown when processing the if statement with empty parentheses
    assertThatNoException().isThrownBy(() -> {
      var ast = documentContext.getAst();
      var ifStatement = Trees.getDescendants(ast)
        .stream()
        .filter(node -> node instanceof IfStatementContext)
        .map(node -> (IfStatementContext) node)
        .findFirst()
        .orElseThrow();
      
      var visitor = new ExpressionTreeBuildingVisitor();
      
      // Find the expression node within the if statement
      var expression = ifStatement.ifBranch().expression();
      
      // Try to build expression tree from empty parentheses
      visitor.visitExpression(expression);
      
      // Result should be null since there's no expression
      assertThat(visitor.getExpressionTree()).isNull();
    });
  }
}