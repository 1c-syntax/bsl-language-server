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
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.tree.ParseTree;


@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.BLOCKER,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.SUSPICIOUS,
    DiagnosticTag.BRAINOVERLOAD
  }
)
public class UnaryPlusInConcatenationDiagnostic extends AbstractVisitorDiagnostic {

  public UnaryPlusInConcatenationDiagnostic(DiagnosticInfo info) {
    super(info);
  }

  @Override
  public ParseTree visitMember(BSLParser.MemberContext ctx) {
    ParseTree childZero = ctx.getChild(0);
    if (childZero == null) {
      return super.visitMember(ctx);
    }
    ParseTree previousNode = Trees.getPreviousNode(ctx.parent, childZero, BSLParser.RULE_operation);
    if (
      (childZero instanceof BSLParser.UnaryModifierContext)
        && ctx.getChildCount() > 1
        && !(ctx.getChild(1).getChild(0) instanceof BSLParser.NumericContext)
        && "+".equals(childZero.getText())
        && !previousNode.equals(childZero)
        && "+".equals(previousNode.getText())
    ) {
      diagnosticStorage.addDiagnostic(((BSLParser.UnaryModifierContext) childZero).start);
    }

    return super.visitMember(ctx);
  }

}
