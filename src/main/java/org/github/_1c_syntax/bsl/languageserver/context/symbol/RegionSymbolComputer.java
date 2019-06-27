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
package org.github._1c_syntax.bsl.languageserver.context.symbol;

import org.antlr.v4.runtime.tree.ParseTree;
import org.github._1c_syntax.bsl.parser.BSLParser;
import org.github._1c_syntax.bsl.parser.BSLParserBaseVisitor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class RegionSymbolComputer extends BSLParserBaseVisitor<ParseTree> {

  private Deque<RegionSymbol.RegionSymbolBuilder> regionStack = new ArrayDeque<>();
  private List<RegionSymbol> regions = new ArrayList<>();

  public RegionSymbolComputer(BSLParser.FileContext ast) {
    visitFile(ast);
  }

  public List<RegionSymbol> getRegions() {
    return new ArrayList<>(regions);
  }

  @Override
  public ParseTree visitRegionStart(BSLParser.RegionStartContext ctx) {

    RegionSymbol.RegionSymbolBuilder builder = new RegionSymbol.RegionSymbolBuilder();
    builder.node(ctx);
    builder.start(ctx);

    regionStack.push(builder);
    return super.visitRegionStart(ctx);
  }

  @Override
  public ParseTree visitRegionEnd(BSLParser.RegionEndContext ctx) {

    if (regionStack.isEmpty()) {
      return super.visitRegionEnd(ctx);
    }

    RegionSymbol.RegionSymbolBuilder builder = regionStack.pop();
    builder.end(ctx);

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
