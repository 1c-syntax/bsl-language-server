/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com>
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
package org.github._1c_syntax.bsl.languageserver.providers;

import org.antlr.v4.runtime.Token;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentRangeFormattingParams;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import org.github._1c_syntax.bsl.languageserver.utils.RangeHelper;
import org.github._1c_syntax.bsl.parser.BSLLexer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class FormatProvider {

  private static final Set<Integer> incrementIndentTokens = new HashSet<>(Arrays.asList(
    BSLLexer.LPAREN,
    BSLLexer.PROCEDURE_KEYWORD,
    BSLLexer.FUNCTION_KEYWORD,
    BSLLexer.IF_KEYWORD,
    BSLLexer.ELSIF_KEYWORD,
    BSLLexer.ELSE_KEYWORD,
    BSLLexer.FOR_KEYWORD,
    BSLLexer.WHILE_KEYWORD,
    BSLLexer.TRY_KEYWORD,
    BSLLexer.EXCEPT_KEYWORD
  ));

  private static final Set<Integer> decrementIndentTokens = new HashSet<>(Arrays.asList(
    BSLLexer.RPAREN,
    BSLLexer.ELSIF_KEYWORD,
    BSLLexer.ELSE_KEYWORD,
    BSLLexer.ENDPROCEDURE_KEYWORD,
    BSLLexer.ENDFUNCTION_KEYWORD,
    BSLLexer.ENDIF_KEYWORD,
    BSLLexer.ENDDO_KEYWORD,
    BSLLexer.ENDTRY_KEYWORD
  ));

  private FormatProvider() {
    // only statics
  }

  public static List<TextEdit> getFormatting(DocumentFormattingParams params, DocumentContext documentContext) {
    return getTextEdits(
      filtredTokens(documentContext.getTokens()),
      RangeHelper.newRange(documentContext.getAst()), 0, params.getOptions()
    );
  }

  public static List<TextEdit> getRangeFormatting(
    DocumentRangeFormattingParams params,
    DocumentContext documentContext
  ) {
    Position start = params.getRange().getStart();
    Position end = params.getRange().getEnd();
    int startLine = start.getLine() + 1;
    int startCharacter = start.getCharacter();
    int endLine = end.getLine() + 1;
    int endCharacter = end.getCharacter();

    List<Token> tokens = filtredTokens(documentContext.getTokens()).stream()
      .filter((Token token) -> {
        int tokenLine = token.getLine();
        int tokenCharacter = token.getCharPositionInLine();
        return inLineRange(startLine, endLine, tokenLine)
          || (tokenLine == endLine && betweenStartAndStopCharacters(startCharacter, endCharacter, tokenCharacter));
      })
      .collect(Collectors.toList());

    return getTextEdits(tokens, params.getRange(), startCharacter, params.getOptions());
  }

  private static boolean betweenStartAndStopCharacters(int startCharacter, int endCharacter, int tokenCharacter) {
    return tokenCharacter >= startCharacter
      && tokenCharacter < endCharacter;
  }

  private static boolean inLineRange(int startLine, int endLine, int tokenLine) {
    return tokenLine >= startLine
      && tokenLine < endLine;
  }

  private static List<TextEdit> getTextEdits(
    List<Token> tokens,
    Range range,
    int startCharacter,
    FormattingOptions options
  ) {

    if (tokens.isEmpty()) {
      return Collections.emptyList();
    }

    List<TextEdit> edits = new ArrayList<>();

    int tabSize = options.getTabSize();
    boolean insertSpaces = options.isInsertSpaces();

    StringBuilder newTextBuilder = new StringBuilder();

    Token firstToken = tokens.get(0);
    String indentation = insertSpaces ? StringUtils.repeat(' ', tabSize) : "\t";

    int currentIndentLevel = (firstToken.getCharPositionInLine() - startCharacter) / indentation.length();

    int lastLine = firstToken.getLine();
    int previousTokenType = -1;
    for (Token token : tokens) {
      boolean needNewLine = token.getLine() != lastLine;

      if (needDecrementIndent(token.getType())) {
        currentIndentLevel--;
      }

      if (token.equals(firstToken)) {
        newTextBuilder.append(StringUtils.repeat(indentation, currentIndentLevel));
      } else if (needNewLine) {
        String currentIndentation = StringUtils.repeat(indentation, currentIndentLevel);
        newTextBuilder.append(StringUtils.repeat("\n" + currentIndentation, token.getLine() - lastLine));
      } else if (needAddSpace(token.getType(), previousTokenType)) {
        newTextBuilder.append(' ');
      }

      newTextBuilder.append(token.getText());

      if (needIncrementIndent(token.getType())) {
        currentIndentLevel++;
      }
      lastLine = token.getLine();
      previousTokenType = token.getType();
    }

    newTextBuilder.append("\n");

    TextEdit edit = new TextEdit(range, newTextBuilder.toString());
    edits.add(edit);

    return edits;
  }

  private static List<Token> filtredTokens(List<Token> tokens) {
    return tokens.stream()
      .filter(token -> token.getChannel() == Token.DEFAULT_CHANNEL
        || token.getType() == BSLLexer.LINE_COMMENT
        || token.getType() == BSLLexer.PREPROC_LINE_COMMENT)
      .collect(Collectors.toList());
  }

  private static boolean needAddSpace(int type, int previousTokenType) {
    switch (previousTokenType) {
      case BSLLexer.DOT:
      case BSLLexer.HASH:
      case BSLLexer.AMPERSAND:
      case BSLLexer.TILDA:
      case BSLLexer.LBRACK:
        return false;
      case BSLLexer.LPAREN:
        return type == BSLLexer.COMMA;
      case BSLLexer.COMMA:
        return true;
      default:
        // no-op
    }

    switch (type) {
      case BSLLexer.SEMICOLON:
      case BSLLexer.DOT:
      case BSLLexer.COMMA:
      case BSLLexer.LPAREN:
      case BSLLexer.RPAREN:
      case BSLLexer.LBRACK:
      case BSLLexer.RBRACK:
        return false;
      default:
        return true;
    }
  }

  private static boolean needIncrementIndent(int tokenType) {
    return incrementIndentTokens.contains(tokenType);
  }

  private static boolean needDecrementIndent(int tokenType) {
    return decrementIndentTokens.contains(tokenType);
  }

}

