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
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.github._1c_syntax.bsl.languageserver.utils.DiagnosticHelper;
import org.github._1c_syntax.bsl.parser.BSLParser;
import java.util.List;

/**
 * @author Leon Chagelishvili <lChagelishvily@gmail.com>
 */
public class IfElseDuplicatedCodeBlockDiagnostic extends AbstractVisitorDiagnostic {

  @Override
  public DiagnosticSeverity getSeverity() {
    return DiagnosticSeverity.Error;
  }

  @Override
  public ParseTree visitIfStatement(BSLParser.IfStatementContext ctx) {
    findDuplicatedCodeBlock(ctx.codeBlock());
    return super.visitIfStatement(ctx);
  }

  private void findDuplicatedCodeBlock(List<BSLParser.CodeBlockContext> codeBlockContexts) {
    for (int i = 0; i < codeBlockContexts.size(); i++) {
      checkCodeBlock(codeBlockContexts, i);
    }
  }

  private void checkCodeBlock(List<BSLParser.CodeBlockContext> codeBlockContexts, int i) {
    BSLParser.CodeBlockContext currentCodeBlock = codeBlockContexts.get(i);
    for (int j = 0; j < codeBlockContexts.size(); j++) {
      if (!currentCodeBlock.equals(codeBlockContexts.get(j))
        && !(currentCodeBlock.children == null && codeBlockContexts.get(j).children == null)
        && DiagnosticHelper.equalNodes(currentCodeBlock, codeBlockContexts.get(j))) {
        addDiagnostic(currentCodeBlock);
        break;
      }
    }
  }

}

