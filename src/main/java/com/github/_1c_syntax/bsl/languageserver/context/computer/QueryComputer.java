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
import com.github._1c_syntax.bsl.parser.SDBLTokenizer;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.regex.Pattern;

public class QueryComputer extends BSLParserBaseVisitor<ParseTree> implements Computer<List<SDBLTokenizer>> {

  private final DocumentContext documentContext;
  private final List<SDBLTokenizer> queries = new ArrayList<>();

  private static final Pattern QUERIES_ROOT_KEY = CaseInsensitivePattern.compile(
    "select|выбрать|drop|уничтожить");

  private static final int MINIMAL_QUERY_STRING_LENGTH = 8;

  public QueryComputer(DocumentContext documentContext) {
    this.documentContext = documentContext;
  }

  @Override
  public List<SDBLTokenizer> compute() {
    queries.clear();
    visitFile(documentContext.getAst());
    return new ArrayList<>(queries);
  }

  @Override
  public ParseTree visitString(BSLParser.StringContext ctx) {

    // проверка на минимальную длину
    if (ctx.getText().length() < MINIMAL_QUERY_STRING_LENGTH) {
      return ctx;
    }

    int startLine = 0;
    var startEmptyLines = "";
    if (!ctx.getTokens().isEmpty()) {
      startLine = ctx.getTokens().get(0).getLine();
      startEmptyLines = "\n".repeat(startLine - 1);
    }

    boolean isQuery = false;

    int prevTokenLine = -1;
    String partString = "";
    var strings = new StringJoiner("\n");
    for (Token token : ctx.getTokens()) {

      if (token.getLine() != prevTokenLine && prevTokenLine != -1) {
        strings.add(partString);
        partString = "";
      }

      if (token.getLine() == prevTokenLine && prevTokenLine != -1) {
        String newString = getString(startLine, token);
        partString = newString.substring(partString.length());
      } else {
        partString = getString(startLine, token);
      }

      if (!isQuery) {
        isQuery = QUERIES_ROOT_KEY.matcher(partString).find();
      }

      startLine = token.getLine();
      prevTokenLine = token.getLine();
    }

    // последнюю часть
    if (!partString.isBlank()) {
      strings.add(partString);
    }

    if (isQuery) {
      queries.add(new SDBLTokenizer(startEmptyLines + removeDoubleQuotes(strings.toString())));
    }

    return ctx;
  }

  @NotNull
  private static String getString(int startLine, Token token) {
    var string = addEmptyLines(startLine, token) + " ".repeat(token.getCharPositionInLine());
    if (token.getText().startsWith("|")) {
      string += " " + trimLastQuote(token.getText().substring(1));
    } else {
      string += trimLastQuote(token.getText());
    }
    return string;
  }

  private static String trimLastQuote(String subString) {
    var quoteCount = subString.length() - subString.replace("\"", "").length();
    if (quoteCount % 2 == 1) {
      String newString;
      var quotePosition = subString.lastIndexOf("\"");
      newString = subString.substring(0, quotePosition) + " ";
      if (quotePosition + 1 < subString.length()) {
        newString += subString.substring(quotePosition + 1);
      }
      return newString;
    }
    return subString;
  }

  private static String addEmptyLines(int startLine, Token token) {
    if (token.getLine() > startLine + 1) {
      return "\n".repeat(token.getLine() - startLine - 1);
    }
    return "";
  }

  private static String removeDoubleQuotes(String text) {
    var leftQuote = " \"";
    var rightQuote = "\" ";
    var leftQuoteFound = false;
    var quotePosition = text.indexOf("\"\"");
    var textLength = text.length();
    var newText = text;
    while (quotePosition > 0) {
      newText = newText.substring(0, quotePosition) + (leftQuoteFound ? rightQuote : leftQuote);
      if (quotePosition + 2 < textLength) {
        newText += text.substring(quotePosition + 2);
      }
      quotePosition = newText.indexOf("\"\"");
      leftQuoteFound = !leftQuoteFound;
    }
    return newText;
  }
}
