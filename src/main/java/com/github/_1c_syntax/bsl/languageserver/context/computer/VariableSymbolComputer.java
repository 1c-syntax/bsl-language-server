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
import com.github._1c_syntax.bsl.languageserver.context.symbol.Usage;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableUsage;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableDescription;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserBaseVisitor;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.lsp4j.Range;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class VariableSymbolComputer extends BSLParserBaseVisitor<ParseTree> implements Computer<List<VariableSymbol>> {

  private final DocumentContext documentContext;
  private final List<VariableSymbol> variables = new ArrayList<>();
  private ArrayList<String> currentMethodParameters = new ArrayList<>();
  private Range currentMethodRange;


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
  public ParseTree visitSub(BSLParser.SubContext ctx) {
    currentMethodRange = Ranges.create(ctx);
    ParseTree tree = super.visitSub(ctx);
    currentMethodRange = null;
    currentMethodParameters.clear();
    return tree;
  }

  @Override
  public ParseTree visitParam(BSLParser.ParamContext ctx) {
    currentMethodParameters.add(ctx.getText());
    return ctx;
  }

  @Override
  public ParseTree visitSubVarDeclaration(BSLParser.SubVarDeclarationContext ctx) {
    var symbol = createVariableSymbol(ctx, ctx.var_name(), false, VariableKind.LOCAL);
    variables.add(symbol);

    return ctx;
  }

  @Override
  public ParseTree visitLValue(BSLParser.LValueContext ctx) {
    if (ctx.getChildCount() > 1) {
      return ctx;
    }

    if (notParameter(ctx.getText()) && notRegistered(ctx.getText())) {
      VariableSymbol symbol = VariableSymbol.builder()
        .name(ctx.getText())
        .range(Ranges.create(ctx))
        .variableNameRange(Ranges.create(ctx))
        .export(false)
        .kind(VariableKind.LOCAL)
        .description(createDescription(getTokenToSearchComments(ctx)))
        .build();
      variables.add(symbol);
    }
    return ctx;
  }

  @Override
  public ParseTree visitCallStatement(BSLParser.CallStatementContext ctx) {
    if(ctx.getStart().getType() == BSLParser.IDENTIFIER) {
      findVariableSymbol(ctx.getStart().getText()).ifPresent(symbol -> {
        Usage usage  = VariableUsage.builder()
          .range(Ranges.create(ctx.getStart()))
          .kind(Usage.Kind.OBJECT)
          .build();
        symbol.addUsage(usage);
      });
    }
    return super.visitCallStatement(ctx);
  }

  @Override
  public ParseTree visitComplexIdentifier(BSLParser.ComplexIdentifierContext ctx) {
    if (ctx.getStart().getType() == BSLParser.IDENTIFIER) {
      findVariableSymbol(ctx.getStart().getText()).ifPresent(symbol -> {
        Usage usage  = VariableUsage.builder()
          .range(Ranges.create(ctx.getStart()))
          .kind(Usage.Kind.OTHER)
          .build();
        symbol.addUsage(usage);
      });
    }

    return super.visitComplexIdentifier(ctx);
  }

  private VariableSymbol createVariableSymbol(
    BSLParserRuleContext ctx,
    BSLParser.Var_nameContext varName,
    boolean export,
    VariableKind kind
  ) {
    return VariableSymbol.builder()
      .name(varName.getText())
      .range(Ranges.create(ctx))
      .variableNameRange(Ranges.create(varName))
      .export(export)
      .kind(kind)
      .description(createDescription(getTokenToSearchComments(ctx)))
      .build();
  }

  private Optional<VariableDescription> createDescription(Token token) {
    List<Token> tokens = documentContext.getTokens();
    List<Token> comments = Trees.getComments(tokens, token);
    Optional<Token> trailingComments = Trees.getTrailingComment(tokens, token);

    if (comments.isEmpty() && trailingComments.isEmpty()) {
      return Optional.empty();
    }

    String commentsText = comments.stream().map(Token::getText).reduce("", String::concat);

    var trailingDescription = trailingComments
      .map(trailingComment -> VariableDescription.builder()
        .description(trailingComment.getText())
        .range(Ranges.create(trailingComment))
        .build()
      );

    var description =
    VariableDescription.builder()
      .description(commentsText)
      .range(getRangeForDescription(comments))
      .trailingDescription(trailingDescription)
      .build();

    return Optional.of(description);
  }

  private static Token getTokenToSearchComments(BSLParserRuleContext declaration) {
    var parent = Trees.getAncestorByRuleIndex(declaration, BSLParser.RULE_moduleVar);
    if (parent == null) {
      return declaration.getStart();
    }
    return parent.getStart();
  }

  private static Range getRangeForDescription(List<Token> tokens) {

    if (tokens.isEmpty()) {
      return null;
    }

    Token firstElement = tokens.get(0);
    Token lastElement = tokens.get(tokens.size() - 1);

    return Ranges.create(firstElement, lastElement);
  }

  private boolean notParameter(String variableName) {
    return !currentMethodParameters.contains(variableName);
  }

  private boolean notRegistered(String variableName) {
    return variables.stream()
      .filter(v -> v.getKind() == VariableKind.MODULE
        || currentMethodRange == null
        || Ranges.containsRange(currentMethodRange, v.getRange()))
      .noneMatch(v -> v.getName().equals(variableName));
  }

  private Optional<VariableSymbol> findVariableSymbol(String variableName) {
    return variables.stream()
      .filter(v -> v.getKind() == VariableKind.MODULE
        || currentMethodRange == null
        || Ranges.containsRange(currentMethodRange, v.getRange()))
      .filter(v -> v.getName().equals(variableName))
      .findFirst();
  }

}
