/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeKind;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Сапплаер областей сворачивания инструкций препроцессору <code>#Если ... #КонецЕсли</code>.
 */
@Component
public class PreprocIfFoldingRangeSupplier implements FoldingRangeSupplier {

  @Override
  public List<FoldingRange> getFoldingRanges(DocumentContext documentContext) {
    PreprocIfVisitor preprocIfVisitor = new PreprocIfVisitor();
    preprocIfVisitor.visitFile(documentContext.getAst());
    return preprocIfVisitor.getRegionRanges();
  }

  private static class PreprocIfVisitor extends BSLParserBaseVisitor<ParseTree> {

    @Getter
    private final List<FoldingRange> regionRanges = new ArrayList<>();
    private final Deque<BSLParser.Preproc_ifContext> preprocIfRegionStack = new ArrayDeque<>();

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
