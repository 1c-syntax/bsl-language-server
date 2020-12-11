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

  /**
   * Ключевые слова для поиска потенциально запросных строк
   */
  private static final Pattern QUERIES_ROOT_KEY = CaseInsensitivePattern.compile(
    "(?:[\\s\";]|^)(?:select|выбрать|drop|уничтожить)(?:\\s|$)");

  private static final Pattern NON_QUERIES_START = CaseInsensitivePattern.compile(
    "(?:^\\s*(?:(?:\\|)|(?:\"\")))");

  /**
   * Минимальная строка для анализа
   */
  private static final int MINIMAL_QUERY_STRING_LENGTH = 8;

  /**
   * Поиск сдвоенных кавычек
   */
  private static final Pattern QUOTE_LINE_PATTERN = CaseInsensitivePattern.compile(
    "(?:\"{12}|\"{10}|\"{8}|\"{6}|\"{4}|\"{2})");

  /**
   * Поиск первой кавычки в строке
   */
  private static final Pattern FIRST_QUOTE_PATTERN = CaseInsensitivePattern.compile(
    "^\\s*(\")");

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

    // конкатенация строк в одну
    int prevTokenLine = -1;
    String partString = "";
    var strings = new StringJoiner("\n");
    for (Token token : ctx.getTokens()) {

      // бывает несколько токенов строки в одной строе файла
      // добавляем часть строки только в случае находления ее на другой строке файла
      if (token.getLine() != prevTokenLine && prevTokenLine != -1) {
        strings.add(partString);
        partString = "";
      }

      // если новый токен строки находится на той же строке файла, что и предыдущий, то добавляем его к ней
      if (token.getLine() == prevTokenLine && prevTokenLine != -1) {
        String newString = getString(startLine, token);
        partString = newString.substring(partString.length());
      } else {
        partString = getString(startLine, token);
      }

      // проверяем подстроку на вероятность запроса
      if (!isQuery) {
        isQuery = QUERIES_ROOT_KEY.matcher(partString).find()
          && !NON_QUERIES_START.matcher(partString).find();
      }

      startLine = token.getLine();
      prevTokenLine = token.getLine();
    }

    // последнюю часть тоже необходимо добавить к общему тексту
    if (!partString.isEmpty()) {
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
      string += trimQuotes(token.getText());
    }
    return string;
  }

  private static String trimLastQuote(String text) {
    var quoteCount = text.length() - text.replace("\"", "").length();
    if (quoteCount % 2 == 1) {
      String newString;
      var quotePosition = text.lastIndexOf("\"");
      newString = text.substring(0, quotePosition) + " ";
      if (quotePosition + 1 < text.length()) {
        newString += text.substring(quotePosition + 1);
      }
      return newString;
    }
    return text;
  }

  private static String trimQuotes(String text) {
    var matcher = FIRST_QUOTE_PATTERN.matcher(text);
    if (matcher.find()) {
      var newText = text.substring(0, matcher.start(1)) + " " + text.substring(matcher.end(1));
      return trimLastQuote(newText);
    }

    return text;
  }

  private static String addEmptyLines(int startLine, Token token) {
    if (token.getLine() > startLine + 1) {
      return "\n".repeat(token.getLine() - startLine - 1);
    }
    return "";
  }

  private static String removeDoubleQuotes(String text) {
    var leftQuoteFound = false;
    var matcher = QUOTE_LINE_PATTERN.matcher(text);
    var newText = text;
    var textLength = text.length();
    var strings = new StringJoiner("");
    while (matcher.find()) {
      var quotesLineLength = matcher.group(0).length();
      var emptyString = (" ".repeat(quotesLineLength / 2)).intern();
      strings.add(newText.substring(0, matcher.start()) + (leftQuoteFound ? "" : emptyString)
        + matcher.group(0).substring(0, quotesLineLength / 2) + (leftQuoteFound ? emptyString : ""));

      if (matcher.end() < textLength) {
        newText = newText.substring(matcher.end());
        textLength = newText.length();
      } else {
        newText = "";
        break;
      }

      matcher = QUOTE_LINE_PATTERN.matcher(newText);
      leftQuoteFound = !leftQuoteFound;
    }

    if (!newText.isEmpty()) {
      strings.add(newText);
    }

    return strings.toString();
  }
}
