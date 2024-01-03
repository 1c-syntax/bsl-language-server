/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2024
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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.lsp4j.Range;

import java.util.stream.Stream;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 15,
  tags = {
    DiagnosticTag.STANDARD,
    DiagnosticTag.BADPRACTICE
  }
)
public class MissingCodeTryCatchExDiagnostic extends AbstractVisitorDiagnostic {

  private static final boolean DEFAULT_COMMENT_AS_CODE = false;

  @DiagnosticParameter(
    type = Boolean.class,
    defaultValue = "" + DEFAULT_COMMENT_AS_CODE
  )
  private boolean commentAsCode = DEFAULT_COMMENT_AS_CODE;

  @Override
  public ParseTree visitExceptCodeBlock(BSLParser.ExceptCodeBlockContext ctx) {

    if (ctx.codeBlock().getChildCount() != 0) {
      return super.visitExceptCodeBlock(ctx);
    }

    if (commentAsCode) {
      Stream<Token> comments = documentContext.getComments().stream();
      Range rangeTry = Ranges.create(ctx.getParent());
      if (comments.anyMatch(token ->
        Ranges.containsRange(
          rangeTry,
          Ranges.create(token)))) {
        return super.visitExceptCodeBlock(ctx);
      }
    }
    diagnosticStorage.addDiagnostic(((BSLParser.TryStatementContext) ctx.getParent()).EXCEPT_KEYWORD());

    return super.visitExceptCodeBlock(ctx);
  }
}
