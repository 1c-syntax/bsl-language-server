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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLLexer;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ErrorNode;

import java.util.StringJoiner;
import java.util.stream.IntStream;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.CRITICAL,
  scope = DiagnosticScope.ALL,
  minutesToFix = 5,
  tags = {
    DiagnosticTag.ERROR
  }
)
public class ParseErrorDiagnostic extends AbstractListenerDiagnostic {

  public static final int EOF = -1;

  @Override
  public void visitErrorNode(ErrorNode node) {
    if (node.getSymbol().getTokenIndex() == -1) {
      diagnosticStorage.addDiagnostic(
        ((ParserRuleContext) node.getParent()).getStart(),
        info.getMessage(node.getText())
      );
    }
  }

  @Override
  public void enterFile(BSLParser.FileContext ctx) {
    var ast = this.documentContext.getAst();
    var initialExpectedString = info.getResourceString("expectedTokens") + " ";

    Trees.getDescendants(ast).stream()
      .filter(ParserRuleContext.class::isInstance)
      .map(ParserRuleContext.class::cast)
      .filter(node -> node.exception != null)
      .forEach((ParserRuleContext node) -> {
        var expectedTokens = node.exception.getExpectedTokens();
        var sj = new StringJoiner(", ");
        expectedTokens.getIntervals().stream()
          .flatMapToInt(interval -> IntStream.rangeClosed(interval.a, interval.b))
          .mapToObj(ParseErrorDiagnostic::getTokenName)
          .forEachOrdered(sj::add);

        var errorToken = node.exception.getOffendingToken();
        if (errorToken.getType() == EOF) {
          errorToken = node.getStart();
        }

        diagnosticStorage.addDiagnostic(
          errorToken,
          info.getMessage(initialExpectedString + sj)
        );
      });
  }

  private static String getTokenName(int tokenType) {
    String value = BSLLexer.VOCABULARY.getLiteralName(tokenType);
    if (value == null) {
      value = BSLLexer.VOCABULARY.getSymbolicName(tokenType);
    }

    return value;
  }
}
