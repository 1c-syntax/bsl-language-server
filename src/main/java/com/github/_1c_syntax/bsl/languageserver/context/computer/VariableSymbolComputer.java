/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2021
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Fedkin <nixel2007@gmail.com> and contributors
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
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ModuleSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
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
import org.eclipse.lsp4j.Range;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

public class VariableSymbolComputer extends BSLParserBaseVisitor<ParseTree> implements Computer<List<VariableSymbol>> {

  private final DocumentContext documentContext;
  private final ModuleSymbol module;
  private final List<MethodSymbol> methods;
  private final Set<VariableSymbol> variables = new HashSet<>();
  private final Map<String, String> currentMethodVariables = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
  private final Map<String, String> moduleVariables = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

  private SourceDefinedSymbol currentMethod;

  public VariableSymbolComputer(DocumentContext documentContext, ModuleSymbol module,  List<MethodSymbol> methods) {
    this.documentContext = documentContext;
    this.module = module;
    this.methods = methods;
    this.currentMethod = module;
  }

  @Override
  public List<VariableSymbol> compute() {
    variables.clear();
    moduleVariables.clear();
    visitFile(documentContext.getAst());
    return new ArrayList<>(variables);
  }

  @Override
  public ParseTree visitModuleVarDeclaration(BSLParser.ModuleVarDeclarationContext ctx) {
    var symbol = VariableSymbol.builder()
      .name(ctx.var_name().getText())
      .owner(documentContext)
      .range(Ranges.create(ctx))
      .variableNameRange(Ranges.create(ctx.var_name()))
      .export(ctx.EXPORT_KEYWORD() != null)
      .kind(VariableKind.MODULE)
      .description(createDescription(ctx))
      .scope(module)
      .build();
    variables.add(symbol);
    moduleVariables.put(ctx.var_name().getText(), ctx.var_name().getText());
    return ctx;
  }

  @Override
  public ParseTree visitSub(BSLParser.SubContext ctx) {
    this.currentMethod = getVariableScope(ctx);
    ParseTree tree = super.visitSub(ctx);
    currentMethodVariables.clear();
    this.currentMethod = module;
    return tree;
  }

  @Override
  public ParseTree visitSubVarDeclaration(BSLParser.SubVarDeclarationContext ctx) {
    var symbol = VariableSymbol.builder()
      .name(ctx.var_name().getText())
      .owner(documentContext)
      .range(Ranges.create(ctx))
      .variableNameRange(Ranges.create(ctx.var_name()))
      .export(false)
      .kind(VariableKind.LOCAL)
      .description(createDescription(ctx))
      .scope(getVariableScope(ctx))
      .build();
    variables.add(symbol);
    currentMethodVariables.put(ctx.var_name().getText(), ctx.var_name().getText());
    return ctx;
  }

  @Override
  public ParseTree visitParam(BSLParser.ParamContext ctx) {
    if (ctx.IDENTIFIER() == null) {
      return ctx;
    }

    var variable = VariableSymbol.builder()
      .name(ctx.IDENTIFIER().getText())
      .scope(currentMethod)
      .owner(documentContext)
      .range(Ranges.create(ctx))
      .variableNameRange(Ranges.create(ctx.IDENTIFIER()))
      .export(false)
      .kind(VariableKind.PARAMETER)
      .description(Optional.empty())
      .build();
    variables.add(variable);

    currentMethodVariables.put(ctx.IDENTIFIER().getText(), ctx.IDENTIFIER().getText());
    return ctx;
  }

  @Override
  public ParseTree visitLValue(BSLParser.LValueContext ctx) {
    if (
      ctx.getChildCount() > 1
      || currentMethodVariables.containsKey(ctx.getText())
      || moduleVariables.containsKey(ctx.getText())
    ) {
      return ctx;
    }

    var variable = VariableSymbol.builder()
      .name(ctx.getText())
      .owner(documentContext)
      .range(Ranges.create(ctx))
      .variableNameRange(Ranges.create(ctx))
      .export(false)
      .kind(VariableKind.DYNAMIC)
      .scope(currentMethod)
      .description(createDescription(ctx))
      .build();
    variables.add(variable);

    currentMethodVariables.put(ctx.getText(), ctx.getText());
    return ctx;
  }

  private SourceDefinedSymbol getVariableScope(BSLParser.SubVarDeclarationContext ctx) {
    var sub = (BSLParser.SubContext) Trees.getRootParent(ctx, BSLParser.RULE_sub);
    if (sub == null) {
      return module;
    }

    return getVariableScope(sub);
  }

  private SourceDefinedSymbol getVariableScope(BSLParser.SubContext ctx) {
      BSLParserRuleContext subNameNode;
      if (Trees.nodeContainsErrors(ctx)) {
        return module;
      } else if (ctx.function() != null) {
        subNameNode = ctx.function().funcDeclaration().subName();
      } else {
        subNameNode = ctx.procedure().procDeclaration().subName();
      }

      Range subNameRange = Ranges.create(subNameNode);

      return methods.stream()
        .filter(methodSymbol -> methodSymbol.getSubNameRange().equals(subNameRange))
        .map(methodSymbol -> (SourceDefinedSymbol) methodSymbol)
        .findAny()
        .orElse(module);
  }

  private Optional<VariableDescription> createDescription(BSLParser.LValueContext ctx) {
    var trailingComments = Trees.getTrailingComment(documentContext.getTokens(), ctx.getStop());

    if (trailingComments.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(
      new VariableDescription(Collections.emptyList(), trailingComments)
    );
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