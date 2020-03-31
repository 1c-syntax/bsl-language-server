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
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodDescription;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserBaseVisitor;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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

    TerminalNode startNode = declaration.FUNCTION_KEYWORD();
    TerminalNode stopNode = ctx.ENDFUNCTION_KEYWORD();

    if (startNode == null
      || startNode instanceof ErrorNode
      || stopNode == null
      || stopNode instanceof ErrorNode
    ) {
      return ctx;
    }

    MethodSymbol.MethodSymbolBuilder builder = MethodSymbol.builder()
      .name(declaration.subName().getText())
      .range(Ranges.create(startNode, stopNode))
      .subNameRange(Ranges.create(declaration.subName()))
      .function(true)
      .export(declaration.EXPORT_KEYWORD() != null)
      .description(createDescription(startNode.getSymbol()));

    List<ParameterDefinition> parameters = Optional.ofNullable(declaration.paramList())
      .map(paramList -> paramList.param().stream()
        .filter(param -> param.IDENTIFIER() != null)
        .map(param ->
          ParameterDefinition.builder()
            .name(param.IDENTIFIER().getText())
            .byValue(param.VAL_KEYWORD() != null)
            .optional(param.defaultValue() != null)
            .build()
        ).collect(Collectors.toList())
      )
      .orElseGet(Collections::emptyList);

    builder.parameters(parameters);

    MethodSymbol methodSymbol = builder.build();

    methods.add(methodSymbol);

    return ctx;
  }

  @Override
  public ParseTree visitProcedure(BSLParser.ProcedureContext ctx) {
    BSLParser.ProcDeclarationContext declaration = ctx.procDeclaration();

    TerminalNode startNode = declaration.PROCEDURE_KEYWORD();
    TerminalNode stopNode = ctx.ENDPROCEDURE_KEYWORD();

    if (startNode == null
      || startNode instanceof ErrorNode
      || stopNode == null
      || stopNode instanceof ErrorNode
    ) {
      return ctx;
    }

    MethodSymbol.MethodSymbolBuilder builder = MethodSymbol.builder()
      .name(declaration.subName().getText())
      .range(Ranges.create(startNode, stopNode))
      .subNameRange(Ranges.create(declaration.subName()))
      .function(false)
      .export(declaration.EXPORT_KEYWORD() != null)
      .description(createDescription(startNode.getSymbol()));

    List<ParameterDefinition> parameters = Optional.ofNullable(declaration.paramList())
      .map(paramList -> paramList.param().stream()
        .filter(param -> param.IDENTIFIER() != null)
        .map(param ->
          ParameterDefinition.builder()
            .name(param
              .IDENTIFIER()
              .getText())
            .byValue(param.VAL_KEYWORD() != null)
            .optional(param.defaultValue() != null)
            .build()
        ).collect(Collectors.toList())
      )
      .orElseGet(Collections::emptyList);

    builder.parameters(parameters);

    MethodSymbol methodSymbol = builder.build();

    methods.add(methodSymbol);

    return ctx;
  }

  private Optional<MethodDescription> createDescription(Token token) {
    List<Token> comments = Trees.getComments(documentContext.getTokens(), token);
    if (comments.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(new MethodDescription(comments));
  }

}
