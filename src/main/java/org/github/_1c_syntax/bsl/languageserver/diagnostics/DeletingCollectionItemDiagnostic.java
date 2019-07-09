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
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import org.github._1c_syntax.bsl.parser.BSLParser;

import java.util.List;
import java.util.stream.Collectors;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 5
)
public class DeletingCollectionItemDiagnostic extends AbstractVisitorDiagnostic {

  @Override
  public ParseTree visitForEachStatement(BSLParser.ForEachStatementContext ctx) {

    String iterator = ctx.IDENTIFIER().getText();
    String expression = ctx.expression().getText();

    List<ParseTree> childIdentifiers = Trees.findAllTokenNodes(ctx.codeBlock(), BSLParser.IDENTIFIER)
      .stream()
      .filter( node -> node.getParent().getClass() == BSLParser.MethodNameContext.class
        && node.getParent().getParent().getClass() != BSLParser.GlobalMethodCallContext.class )
      .filter( node -> node.getText().equalsIgnoreCase("удалить")
        || node.getText().equalsIgnoreCase("delete") )
      .filter( node -> node.getParent().getParent().getParent().getParent().getText().startsWith(
          expression + "." + node.getParent().getText() ) )
      .filter( node -> node.getParent().getParent().getText().contains( iterator ) )
      .collect(Collectors.toList());

    if (!childIdentifiers.isEmpty()) {
      diagnosticStorage.addDiagnostic(ctx, expression);
    }

    return super.visitForEachStatement(ctx);

  }
}
