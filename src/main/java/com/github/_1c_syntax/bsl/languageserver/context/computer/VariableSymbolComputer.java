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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class VariableSymbolComputer extends BSLParserBaseVisitor<ParseTree> implements Computer<List<VariableSymbol>> {

  private final DocumentContext documentContext;
  private final List<VariableSymbol> variables = new ArrayList<>();

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
    var symbol = createVariableSymbol(ctx, ctx.var_name(), ctx.EXPORT_KEYWORD() != null, VariableKind.GLOBAL);
    variables.add(symbol);

    return ctx;
  }

  @Override
  public ParseTree visitSubVarDeclaration(BSLParser.SubVarDeclarationContext ctx) {
    var symbol = createVariableSymbol(ctx, ctx.var_name(), false, VariableKind.LOCAL);
    variables.add(symbol);

    return ctx;
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
    if (comments.isEmpty()) {
      return Optional.empty();
    }

    String commentsText = comments.stream().map(Token::getText).reduce("", String::concat);

    var trailingDescription = Trees.getTrailingComment(tokens, token)
      .map(trailingComment -> VariableDescription.builder()
        .description(trailingComment.getText())
        .range(Ranges.create(trailingComment))
        .build()
      );

    var description = VariableDescription.builder()
      .description(commentsText)
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

}
