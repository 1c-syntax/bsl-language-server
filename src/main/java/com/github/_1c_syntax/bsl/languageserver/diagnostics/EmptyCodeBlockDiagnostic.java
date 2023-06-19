/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2023
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
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.Tree;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 5,
  tags = {
    DiagnosticTag.BADPRACTICE,
    DiagnosticTag.SUSPICIOUS
  }
)
public class EmptyCodeBlockDiagnostic extends AbstractVisitorDiagnostic {

  private static final boolean DEFAULT_COMMENT_AS_CODE = false;

  @DiagnosticParameter(
    type = Boolean.class,
    defaultValue = "" + DEFAULT_COMMENT_AS_CODE
  )
  private boolean commentAsCode = DEFAULT_COMMENT_AS_CODE;

  @Override
  public ParseTree visitCodeBlock(BSLParser.CodeBlockContext ctx) {

    boolean isFileBlock = ctx.getParent() instanceof BSLParser.FileContext
      || ctx.getParent() instanceof BSLParser.FileCodeBlockBeforeSubContext
      || ctx.getParent() instanceof BSLParser.FileCodeBlockContext;

    if (ctx.getChildCount() > 0
      || isFileBlock
      || ctx.getParent() instanceof BSLParser.SubCodeBlockContext
      || ctx.getParent() instanceof BSLParser.ExceptCodeBlockContext) {
      return super.visitCodeBlock(ctx);
    }

    if (commentAsCode) {
      Stream<Token> comments = documentContext.getComments().stream();
      var rangeCodeBlock = Ranges.create(ctx.getStop(), ctx.getStart());
      if (comments.anyMatch(token ->
        Ranges.containsRange(
          rangeCodeBlock,
          Ranges.create(token)))) {
        return super.visitCodeBlock(ctx);
      }
    }

    int lineOfStop = ctx.getStop().getLine();

    List<Tree> list = Trees.getChildren(ctx.getParent()).stream()
      .filter(TerminalNode.class::isInstance)
      .filter(node -> ((TerminalNode) node).getSymbol().getLine() == lineOfStop)
      .collect(Collectors.toList());

    if (!list.isEmpty()) {
      TerminalNode first = (TerminalNode) list.get(0);
      TerminalNode last = (TerminalNode) list.get(list.size() - 1);

      diagnosticStorage.addDiagnostic(first, last);
    } else {
      diagnosticStorage.addDiagnostic(ctx.getParent().getStop());
    }

    return super.visitCodeBlock(ctx);
  }
}
