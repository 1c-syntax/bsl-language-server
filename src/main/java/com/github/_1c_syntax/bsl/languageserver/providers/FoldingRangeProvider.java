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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.RegionSymbol;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserBaseVisitor;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeKind;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class FoldingRangeProvider {

  private FoldingRangeProvider() {
    // only statics
  }

  public static List<FoldingRange> getFoldingRange(DocumentContext documentContext) {

    List<FoldingRange> foldingRanges = getCommentRanges(documentContext);

    CodeBlockRangeFinder codeBlockRangeFinder = new CodeBlockRangeFinder();
    codeBlockRangeFinder.visitFile(documentContext.getAst());
    List<FoldingRange> codeBlockRegionRanges = codeBlockRangeFinder.getRegionRanges();

    UseRangeFinder useRangeFinder = new UseRangeFinder();
    useRangeFinder.visitFile(documentContext.getAst());
    List<FoldingRange> useRegionRanges = useRangeFinder.getRegionRanges();

    RegionRangeFinder regionRangeFinder = new RegionRangeFinder(documentContext);
    List<FoldingRange> regionRanges = regionRangeFinder.getRegionRanges();

    PreprocIfRegionRangeFinder preprocIfRegionRangeFinder = new PreprocIfRegionRangeFinder();
    preprocIfRegionRangeFinder.visitFile(documentContext.getAst());
    List<FoldingRange> preprocRegionRanges = preprocIfRegionRangeFinder.getRegionRanges();

    foldingRanges.addAll(codeBlockRegionRanges);
    foldingRanges.addAll(useRegionRanges);
    foldingRanges.addAll(regionRanges);
    foldingRanges.addAll(preprocRegionRanges);

    return foldingRanges;
  }

  private static List<FoldingRange> getCommentRanges(DocumentContext documentContext) {
    List<FoldingRange> foldingRanges = new ArrayList<>();

    int lastRangeStart = -1;
    int previousLine = -1;
    List<Token> comments = documentContext.getComments();
    for (Token token : comments) {
      int tokenLine = token.getLine();

      if (tokenLine != previousLine + 1) {
        if (lastRangeStart != previousLine) {
          FoldingRange foldingRange = new FoldingRange(lastRangeStart - 1, previousLine - 1);
          foldingRange.setKind(FoldingRangeKind.Comment);

          foldingRanges.add(foldingRange);
        }
        // new range
        lastRangeStart = tokenLine;
      }

      previousLine = tokenLine;
    }

    // add last range
    if (lastRangeStart != previousLine) {
      FoldingRange foldingRange = new FoldingRange(lastRangeStart - 1, previousLine - 1);
      foldingRange.setKind(FoldingRangeKind.Comment);

      foldingRanges.add(foldingRange);
    }
    return foldingRanges;
  }

  private static class UseRangeFinder extends BSLParserBaseVisitor<ParseTree> {

    private List<FoldingRange> regionRanges = new ArrayList<>();

    public List<FoldingRange> getRegionRanges() {
      return new ArrayList<>(regionRanges);
    }

    @Override
    public ParseTree visitFile(BSLParser.FileContext ctx) {
      ParseTree[] uses = Trees.findAllRuleNodes(ctx, BSLParser.RULE_use).toArray(new ParseTree[0]);

      if (uses.length <= 1) {
        return ctx;
      }

      int start = ((BSLParser.UseContext) uses[0]).getStart().getLine();
      int stop = ((BSLParser.UseContext) uses[uses.length - 1]).getStop().getLine();

      FoldingRange foldingRange = new FoldingRange(start - 1, stop - 1);
      foldingRange.setKind(FoldingRangeKind.Imports);

      regionRanges.add(foldingRange);

      return ctx;
    }

  }

  private static class CodeBlockRangeFinder extends BSLParserBaseVisitor<ParseTree> {

    private List<FoldingRange> regionRanges = new ArrayList<>();

    public List<FoldingRange> getRegionRanges() {
      return new ArrayList<>(regionRanges);
    }

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

    private void addRegionRange(TerminalNode start, TerminalNode stop) {
      if (start == null || stop == null) {
        return;
      }

      int startLine = start.getSymbol().getLine();
      int stopLine = stop.getSymbol().getLine();

      if (stopLine > startLine) {
        FoldingRange foldingRange = new FoldingRange(startLine - 1, stopLine - 1);
        foldingRange.setKind(FoldingRangeKind.Region);

        regionRanges.add(foldingRange);
      }
    }
  }

  private static class RegionRangeFinder {

    private List<FoldingRange> regionRanges = new ArrayList<>();

    RegionRangeFinder(DocumentContext documentContext) {
      documentContext.getRegions().forEach((RegionSymbol regionSymbol) -> {
        createFoldingRange(regionSymbol);
        regionSymbol.getChildren().forEach(this::createFoldingRange);
      });

    }

    List<FoldingRange> getRegionRanges() {
      return new ArrayList<>(regionRanges);
    }

    private void createFoldingRange(RegionSymbol regionSymbol) {
      BSLParser.RegionStartContext regionStart = regionSymbol.getStartNode();
      BSLParser.RegionEndContext regionEnd = regionSymbol.getEndNode();

      int start = regionStart.getStart().getLine();
      int stop = regionEnd.getStop().getLine();

      FoldingRange foldingRange = new FoldingRange(start - 1, stop - 1);
      foldingRange.setKind(FoldingRangeKind.Region);

      regionRanges.add(foldingRange);
    }

  }

  private static class PreprocIfRegionRangeFinder extends BSLParserBaseVisitor<ParseTree> {

    private Deque<BSLParser.Preproc_ifContext> preprocIfRegionStack = new ArrayDeque<>();
    private List<FoldingRange> regionRanges = new ArrayList<>();

    public List<FoldingRange> getRegionRanges() {
      return new ArrayList<>(regionRanges);
    }

    @Override
    public ParseTree visitPreproc_if(BSLParser.Preproc_ifContext ctx) {
      preprocIfRegionStack.push(ctx);
      return super.visitPreproc_if(ctx);
    }

    @Override
    public ParseTree visitPreproc_endif(BSLParser.Preproc_endifContext ctx) {

      if (preprocIfRegionStack.isEmpty()) {
        return super.visitPreproc_endif(ctx);
      }

      BSLParser.Preproc_ifContext regionStart = preprocIfRegionStack.pop();

      int start = regionStart.getStart().getLine();
      int stop = ctx.getStop().getLine();

      FoldingRange foldingRange = new FoldingRange(start - 1, stop - 1);
      foldingRange.setKind(FoldingRangeKind.Region);

      regionRanges.add(foldingRange);

      return super.visitPreproc_endif(ctx);
    }

  }
}
