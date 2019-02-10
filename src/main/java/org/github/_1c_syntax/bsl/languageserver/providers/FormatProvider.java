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
import org.eclipse.lsp4j.*;
import org.github._1c_syntax.bsl.languageserver.utils.RangeHelper;
import org.github._1c_syntax.bsl.parser.BSLLexer;
import org.github._1c_syntax.bsl.parser.BSLParser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class FormatProvider {

  private FormatProvider() {
    // only statics
  }

  public static List<TextEdit> getFormatting(DocumentFormattingParams params, BSLParser.FileContext fileTree) {
    List<Token> tokens = fileTree.getTokens();
    return getTextEdits(tokens, RangeHelper.newRange(fileTree), 0, params.getOptions());
  }

  public static List<TextEdit> getRangeFormatting(DocumentRangeFormattingParams params, BSLParser.FileContext fileTree) {
    Position start = params.getRange().getStart();
    Position end = params.getRange().getEnd();
    int startLine = start.getLine() + 1;
    int startCharacter = start.getCharacter();
    int endLine = end.getLine() + 1;
    int endCharacter = end.getCharacter();

    List<Token> tokens = fileTree.getTokens().stream()
      .filter(token -> {
        int tokenLine = token.getLine();
        int tokenCharacter = token.getCharPositionInLine();
        return (tokenLine >= startLine
          && tokenLine < endLine)
          || (tokenLine == endLine
          && tokenCharacter >= startCharacter
          && tokenCharacter < endCharacter);
      })
      .collect(Collectors.toList());

    return getTextEdits(tokens, params.getRange(), startCharacter, params.getOptions());
  }

  private static List<TextEdit> getTextEdits(
    List<Token> tokens,
    Range range,
    int startCharacter,
    FormattingOptions options
  ) {
    List<TextEdit> edits = new ArrayList<>();

    if (tokens.isEmpty()) {
      return edits;
    }

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
        // TODO: CRLF/LF ?
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
    Set<Integer> incrementsIndent = new HashSet<>();
    incrementsIndent.add(BSLLexer.LPAREN);
    incrementsIndent.add(BSLLexer.PROCEDURE_KEYWORD);
    incrementsIndent.add(BSLLexer.FUNCTION_KEYWORD);
    incrementsIndent.add(BSLLexer.IF_KEYWORD);
    incrementsIndent.add(BSLLexer.ELSIF_KEYWORD);
    incrementsIndent.add(BSLLexer.ELSE_KEYWORD);
    incrementsIndent.add(BSLLexer.FOR_KEYWORD);
    incrementsIndent.add(BSLLexer.WHILE_KEYWORD);
    incrementsIndent.add(BSLLexer.TRY_KEYWORD);
    incrementsIndent.add(BSLLexer.EXCEPT_KEYWORD);

    return incrementsIndent.contains(tokenType);
  }

  private static boolean needDecrementIndent(int tokenType) {
    Set<Integer> decrementsIndent = new HashSet<>();
    decrementsIndent.add(BSLLexer.RPAREN);
    decrementsIndent.add(BSLLexer.ELSIF_KEYWORD);
    decrementsIndent.add(BSLLexer.ELSE_KEYWORD);
    decrementsIndent.add(BSLLexer.ENDPROCEDURE_KEYWORD);
    decrementsIndent.add(BSLLexer.ENDFUNCTION_KEYWORD);
    decrementsIndent.add(BSLLexer.ENDIF_KEYWORD);
    decrementsIndent.add(BSLLexer.ENDDO_KEYWORD);
    decrementsIndent.add(BSLLexer.ENDTRY_KEYWORD);

    return decrementsIndent.contains(tokenType);
  }

}

