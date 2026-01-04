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
package com.github._1c_syntax.bsl.languageserver.folding;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserBaseVisitor;
import lombok.Getter;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeKind;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Сапплаер областей сворачивания блоков кода: методов, условий, циклов, попыток.
 */
@Component
public class CodeBlockFoldingRangeSupplier implements FoldingRangeSupplier {

  @Override
  public List<FoldingRange> getFoldingRanges(DocumentContext documentContext) {
    var codeBlockVisitor = new CodeBlockVisitor();
    codeBlockVisitor.visitFile(documentContext.getAst());
    return codeBlockVisitor.getRegionRanges();
  }

  private static class CodeBlockVisitor extends BSLParserBaseVisitor<ParseTree> {

    @Getter
    private final List<FoldingRange> regionRanges = new ArrayList<>();

    @Override
    public ParseTree visitProcedure(BSLParser.ProcedureContext ctx) {
      addRegionRange(ctx.procDeclaration().PROCEDURE_KEYWORD(), ctx.ENDPROCEDURE_KEYWORD());
      return super.visitProcedure(ctx);
    }

    @Override
    public ParseTree visitFunction(BSLParser.FunctionContext ctx) {
      addRegionRange(ctx.funcDeclaration().FUNCTION_KEYWORD(), ctx.ENDFUNCTION_KEYWORD());
      return super.visitFunction(ctx);
    }

    @Override
    public ParseTree visitIfStatement(BSLParser.IfStatementContext ctx) {
      addRegionRange(ctx.ifBranch().IF_KEYWORD(), ctx.ENDIF_KEYWORD());
      return super.visitIfStatement(ctx);
    }

    @Override
    public ParseTree visitWhileStatement(BSLParser.WhileStatementContext ctx) {
      addRegionRange(ctx.WHILE_KEYWORD(), ctx.ENDDO_KEYWORD());
      return super.visitWhileStatement(ctx);
    }

    @Override
    public ParseTree visitForStatement(BSLParser.ForStatementContext ctx) {
      addRegionRange(ctx.FOR_KEYWORD(), ctx.ENDDO_KEYWORD());
      return super.visitForStatement(ctx);
    }

    @Override
    public ParseTree visitForEachStatement(BSLParser.ForEachStatementContext ctx) {
      addRegionRange(ctx.FOR_KEYWORD(), ctx.ENDDO_KEYWORD());
      return super.visitForEachStatement(ctx);
    }

    @Override
    public ParseTree visitTryStatement(BSLParser.TryStatementContext ctx) {
      addRegionRange(ctx.TRY_KEYWORD(), ctx.ENDTRY_KEYWORD());
      return super.visitTryStatement(ctx);
    }

    private void addRegionRange(@Nullable TerminalNode start, @Nullable TerminalNode stop) {
      if (start == null || stop == null) {
        return;
      }

      int startLine = start.getSymbol().getLine();
      int stopLine = stop.getSymbol().getLine();

      if (stopLine > startLine) {
        var foldingRange = new FoldingRange(startLine - 1, stopLine - 1);
        foldingRange.setKind(FoldingRangeKind.Region);

        regionRanges.add(foldingRange);
      }
    }
  }

}
