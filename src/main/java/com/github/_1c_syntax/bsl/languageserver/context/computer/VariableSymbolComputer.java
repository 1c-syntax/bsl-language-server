/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2021
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
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableDescription;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserBaseVisitor;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class VariableSymbolComputer extends BSLParserBaseVisitor<ParseTree> implements Computer<List<VariableSymbol>> {

  private final DocumentContext documentContext;
  private final List<VariableSymbol> variables = new ArrayList<>();
  private final List<String> currentMethodParameters = new ArrayList<>();
  private final List<String> currentMethodVariables = new ArrayList<>();

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
  public ParseTree visitModuleVarDeclaration(BSLParser.ModuleVarDeclarationContext ctx) {
    var symbol = createVariableSymbol(ctx, ctx.var_name(), ctx.EXPORT_KEYWORD() != null, VariableKind.MODULE);
    variables.add(symbol);

    return ctx;
  }

  @Override
  public ParseTree visitSubVarDeclaration(BSLParser.SubVarDeclarationContext ctx) {
    var symbol = createVariableSymbol(ctx, ctx.var_name(), false, VariableKind.LOCAL);
    variables.add(symbol);
    currentMethodParameters.add(ctx.var_name().getText());

    return ctx;
  }

  @Override
  public ParseTree visitSub(BSLParser.SubContext ctx) {
    ParseTree tree = super.visitSub(ctx);
    currentMethodVariables.clear();
    currentMethodParameters.clear();
    return tree;
  }

  @Override
  public ParseTree visitParam(BSLParser.ParamContext ctx) {
    if (ctx.IDENTIFIER() == null) {
      return ctx;
    }

    variables.add(createVariableSymbol(ctx, ctx.IDENTIFIER()));
    currentMethodParameters.add(ctx.IDENTIFIER().getText());
    return ctx;
  }

  @Override
  public ParseTree visitLValue(BSLParser.LValueContext ctx) {
    if (
      ctx.getChildCount() > 1
        || currentMethodParameters.contains(ctx.getText())
        || currentMethodVariables.contains(ctx.getText())
        || registeredGlobal(ctx.getText())
    ) {
      return ctx;
    }

    variables.add(createVariableSymbol(ctx));
    currentMethodVariables.add(ctx.getText());
    return ctx;
  }

  private boolean registeredGlobal(String variableName) {
    return variables.stream()
      .filter(v -> v.getKind() == VariableKind.MODULE || v.getKind() == VariableKind.GLOBAL)
      .anyMatch(v -> v.getName().equalsIgnoreCase(variableName));
  }

  private VariableSymbol createVariableSymbol(
    BSLParserRuleContext ctx,
    BSLParser.Var_nameContext varName,
    boolean export,
    VariableKind kind
  ) {
    return VariableSymbol.builder()
      .name(varName.getText())
      .owner(documentContext)
      .range(Ranges.create(ctx))
      .variableNameRange(Ranges.create(varName))
      .export(export)
      .kind(kind)
      .description(createDescription(ctx))
      .build();
  }

  private VariableSymbol createVariableSymbol(BSLParserRuleContext ctx) {
    return VariableSymbol.builder()
      .name(ctx.getText())
      .owner(documentContext)
      .range(Ranges.create(ctx))
      .variableNameRange(Ranges.create(ctx))
      .export(false)
      .kind(VariableKind.DYNAMIC)
      .description(Optional.empty())
      .build();
  }

  private VariableSymbol createVariableSymbol(BSLParserRuleContext ctx,
                                              TerminalNode paramName) {
    return VariableSymbol.builder()
      .name(paramName.getText())
      .owner(documentContext)
      .range(Ranges.create(ctx))
      .variableNameRange(Ranges.create(paramName))
      .export(false)
      .kind(VariableKind.PARAMETER)
      .description(Optional.empty())
      .build();
  }

  private Optional<VariableDescription> createDescription(BSLParserRuleContext ctx) {
    List<Token> tokens = documentContext.getTokens();
    List<Token> comments = new ArrayList<>();

    // поиск комментариев начинается от первого токена - VAR
    var varToken = Trees.getPreviousTokenFromDefaultChannel(tokens,
      ctx.getStart().getTokenIndex(), BSLParser.VAR_KEYWORD);
    varToken.ifPresent(value -> comments.addAll(Trees.getComments(tokens, value)));

    // висячий комментарий смотрим по токену переменной, он должен находится в этой же строке
    Optional<Token> trailingComments = Trees.getTrailingComment(tokens, ctx.getStop());

    if (comments.isEmpty() && trailingComments.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(new VariableDescription(comments, trailingComments));

  }

}