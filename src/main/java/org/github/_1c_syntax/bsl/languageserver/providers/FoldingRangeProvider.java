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
package org.github._1c_syntax.bsl.languageserver.providers;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.lsp4j.FoldingRange;
import org.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import org.github._1c_syntax.bsl.parser.BSLParser;
import org.github._1c_syntax.bsl.parser.BSLParserBaseVisitor;
import org.github._1c_syntax.bsl.parser.BSLParserRuleContext;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class FoldingRangeProvider {

  private FoldingRangeProvider() {
    // only statics
  }

  public static List<FoldingRange> getFoldingRange(DocumentContext documentContext) {

    List<FoldingRange> foldingRanges = new ArrayList<>();

    int lastRangeStart = -1;
    int previousLine = -1;
    List<Token> comments = documentContext.getComments();
    for (Token token : comments) {
      int tokenLine = token.getLine();

      if (tokenLine != previousLine + 1) {
        if (lastRangeStart != previousLine) {
          FoldingRange foldingRange = new FoldingRange(lastRangeStart - 1, previousLine - 1);
          foldingRange.setKind("comment");

          foldingRanges.add(foldingRange);
        }
        // new range
        lastRangeStart = tokenLine;
      }

      previousLine = tokenLine;
    }

    CodeBlockRangeFinder codeBlockRangeFinder = new CodeBlockRangeFinder();
    codeBlockRangeFinder.visitFile(documentContext.getAst());

    foldingRanges.addAll(codeBlockRangeFinder.getRegionRanges());

    RegionRangeFinder regionRangeFinder = new RegionRangeFinder();
    regionRangeFinder.visitFile(documentContext.getAst());

    foldingRanges.addAll(regionRangeFinder.getRegionRanges());

    return foldingRanges;
  }

  private static class CodeBlockRangeFinder extends BSLParserBaseVisitor<ParseTree> {

    private List<FoldingRange> regionRanges = new ArrayList<>();

    public List<FoldingRange> getRegionRanges() {
      return regionRanges;
    }

    @Override
    public ParseTree visitSub(BSLParser.SubContext ctx) {
      addRegionRange(ctx);
      return super.visitSub(ctx);
    }

    @Override
    public ParseTree visitIfStatement(BSLParser.IfStatementContext ctx) {
      addRegionRange(ctx);
      return super.visitIfStatement(ctx);
    }

    @Override
    public ParseTree visitWhileStatement(BSLParser.WhileStatementContext ctx) {
      addRegionRange(ctx);
      return super.visitWhileStatement(ctx);
    }

    @Override
    public ParseTree visitForStatement(BSLParser.ForStatementContext ctx) {
      addRegionRange(ctx);
      return super.visitForStatement(ctx);
    }

    @Override
    public ParseTree visitForEachStatement(BSLParser.ForEachStatementContext ctx) {
      addRegionRange(ctx);
      return super.visitForEachStatement(ctx);
    }

    @Override
    public ParseTree visitTryStatement(BSLParser.TryStatementContext ctx) {
      addRegionRange(ctx);
      return super.visitTryStatement(ctx);
    }

    private void addRegionRange(BSLParserRuleContext ctx) {
      int start = ctx.getStart().getLine();
      int stop = ctx.getStop().getLine();

      if (stop > start) {
        FoldingRange foldingRange = new FoldingRange(start - 1, stop - 1);
        foldingRange.setKind("region");

        regionRanges.add(foldingRange);
      }
    }
  }

  private static class RegionRangeFinder extends BSLParserBaseVisitor<ParseTree> {

    private Deque<BSLParser.RegionStartContext> regionStack = new ArrayDeque<>();
    private List<FoldingRange> regionRanges = new ArrayList<>();

    public List<FoldingRange> getRegionRanges() {
      return regionRanges;
    }

    @Override
    public ParseTree visitRegionStart(BSLParser.RegionStartContext ctx) {
      regionStack.push(ctx);
      return super.visitRegionStart(ctx);
    }

    @Override
    public ParseTree visitRegionEnd(BSLParser.RegionEndContext ctx) {

      BSLParser.RegionStartContext regionStart = regionStack.pop();

      int start = regionStart.getStart().getLine();
      int stop = ctx.getStop().getLine();

      FoldingRange foldingRange = new FoldingRange(start - 1, stop - 1);
      foldingRange.setKind("region");

      regionRanges.add(foldingRange);

      return super.visitRegionEnd(ctx);
    }
  }
}
