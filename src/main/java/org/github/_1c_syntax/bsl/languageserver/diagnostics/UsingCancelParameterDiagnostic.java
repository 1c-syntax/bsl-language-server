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
package org.github._1c_syntax.bsl.languageserver.diagnostics;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.Trees;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.github._1c_syntax.bsl.parser.BSLLexer;
import org.github._1c_syntax.bsl.parser.BSLParser;
import org.github._1c_syntax.bsl.parser.BSLParserRuleContext;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class UsingCancelParameterDiagnostic extends AbstractVisitorDiagnostic {

  private static final Pattern cancelPattern = Pattern.compile("отказ|cancel");

  @Override
  public DiagnosticSeverity getSeverity() {
    return DiagnosticSeverity.Error;
  }

  @Override
  public ParseTree visitSub(BSLParser.SubContext ctx) {

    Collection<ParseTree> params = Trees.findAllRuleNodes(ctx, BSLParser.RULE_param);

    boolean inParams = params.stream().anyMatch(
      node -> cancelPattern.matcher(((BSLParser.ParamContext) node).IDENTIFIER()
        .getText()
        .toLowerCase(Locale.getDefault()))
        .matches());

    // ToDO обрабатывать не только в параметрах
    if (!inParams) {
      return ctx;
    }

    int skip = 0;
    Collection<ParseTree> assigns = Trees.findAllRuleNodes(ctx, BSLParser.RULE_assignment);

    List<ParseTree> tree = assigns.stream()
      .filter(
        node -> cancelPattern.matcher(((BSLParser.AssignmentContext) node).complexIdentifier()
          .getText()
          .toLowerCase(Locale.getDefault()))
          .matches()
      ).collect(Collectors.toList());

    tree.stream().skip(skip).forEach(
      (ParseTree ident) -> {
        if (!rightPartIsValid((BSLParser.AssignmentContext) ident)) {
          addDiagnostic((BSLParserRuleContext) ident.getParent());
        }

      }
    );

    return ctx;
  }

  private static boolean rightPartIsValid(BSLParser.AssignmentContext ident) {

    return equalTrue(ident) || orCancel(ident);
  }


  private static boolean orCancel(BSLParser.AssignmentContext ident) {

    BSLParser.OperationContext logicaloperation = ident.expression().operation(0);
    if (logicaloperation != null && logicaloperation.boolOperation().OR_KEYWORD() != null) {

      return ident.expression()
        .member()
        .stream()
        .anyMatch(token -> cancelPattern.matcher(token.getText().toLowerCase(Locale.getDefault()))
        .matches());

    }

    return false;

  }

  private static boolean equalTrue(BSLParser.AssignmentContext ident) {

    return ident.expression().getStop().getType() == BSLLexer.TRUE;
  }

}
