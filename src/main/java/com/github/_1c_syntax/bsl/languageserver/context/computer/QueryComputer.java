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
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserBaseVisitor;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import com.github._1c_syntax.bsl.parser.SDBLTokenizer;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class QueryComputer extends BSLParserBaseVisitor<ParseTree> implements Computer<Map<BSLParserRuleContext, SDBLTokenizer>> {

  private final DocumentContext documentContext;
  private final Map<BSLParserRuleContext, SDBLTokenizer> queries = new HashMap<>();

  private final Pattern QUERIES_ROOT_KEY = CaseInsensitivePattern.compile(
    "select|выбрать|drop|уничтожить");

  public QueryComputer(DocumentContext documentContext) {
    this.documentContext = documentContext;
  }

  @Override
  public Map<BSLParserRuleContext, SDBLTokenizer> compute() {
    queries.clear();
    visitFile(documentContext.getAst());
    return new HashMap<>(queries);
  }

  @Override
  public ParseTree visitString(BSLParser.StringContext ctx) {

    int startLine = 0;
    var text = "";
    if (ctx.getTokens().size() > 0) {
      startLine = ctx.getTokens().get(0).getLine();
      text = StringUtils.repeat('\n', startLine);
    }

    var strings = new ArrayList<>();
    for (Token token : ctx.getTokens()) {
      strings.add(getString(startLine, token));
      startLine = token.getLine();
    }
    text += StringUtils.join(strings, '\n');

    if (QUERIES_ROOT_KEY.matcher(text).find()) {
      // в токенайзер передадим строку без кавычек
      queries.put(ctx, new SDBLTokenizer(text.substring(1, text.length() - 1)));
    }

    return ctx;
  }

  @NotNull
  private String getString(int startLine, Token token) {
    var string = addEmptyLines(startLine, token) + StringUtils.repeat(' ', token.getCharPositionInLine());
    if (token.getText().startsWith("|")) {
      string += " " + token.getText().substring(1);
    } else {
      string += token.getText();
    }
    return string;
  }

  private String addEmptyLines(int startLine, Token token) {
    if (token.getLine() > startLine + 1) {
      return StringUtils.repeat('\n', (token.getLine() - startLine - 1));
    }
    return "";
  }
}
