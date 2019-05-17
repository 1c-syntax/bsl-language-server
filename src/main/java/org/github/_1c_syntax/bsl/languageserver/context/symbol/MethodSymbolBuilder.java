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

import java.util.ArrayList;
import java.util.List;

public class MethodSymbolBuilder extends BSLParserBaseVisitor<ParseTree> {

  private List<MethodSymbol> methods = new ArrayList<>();

  public MethodSymbolBuilder(BSLParser.FileContext ast) {
    visitFile(ast);
  }

  @Override
  public ParseTree visitFile(BSLParser.FileContext ctx) {
    methods.clear();
    return super.visitFile(ctx);
  }

  @Override
  public ParseTree visitFunction(BSLParser.FunctionContext ctx) {
    MethodSymbol methodSymbol = new MethodSymbol();
    BSLParser.FuncDeclarationContext declaration = ctx.funcDeclaration();

    methodSymbol.setName(declaration.subName().getText());
    methodSymbol.setExport(declaration.EXPORT_KEYWORD() != null);
    methodSymbol.setFunction(true);
    methodSymbol.setNode(ctx);

    methods.add(methodSymbol);

    return ctx;
  }

  @Override
  public ParseTree visitProcedure(BSLParser.ProcedureContext ctx) {
    MethodSymbol methodSymbol = new MethodSymbol();
    BSLParser.ProcDeclarationContext declaration = ctx.procDeclaration();

    methodSymbol.setName(declaration.subName().getText());
    methodSymbol.setExport(declaration.EXPORT_KEYWORD() != null);
    methodSymbol.setFunction(false);
    methodSymbol.setNode(ctx);

    methods.add(methodSymbol);

    return ctx;
  }

  public List<MethodSymbol> getMethods() {
    return new ArrayList<>(methods);
  }

}
