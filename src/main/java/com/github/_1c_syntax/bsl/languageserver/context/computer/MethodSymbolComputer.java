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

import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodDescriptionSymbol;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.RegionSymbol;
import com.github._1c_syntax.bsl.languageserver.utils.RangeHelper;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserBaseVisitor;

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

    TerminalNode startNode = declaration.FUNCTION_KEYWORD();
    TerminalNode stopNode = ctx.ENDFUNCTION_KEYWORD();

    if (startNode == null || stopNode == null) {
      return ctx;
    }

    MethodSymbol methodSymbol = MethodSymbol.builder()
      .name(declaration.subName().getText())
      .export(declaration.EXPORT_KEYWORD() != null)
      .function(true)
      .node(ctx)
      .region(findRegion(startNode, stopNode))
      .range(RangeHelper.newRange(startNode, stopNode))
      .subNameRange(RangeHelper.newRange(declaration.subName()))
      .description(findMethodDescription(ctx.getStart()))
      .build();

    methods.add(methodSymbol);

    return ctx;
  }

  @Override
  public ParseTree visitProcedure(BSLParser.ProcedureContext ctx) {
    BSLParser.ProcDeclarationContext declaration = ctx.procDeclaration();

    TerminalNode startNode = declaration.PROCEDURE_KEYWORD();
    TerminalNode stopNode = ctx.ENDPROCEDURE_KEYWORD();

    if (startNode == null || stopNode == null) {
      return ctx;
    }

    MethodSymbol methodSymbol = MethodSymbol.builder()
      .name(declaration.subName().getText())
      .export(declaration.EXPORT_KEYWORD() != null)
      .function(false)
      .node(ctx)
      .region(findRegion(startNode, stopNode))
      .range(RangeHelper.newRange(startNode, stopNode))
      .subNameRange(RangeHelper.newRange(declaration.subName()))
      .description(findMethodDescription(ctx.getStart()))
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

  private MethodDescriptionSymbol findMethodDescription(Token start) {
    if(start == null) {
      return null;
    }

    List<Token> comments = getMethodComments(start, null);
    if(comments.size() == 0) {
      return null;
    }

    return new MethodDescriptionSymbol(comments);
  }

  private List<Token> getMethodComments(Token start, List<Token> lines) {
    int index = start.getTokenIndex();

    if(index == 0) {
      return lines;
    }

    Token token = documentContext.getTokens().get(index - 1);

    if(lines == null) {
      lines = new ArrayList<>();
    }

    if(abortSearch(token)){
      return lines;
    }

    lines = getMethodComments(token, lines);
    int type = token.getType();
    if(type == BSLParser.LINE_COMMENT) {
      lines.add(token);
    }
    return lines;
  }

  private boolean abortSearch(Token token) {
    int type = token.getType();
    return (type != BSLParser.ANNOTATION_ATCLIENT_SYMBOL
      && type != BSLParser.ANNOTATION_ATSERVERNOCONTEXT_SYMBOL
      && type != BSLParser.ANNOTATION_ATCLIENTATSERVERNOCONTEXT_SYMBOL
      && type != BSLParser.ANNOTATION_ATCLIENTATSERVER_SYMBOL
      && type != BSLParser.ANNOTATION_ATSERVER_SYMBOL
      && type != BSLParser.ANNOTATION_CUSTOM_SYMBOL
      && type != BSLParser.ANNOTATION_UKNOWN
      && type != BSLParser.LINE_COMMENT
      && type != BSLParser.WHITE_SPACE)
        || isBlankLine(token);
  }

  private boolean isBlankLine(Token token) {
    return token.getType() == BSLParser.WHITE_SPACE
      && (token.getTokenIndex() == 0
        || documentContext.getTokens().get(token.getTokenIndex() - 1).getLine() != token.getLine());
  }
}
