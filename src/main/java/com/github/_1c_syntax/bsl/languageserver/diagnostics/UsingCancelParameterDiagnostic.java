/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLLexer;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 10,
  tags = {
    DiagnosticTag.STANDARD,
    DiagnosticTag.BADPRACTICE
  }
)
public class UsingCancelParameterDiagnostic extends AbstractVisitorDiagnostic {

  private static final Pattern cancelPattern = Pattern.compile(
    "отказ|cancel",
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
  );

  public UsingCancelParameterDiagnostic(DiagnosticInfo info) {
    super(info);
  }

  @Override
  public ParseTree visitSub(BSLParser.SubContext ctx) {

    Collection<ParseTree> params = Trees.findAllRuleNodes(ctx, BSLParser.RULE_param);

    boolean inParams = params.stream().anyMatch(
      node -> cancelPattern.matcher(((BSLParser.ParamContext) node).IDENTIFIER()
        .getText())
        .matches());

    // ToDO обрабатывать не только в параметрах
    if (!inParams) {
      return ctx;
    }

    int skip = 0;
    Collection<ParseTree> assigns = Trees.findAllRuleNodes(ctx, BSLParser.RULE_assignment);

    List<ParseTree> tree = assigns.stream()
      .filter(
        node -> cancelPattern.matcher(((BSLParser.AssignmentContext) node).lValue()
          .getText())
          .matches()
      ).collect(Collectors.toList());

    tree.stream().skip(skip).forEach(
      (ParseTree ident) -> {
        if (!rightPartIsValid((BSLParser.AssignmentContext) ident)) {
          diagnosticStorage.addDiagnostic((BSLParserRuleContext) ident.getParent());
        }
      }
    );

    return ctx;
  }

  private static boolean rightPartIsValid(BSLParser.AssignmentContext ident) {
    return equalTrue(ident) || orCancel(ident);
  }

  private static boolean orCancel(BSLParser.AssignmentContext ident) {

    BSLParser.ExpressionContext expression = ident.expression();
    if (expression != null) {

      BSLParser.OperationContext logicalOperation = expression.operation(0);
      if (logicalOperation != null) {

        BSLParser.BoolOperationContext boolOperation = logicalOperation.boolOperation();
        if (boolOperation != null
          && boolOperation.OR_KEYWORD() != null) {

          return expression
            .member()
            .stream()
            .anyMatch(token -> cancelPattern.matcher(token.getText())
              .matches());
        }
      }
    }

    return false;
  }

  private static boolean equalTrue(BSLParser.AssignmentContext ident) {
    return ident.expression().getStop().getType() == BSLLexer.TRUE;
  }

}
