/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.RegionSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserBaseVisitor;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class VariableSymbolComputer extends BSLParserBaseVisitor<ParseTree> implements Computer<List<VariableSymbol>> {

  private final DocumentContext documentContext;
  private List<VariableSymbol> variables = new ArrayList<>();

  public VariableSymbolComputer(DocumentContext documentContext) {
    this.documentContext = documentContext;
  }

  @Override
  public List<VariableSymbol> compute() {
    variables.clear();
    visitFile(documentContext.getAst());
    return new ArrayList<>(variables);
  }

  @Override
  public ParseTree visitModuleVars(BSLParser.ModuleVarsContext ctx) {

    ctx.moduleVar().forEach(moduleVar -> {

      moduleVar.moduleVarsList().moduleVarDeclaration().forEach(declaration -> {

        var startNode = declaration.getStart();
        var stopNode = declaration.getStop();
        var description = ""; // TODO: получить описание

        VariableSymbol variableSymbol = VariableSymbol.builder()
          .name(declaration.var_name().getText())
          .export(declaration.EXPORT_KEYWORD() != null)
          .node(moduleVar)
          .description(description)
          .range(Ranges.create(startNode, stopNode))
          .region(findRegion(startNode, stopNode))
          .build();

        variables.add(variableSymbol);

      });

    });

    return ctx;
  }

  private Optional<RegionSymbol> findRegion(Token start, Token stop) {

    if (start == null || stop == null) {
      return Optional.empty();
    }

    int startLine = start.getLine();
    int endLine = stop.getLine();

    return documentContext.getRegionsFlat().stream()
      .filter(regionSymbol -> regionSymbol.getStartLine() < startLine && regionSymbol.getEndLine() > endLine)
      .max(Comparator.comparingInt(RegionSymbol::getStartLine));

  }

}
