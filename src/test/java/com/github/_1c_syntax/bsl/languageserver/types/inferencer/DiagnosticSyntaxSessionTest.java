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

import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BslExpression;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLTokenizer;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosticSyntaxSessionTest {
  @Test
  void repeatedRootsInOneBatchReuseTheTree() {
    var expression = expression();
    try (var scope = new DiagnosticDeclarationSession().open()) {
      var first = DiagnosticDeclarationSession.expressionTree(expression);
      assertThat(first).isNotNull();
      assertThat(DiagnosticDeclarationSession.expressionTree(expression)).isSameAs(first);
    }
  }

  @Test
  void separateBatchesAndUnscopedQueriesHaveSeparateTrees() {
    var expression = expression();
    BslExpression first;
    try (var scope = new DiagnosticDeclarationSession().open()) {
      first = DiagnosticDeclarationSession.expressionTree(expression);
    }
    try (var scope = new DiagnosticDeclarationSession().open()) {
      assertThat(DiagnosticDeclarationSession.expressionTree(expression)).isNotSameAs(first);
    }
    assertThat(DiagnosticDeclarationSession.expressionTree(expression))
      .isNotSameAs(DiagnosticDeclarationSession.expressionTree(expression));
  }

  @Test
  void nestedBatchRestoresTheOuterTreeAndReparsedSyntaxIsDistinct() {
    var expression = expression();
    try (var outer = new DiagnosticDeclarationSession().open()) {
      var first = DiagnosticDeclarationSession.expressionTree(expression);
      try (var nested = new DiagnosticDeclarationSession().open()) {
        assertThat(DiagnosticDeclarationSession.expressionTree(expression)).isNotSameAs(first);
      }
      assertThat(DiagnosticDeclarationSession.expressionTree(expression)).isSameAs(first);
      assertThat(DiagnosticDeclarationSession.expressionTree(expression())).isNotSameAs(first);
    }
  }

  @Test
  void concurrentDiagnosticsUseTheSameCompletedTree() throws Exception {
    var expression = expression();
    var batch = new DiagnosticDeclarationSession();
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first = executor.submit(() -> parsedAfterStart(batch, expression, ready, start));
      var second = executor.submit(() -> parsedAfterStart(batch, expression, ready, start));
      try {
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      } finally {
        start.countDown();
      }
      assertThat(first.get(10, TimeUnit.SECONDS)).isNotNull()
        .isSameAs(second.get(10, TimeUnit.SECONDS));
    }
  }

  @Test
  void aMissingExpressionIsAcceptedWithAndWithoutAScope() {
    assertThat(DiagnosticDeclarationSession.expressionTree(null)).isNull();
    try (var scope = new DiagnosticDeclarationSession().open()) {
      assertThat(DiagnosticDeclarationSession.expressionTree(null)).isNull();
    }
  }


  @Test
  void memberQueriesReuseOnlyTheirBatchAndExactAst() {
    var member = member("Result = Object.Property;");
    BslExpression first;
    try (var scope = new DiagnosticDeclarationSession().open()) {
      first = ExpressionAtPosition.findExpressionTree(member).orElseThrow();
      assertThat(ExpressionAtPosition.findExpressionTree(member)).containsSame(first);
      assertThat(ExpressionAtPosition.findExpressionTree(member("Result = Other.Property;")))
        .isPresent().get().isNotSameAs(first);
    }
    try (var scope = new DiagnosticDeclarationSession().open()) {
      assertThat(ExpressionAtPosition.findExpressionTree(member)).isPresent().get().isNotSameAs(first);
    }
    assertThat(ExpressionAtPosition.findExpressionTree(member)).isPresent().get().isNotSameAs(first);
  }

  @Test
  void memberAndAssignmentQueriesShareCompletedSyntaxAcrossDiagnosticTasks() throws Exception {
    var member = member("Result = Object.Property;");
    var batch = new DiagnosticDeclarationSession();
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first = executor.submit(() -> {
        try (var scope = batch.open()) {
          return ExpressionAtPosition.findExpressionTree(member).orElseThrow();
        }
      });
      var second = executor.submit(() -> {
        try (var scope = batch.open()) {
          return ExpressionAtPosition.findExpressionTree(member).orElseThrow();
        }
      });
      assertThat(first.get(10, TimeUnit.SECONDS)).isSameAs(second.get(10, TimeUnit.SECONDS));
      try (var scope = batch.open()) {
        var rule = Trees.<BSLParser.ExpressionContext>getAncestorByRuleIndex(
          (org.antlr.v4.runtime.ParserRuleContext) member.getParent(), BSLParser.RULE_expression);
        assertThat(DiagnosticDeclarationSession.expressionTree(rule)).isSameAs(first.get());
      }
    }
  }

  private static org.antlr.v4.runtime.tree.TerminalNode member(String source) {
    var ast = new BSLTokenizer(source).getAst();
    return Trees.findAllRuleNodes(ast, BSLParser.RULE_accessProperty).stream()
      .map(BSLParser.AccessPropertyContext.class::cast).findFirst().orElseThrow().IDENTIFIER();
  }

  private static BslExpression parsedAfterStart(DiagnosticDeclarationSession batch,
                                               BSLParser.ExpressionContext expression,
                                               CountDownLatch ready, CountDownLatch start) throws Exception {
    try (var scope = batch.open()) {
      ready.countDown();
      assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
      return DiagnosticDeclarationSession.expressionTree(expression);
    }
  }

  private static BSLParser.ExpressionContext expression() {
    var ast = new BSLTokenizer("Result = New Structure(\"Key\", 42);").getAst();
    return Trees.findAllRuleNodes(ast, BSLParser.RULE_assignment).stream()
      .map(BSLParser.AssignmentContext.class::cast).findFirst().orElseThrow().expression();
  }
}
