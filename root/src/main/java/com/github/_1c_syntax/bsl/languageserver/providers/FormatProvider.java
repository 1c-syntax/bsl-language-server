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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.BSLLexer;
import org.antlr.v4.runtime.Token;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentRangeFormattingParams;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
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
    BSLLexer.EXCEPT_KEYWORD,
    BSLLexer.ENDTRY_KEYWORD
  ));

  private static final Set<Integer> primitiveTokenTypes = new HashSet<>(Arrays.asList(
    BSLLexer.NULL,
    BSLLexer.DATETIME,
    BSLLexer.DECIMAL,
    BSLLexer.TRUE,
    BSLLexer.FALSE,
    BSLLexer.UNDEFINED,
    BSLLexer.FLOAT,
    BSLLexer.STRING
  ));

  public List<TextEdit> getFormatting(DocumentFormattingParams params, DocumentContext documentContext) {
    List<Token> tokens = documentContext.getTokens();
    if (tokens.isEmpty()) {
      return Collections.emptyList();
    }
    Token firstToken = tokens.get(0);
    Token lastToken = tokens.get(tokens.size() - 1);

    return getTextEdits(
      tokens,
      Ranges.create(firstToken, lastToken), firstToken.getCharPositionInLine(), params.getOptions()
    );
  }

  public List<TextEdit> getRangeFormatting(
    DocumentRangeFormattingParams params,
    DocumentContext documentContext
  ) {
    Position start = params.getRange().getStart();
    Position end = params.getRange().getEnd();
    int startLine = start.getLine() + 1;
    int startCharacter = start.getCharacter();
    int endLine = end.getLine() + 1;
    int endCharacter = end.getCharacter();

    List<Token> tokens = documentContext.getTokens().stream()
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

    String newText = getNewText(tokens, range, startCharacter, options);

    if (newText.isEmpty()) {
      return Collections.emptyList();
    }

    var edit = new TextEdit(range, newText);

    return List.of(edit);

  }

  public static String getNewText(
    List<Token> tokens,
    Range range,
    int startCharacter,
    FormattingOptions options
  ) {

    if (tokens.isEmpty()) {
      return "";
    }

    List<Token> filteredTokens = filteredTokens(tokens);
    if (filteredTokens.isEmpty()) {
      return "";
    }

    int tabSize = options.getTabSize();
    boolean insertSpaces = options.isInsertSpaces();

    var newTextBuilder = new StringBuilder();

    var firstToken = filteredTokens.get(0);
    String indentation = insertSpaces ? StringUtils.repeat(' ', tabSize) : "\t";

    int currentIndentLevel = (firstToken.getCharPositionInLine() - startCharacter) / indentation.length();
    int additionalIndentLevel = -1;
    var inMethodDefinition = false;
    var insideOperator = false;
    var parameterDeclarationMode = false;

    int lastLine = firstToken.getLine();
    int previousTokenType = -1;
    var previousIsUnary = false;

    for (Token token : filteredTokens) {
      int tokenType = token.getType();

      boolean needNewLine = token.getLine() != lastLine;

      if (tokenType == BSLLexer.FUNCTION_KEYWORD || tokenType == BSLLexer.PROCEDURE_KEYWORD) {
        inMethodDefinition = true;
      }
      if (inMethodDefinition && tokenType == BSLLexer.RPAREN) {
        inMethodDefinition = false;
      }
      switch (tokenType) {
        case BSLLexer.IF_KEYWORD:
        case BSLLexer.ELSIF_KEYWORD:
        case BSLLexer.WHILE_KEYWORD:
        case BSLLexer.FOR_KEYWORD:
          insideOperator = true;
        default:
          // no-op
      }
      if (insideOperator) {
        switch (tokenType) {
          case BSLLexer.THEN_KEYWORD:
          case BSLLexer.DO_KEYWORD:
            insideOperator = false;
          default:
            // no-op
        }
      }

      if (previousTokenType == BSLLexer.ANNOTATION_CUSTOM_SYMBOL && tokenType == BSLLexer.LPAREN) {
        parameterDeclarationMode = true;
      }

      // Add indentation before token lines
      if (needNewLine) {
        var currentIndentation = StringUtils.repeat(indentation, currentIndentLevel);
        newTextBuilder.append(StringUtils.repeat("\n" + currentIndentation, token.getLine() - lastLine - 1));
      }

      // Decrement indent on operators ends and right paren.
      if (needDecrementIndent(tokenType)) {
        currentIndentLevel--;

        // additional decrement if additional indent was added after `=` sign.
        // on all operators except right paren.
        if (tokenType != BSLLexer.RPAREN && currentIndentLevel == additionalIndentLevel) {
          currentIndentLevel--;
          additionalIndentLevel = -1;
        }
      }

      // Add indentation on token line
      if (token.equals(firstToken)) {
        newTextBuilder.append(StringUtils.repeat(indentation, currentIndentLevel));
      } else if (needNewLine) {
        var currentIndentation = StringUtils.repeat(indentation, currentIndentLevel);
        newTextBuilder.append("\n");
        newTextBuilder.append(currentIndentation);
      } else if (needAddSpace(tokenType, previousTokenType, previousIsUnary)) {
        newTextBuilder.append(' ');
      } else {
        // no-op
      }

      String addedText = token.getText();
      if (tokenType == BSLLexer.LINE_COMMENT) {
        addedText = addedText.trim();
      }
      newTextBuilder.append(addedText);

      // Increment on operator starts and left paren
      if (needIncrementIndent(tokenType)) {
        currentIndentLevel++;
      }

      // Add additional indent after first `=` sign in operator
      if (tokenType == BSLLexer.ASSIGN && additionalIndentLevel < 0 && !inMethodDefinition && !insideOperator) {
        currentIndentLevel++;
        additionalIndentLevel = currentIndentLevel;
      }
      // Remove additional indent after semicolon or parameter default value.
      if (additionalIndentLevel > 0
        && (tokenType == BSLLexer.SEMICOLON || (parameterDeclarationMode && isPrimitive(tokenType)))) {
        currentIndentLevel--;
        additionalIndentLevel = -1;
      }

      if (parameterDeclarationMode && tokenType == BSLLexer.RPAREN) {
        parameterDeclarationMode = false;
      }

      lastLine = token.getLine();
      previousIsUnary = isUnary(tokenType, previousTokenType);
      previousTokenType = tokenType;
    }

    var lastToken = tokens.get(tokens.size() - 1);
    if (lastToken.getText().endsWith("\n") || lastToken.getText().endsWith("\r")) {
      newTextBuilder.append("\n");

      if (range.getEnd().getCharacter() != 0) {
        var currentIndentation = StringUtils.repeat(indentation, currentIndentLevel);
        newTextBuilder.append(currentIndentation);
      }
    }

    return newTextBuilder.toString();
  }

  private static List<Token> filteredTokens(List<Token> tokens) {
    return tokens.stream()
      .filter(token -> token.getChannel() == Token.DEFAULT_CHANNEL
        || token.getType() == BSLLexer.LINE_COMMENT)
      .collect(Collectors.toList());
  }

  private static boolean needAddSpace(int type, int previousTokenType, boolean previousIsUnary) {

    if (previousIsUnary) {
      return false;
    }

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
      case BSLLexer.GREATER_OR_EQUAL:
      case BSLLexer.LESS_OR_EQUAL:
      case BSLLexer.NOT_EQUAL:
      case BSLLexer.ASSIGN:
        return true;
      default:
        // no-op
    }

    if (type == BSLLexer.LPAREN) {
      switch (previousTokenType) {
        case BSLLexer.IDENTIFIER:
        case BSLLexer.ANNOTATION_CUSTOM_SYMBOL:
        case BSLLexer.EXECUTE_KEYWORD:
        case BSLLexer.NEW_KEYWORD:
        case BSLLexer.QUESTION:
        case BSLLexer.RAISE_KEYWORD:
          return false;
        default:
          return true;
      }
    }

    switch (type) {
      case BSLLexer.SEMICOLON:
      case BSLLexer.DOT:
      case BSLLexer.COMMA:
      case BSLLexer.RPAREN:
      case BSLLexer.LBRACK:
      case BSLLexer.RBRACK:
        return false;
      default:
        return true;
    }
  }

  private static boolean isUnary(int type, int previousTokenType) {
    if (type != BSLLexer.MINUS) {
      return false;
    }
    switch (previousTokenType) {
      case BSLLexer.PLUS:
      case BSLLexer.MINUS:
      case BSLLexer.MUL:
      case BSLLexer.QUOTIENT:
      case BSLLexer.ASSIGN:
      case BSLLexer.MODULO:
      case BSLLexer.LESS:
      case BSLLexer.GREATER:
      case BSLLexer.LBRACK:
      case BSLLexer.LPAREN:
      case BSLLexer.RETURN_KEYWORD:
      case BSLLexer.NOT_EQUAL:
      case BSLLexer.COMMA:
      case BSLLexer.LESS_OR_EQUAL:
      case BSLLexer.GREATER_OR_EQUAL:
        return true;
      default:
        return false;
    }
  }

  private static boolean needIncrementIndent(int tokenType) {
    return incrementIndentTokens.contains(tokenType);
  }

  private static boolean needDecrementIndent(int tokenType) {
    return decrementIndentTokens.contains(tokenType);
  }

  private static boolean isPrimitive(int tokenType) {
    return primitiveTokenTypes.contains(tokenType);
  }

}

