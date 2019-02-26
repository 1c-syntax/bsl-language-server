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
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.Tree;
import org.antlr.v4.runtime.tree.Trees;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.github._1c_syntax.bsl.parser.BSLParser;

import java.util.List;
import java.util.stream.Collectors;

public class EmptyCodeBlockDiagnostic extends AbstractVisitorDiagnostic {

  @Override
  public DiagnosticSeverity getSeverity() {
    return DiagnosticSeverity.Warning;
  }

  @Override
  public ParseTree visitCodeBlock(BSLParser.CodeBlockContext ctx) {

    if (ctx.getParent() instanceof BSLParser.FileContext
      || ctx.getParent() instanceof BSLParser.SubCodeBlockContext) {
      return super.visitCodeBlock(ctx);
    }

    if (ctx.getChildCount() > 0) {
      return super.visitCodeBlock(ctx);
    }

    int lineOfstop = ctx.getStop().getLine();

    List<Tree> list = Trees.getChildren(ctx.getParent()).stream()
      .filter(node -> node instanceof TerminalNode)
      .filter(node -> ((TerminalNode) node).getSymbol().getLine() == lineOfstop)
      .collect(Collectors.toList());

    TerminalNode first = (TerminalNode) list.get(0);
    TerminalNode last = (TerminalNode) list.get(list.size() - 1);

    addDiagnostic(
      first.getSymbol().getLine() - 1,
      first.getSymbol().getCharPositionInLine(),
      last.getSymbol().getLine() - 1,
      last.getSymbol().getCharPositionInLine() + last.getText().length()
    );

    return super.visitCodeBlock(ctx);
  }
}
