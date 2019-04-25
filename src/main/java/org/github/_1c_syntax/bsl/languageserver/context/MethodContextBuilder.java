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
package org.github._1c_syntax.bsl.languageserver.context;

import org.antlr.v4.runtime.tree.ParseTree;
import org.github._1c_syntax.bsl.parser.BSLParser;
import org.github._1c_syntax.bsl.parser.BSLParserBaseVisitor;

import java.util.ArrayList;
import java.util.List;

public class MethodContextBuilder extends BSLParserBaseVisitor<ParseTree> {

  private List<MethodContext> methods = new ArrayList<>();

  @Override
  public ParseTree visitFile(BSLParser.FileContext ctx) {
    methods.clear();
    return super.visitFile(ctx);
  }

  @Override
  public ParseTree visitFunction(BSLParser.FunctionContext ctx) {
    MethodContext methodContext = new MethodContext();
    BSLParser.FuncDeclarationContext declaration = ctx.funcDeclaration();

    methodContext.setName(declaration.subName().getText());
    methodContext.setExport(declaration.EXPORT_KEYWORD() != null);
    methodContext.setFunction(true);
    methodContext.setNode(ctx);

    methods.add(methodContext);

    return ctx;
  }

  @Override
  public ParseTree visitProcedure(BSLParser.ProcedureContext ctx) {
    MethodContext methodContext = new MethodContext();
    BSLParser.ProcDeclarationContext declaration = ctx.procDeclaration();

    methodContext.setName(declaration.subName().getText());
    methodContext.setExport(declaration.EXPORT_KEYWORD() != null);
    methodContext.setFunction(false);
    methodContext.setNode(ctx);

    methods.add(methodContext);

    return ctx;
  }

  public List<MethodContext> getMethods() {
    return new ArrayList<>(methods);
  }

}
