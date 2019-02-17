/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com>
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
import org.github._1c_syntax.bsl.parser.BSLParser;
import org.github._1c_syntax.bsl.parser.BSLParserRuleContext;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class CancelDiagnostic extends AbstractVisitorDiagnostic {

  @Override
  public DiagnosticSeverity getSeverity() {
    return DiagnosticSeverity.Warning;
  }

  @Override
  public ParseTree visitSub(BSLParser.SubContext ctx) {

    Collection<ParseTree> params = Trees.findAllRuleNodes(ctx, BSLParser.RULE_param);

    boolean inParams = params.stream().filter(node ->
      ((BSLParser.ParamContext) node).IDENTIFIER().getText().equalsIgnoreCase("Отказ")
    ).count() == 1;

    int skip = 0;
    if (!inParams) {
      skip = 1;
    }

    Collection<ParseTree> assigns = Trees.findAllRuleNodes(ctx, BSLParser.RULE_assignment);

    List<ParseTree> tree = assigns.stream().filter(node ->
      ((BSLParser.AssignmentContext) node).complexIdentifier().getText().equalsIgnoreCase("Отказ")
    ).collect(Collectors.toList());


    tree.stream().skip(skip).forEach(
      (ParseTree ident) -> {

        boolean validAsign = ((BSLParser.AssignmentContext) ident).expression().getText().equalsIgnoreCase("Истина");

        if (!validAsign) {
          addDiagnostic((BSLParserRuleContext) ident.getParent());
        }

      }
    );

    return super.visitSub(ctx);
  }

}
