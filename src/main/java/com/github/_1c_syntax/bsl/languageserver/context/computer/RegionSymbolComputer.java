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
package com.github._1c_syntax.bsl.languageserver.context.computer;

import org.antlr.v4.runtime.tree.ParseTree;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.RegionSymbol;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserBaseVisitor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class RegionSymbolComputer
  extends BSLParserBaseVisitor<ParseTree>
  implements Computer<List<RegionSymbol>> {

  private final DocumentContext documentContext;
  private Deque<RegionSymbol.RegionSymbolBuilder> regionStack = new ArrayDeque<>();
  private List<RegionSymbol> regions = new ArrayList<>();

  public RegionSymbolComputer(DocumentContext documentContext) {
    this.documentContext = documentContext;
  }

  @Override
  public List<RegionSymbol> compute() {
    regionStack.clear();
    regions.clear();

    visitFile(documentContext.getAst());

    return new ArrayList<>(regions);
  }

  @Override
  public ParseTree visitRegionStart(BSLParser.RegionStartContext ctx) {

    RegionSymbol.RegionSymbolBuilder builder = RegionSymbol.builder();
    builder.node(ctx);
    builder.startNode(ctx);
    builder.startLine(ctx.getStart().getLine());
    builder.name(ctx.regionName().getText());
    builder.nameNode(ctx.regionName());

    regionStack.push(builder);
    return super.visitRegionStart(ctx);
  }

  @Override
  public ParseTree visitRegionEnd(BSLParser.RegionEndContext ctx) {

    if (regionStack.isEmpty()) {
      return super.visitRegionEnd(ctx);
    }

    RegionSymbol.RegionSymbolBuilder builder = regionStack.pop();
    builder.endNode(ctx);
    builder.endLine(ctx.getStop().getLine());

    RegionSymbol region = builder.build();

    RegionSymbol.RegionSymbolBuilder parent = regionStack.peek();
    if (parent != null) {
      parent.child(region);
    } else {
      regions.add(region);
    }

    return super.visitRegionEnd(ctx);
  }

}
