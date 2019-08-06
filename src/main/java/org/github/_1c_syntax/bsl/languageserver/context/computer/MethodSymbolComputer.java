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
package org.github._1c_syntax.bsl.languageserver.context.computer;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import org.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import org.github._1c_syntax.bsl.languageserver.context.symbol.RegionSymbol;
import org.github._1c_syntax.bsl.parser.BSLParser;
import org.github._1c_syntax.bsl.parser.BSLParserBaseVisitor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class MethodSymbolComputer
  extends BSLParserBaseVisitor<ParseTree>
  implements Computer<List<MethodSymbol>> {

  private final DocumentContext documentContext;
  private List<MethodSymbol> methods = new ArrayList<>();

  public MethodSymbolComputer(DocumentContext documentContext) {
    this.documentContext = documentContext;
  }

  @Override
  public List<MethodSymbol> compute() {
    methods.clear();
    visitFile(documentContext.getAst());
    return new ArrayList<>(methods);
  }

  @Override
  public ParseTree visitFunction(BSLParser.FunctionContext ctx) {
    BSLParser.FuncDeclarationContext declaration = ctx.funcDeclaration();

    MethodSymbol methodSymbol = MethodSymbol.builder()
      .name(declaration.subName().getText())
      .export(declaration.EXPORT_KEYWORD() != null)
      .function(true)
      .node(ctx)
      .region(findRegion(ctx.funcDeclaration().FUNCTION_KEYWORD(), ctx.ENDFUNCTION_KEYWORD()))
      .build();

    methods.add(methodSymbol);

    return ctx;
  }

  @Override
  public ParseTree visitProcedure(BSLParser.ProcedureContext ctx) {
    BSLParser.ProcDeclarationContext declaration = ctx.procDeclaration();

    MethodSymbol methodSymbol = MethodSymbol.builder()
      .name(declaration.subName().getText())
      .export(declaration.EXPORT_KEYWORD() != null)
      .function(false)
      .node(ctx)
      .region(findRegion(ctx.procDeclaration().PROCEDURE_KEYWORD(), ctx.ENDPROCEDURE_KEYWORD()))
      .build();

    methods.add(methodSymbol);

    return ctx;
  }

  private RegionSymbol findRegion(TerminalNode start, TerminalNode stop) {

    if (start == null || stop == null) {
      return null;
    }

    int startLine = start.getSymbol().getLine();
    int endLine = stop.getSymbol().getLine();

    Optional<RegionSymbol> region = documentContext.getRegionsFlat().stream()
      .filter(regionSymbol -> regionSymbol.getStartLine() < startLine && regionSymbol.getEndLine() > endLine)
      .max(Comparator.comparingInt(RegionSymbol::getStartLine));

    return region.orElse(null);

  }
}
