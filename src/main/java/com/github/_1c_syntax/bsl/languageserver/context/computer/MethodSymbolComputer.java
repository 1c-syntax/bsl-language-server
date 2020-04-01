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

import javax.annotation.Nullable;
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

    MethodSymbol methodSymbol = createMethodSymbol(
      startNode,
      stopNode,
      declaration.subName(),
      declaration.paramList(),
      true,
      declaration.EXPORT_KEYWORD() != null
    );

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

    MethodSymbol methodSymbol = createMethodSymbol(
      startNode,
      stopNode,
      declaration.subName(),
      declaration.paramList(),
      false,
      declaration.EXPORT_KEYWORD() != null
    );

    methods.add(methodSymbol);

    return ctx;
  }

  private MethodSymbol createMethodSymbol(
    TerminalNode startNode,
    TerminalNode stopNode,
    BSLParser.SubNameContext subName,
    BSLParser.ParamListContext paramList,
    boolean function,
    boolean export
  ) {
    return MethodSymbol.builder()
      .name(subName.getText())
      .range(Ranges.create(startNode, stopNode))
      .subNameRange(Ranges.create(subName))
      .function(function)
      .export(export)
      .description(createDescription(startNode.getSymbol()))
      .parameters(createParameters(paramList))
      .build();
  }

  private Optional<MethodDescription> createDescription(Token token) {
    List<Token> comments = Trees.getComments(documentContext.getTokens(), token);
    if (comments.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(new MethodDescription(comments));
  }

  private static List<ParameterDefinition> createParameters(@Nullable BSLParser.ParamListContext paramList) {
    if (paramList == null) {
      return Collections.emptyList();
    }

    return paramList.param().stream()
      .map(param ->
        ParameterDefinition.builder()
          .name(getParameterName(param.IDENTIFIER()))
          .byValue(param.VAL_KEYWORD() != null)
          .optional(param.defaultValue() != null)
          .build()
      ).collect(Collectors.toList());
  }

  private static String getParameterName(TerminalNode identifier) {
    return Optional.ofNullable(identifier)
      .map(ParseTree::getText)
      .orElse("<UNKNOWN_IDENTIFIER>");
  }
}
