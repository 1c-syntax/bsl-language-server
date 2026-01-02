/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2026
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

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.configuration.events.LanguageServerConfigurationChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.utils.Keywords;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.BSLLexer;
import com.github._1c_syntax.bsl.types.MultiName;
import org.antlr.v4.runtime.Token;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentRangeFormattingParams;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Провайдер для форматирования исходного кода.
 * <p>
 * Обрабатывает запросы {@code textDocument/formatting} и {@code textDocument/rangeFormatting}.
 *
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_formatting">Document Formatting Request specification</a>
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_rangeFormatting">Document Range Formatting Request specification</a>
 */
@Component
public final class FormatProvider {

  private static final Set<Integer> keywordTypes = keywordsTokenTypes();

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

  private final Map<Integer, MultiName> keywordCanonText;

  private final LanguageServerConfiguration languageServerConfiguration;

  public FormatProvider(LanguageServerConfiguration languageServerConfiguration) {
    this.languageServerConfiguration = languageServerConfiguration;
    keywordCanonText = getKeywordsCanonicalText();
  }

  public List<TextEdit> getFormatting(DocumentFormattingParams params, DocumentContext documentContext) {
    List<Token> tokens = documentContext.getTokens();
    if (tokens.isEmpty()) {
      return Collections.emptyList();
    }
    var firstToken = tokens.get(0);
    var lastToken = tokens.get(tokens.size() - 1);

    var locale = documentContext.getScriptVariantLocale();
    return getTextEdits(
      tokens,
      locale,
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

    return getTextEdits(
      tokens, documentContext.getScriptVariantLocale(), params.getRange(), startCharacter, params.getOptions());
  }

  @EventListener
  public void handleEvent(LanguageServerConfigurationChangedEvent event) {
    putLogicalNotOrKeywords(keywordCanonText);
  }

  private static boolean betweenStartAndStopCharacters(int startCharacter, int endCharacter, int tokenCharacter) {
    return tokenCharacter >= startCharacter
      && tokenCharacter < endCharacter;
  }

  private static boolean inLineRange(int startLine, int endLine, int tokenLine) {
    return tokenLine >= startLine
      && tokenLine < endLine;
  }

  private List<TextEdit> getTextEdits(
    List<Token> tokens,
    Locale languageLocale,
    Range range,
    int startCharacter,
    FormattingOptions options
  ) {

    String newText = getNewText(tokens, languageLocale, range, startCharacter, options);

    if (newText.isEmpty()) {
      return Collections.emptyList();
    }

    var edit = new TextEdit(range, newText);

    return List.of(edit);

  }

  public String getNewText(
    List<Token> tokens,
    Locale languageLocale,
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
          break;
        default:
          // no-op
      }
      if (insideOperator) {
        switch (tokenType) {
          case BSLLexer.THEN_KEYWORD:
          case BSLLexer.DO_KEYWORD:
            insideOperator = false;
            break;
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

      if (needNewLine && tokenType == BSLLexer.DOT && additionalIndentLevel < 0) {
        currentIndentLevel++;
        additionalIndentLevel = currentIndentLevel;
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
      } else if (keywordTypes.contains(tokenType)) {
        addedText = checkAndFormatKeyword(token, languageLocale);
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

  private String checkAndFormatKeyword(Token token, Locale languageLocale) {
    var needFormatKeyword = languageServerConfiguration.getFormattingOptions().isUseKeywordsFormatting();
    if (needFormatKeyword) {
      return keywordCanonText.getOrDefault(token.getType(), MultiName.EMPTY).get(languageLocale.getLanguage());
    }

    return token.getText();
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
      return switch (previousTokenType) {
        case BSLLexer.IDENTIFIER, BSLLexer.ANNOTATION_CUSTOM_SYMBOL, BSLLexer.EXECUTE_KEYWORD, BSLLexer.NEW_KEYWORD,
             BSLLexer.QUESTION, BSLLexer.RAISE_KEYWORD -> false;
        default -> true;
      };
    }

    return switch (type) {
      case BSLLexer.SEMICOLON, BSLLexer.DOT, BSLLexer.COMMA, BSLLexer.RPAREN, BSLLexer.LBRACK, BSLLexer.RBRACK -> false;
      default -> true;
    };
  }

  private static boolean isUnary(int type, int previousTokenType) {
    if (type != BSLLexer.MINUS) {
      return false;
    }
    return switch (previousTokenType) {
      case BSLLexer.PLUS, BSLLexer.MINUS, BSLLexer.MUL, BSLLexer.QUOTIENT, BSLLexer.ASSIGN, BSLLexer.MODULO,
           BSLLexer.LESS, BSLLexer.GREATER, BSLLexer.LBRACK, BSLLexer.LPAREN, BSLLexer.RETURN_KEYWORD,
           BSLLexer.NOT_EQUAL, BSLLexer.COMMA, BSLLexer.LESS_OR_EQUAL, BSLLexer.GREATER_OR_EQUAL -> true;
      default -> false;
    };
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

  private static Set<Integer> keywordsTokenTypes() {
    Set<Integer> result = new HashSet<>();

    result.add(BSLLexer.IF_KEYWORD);
    result.add(BSLLexer.THEN_KEYWORD);
    result.add(BSLLexer.ELSIF_KEYWORD);
    result.add(BSLLexer.ELSE_KEYWORD);
    result.add(BSLLexer.ENDIF_KEYWORD);
    result.add(BSLLexer.FOR_KEYWORD);
    result.add(BSLLexer.EACH_KEYWORD);
    result.add(BSLLexer.IN_KEYWORD);
    result.add(BSLLexer.TO_KEYWORD);
    result.add(BSLLexer.WHILE_KEYWORD);
    result.add(BSLLexer.DO_KEYWORD);
    result.add(BSLLexer.ENDDO_KEYWORD);
    result.add(BSLLexer.PROCEDURE_KEYWORD);
    result.add(BSLLexer.FUNCTION_KEYWORD);
    result.add(BSLLexer.ENDFUNCTION_KEYWORD);
    result.add(BSLLexer.ENDPROCEDURE_KEYWORD);
    result.add(BSLLexer.VAR_KEYWORD);
    result.add(BSLLexer.GOTO_KEYWORD);
    result.add(BSLLexer.RETURN_KEYWORD);
    result.add(BSLLexer.BREAK_KEYWORD);
    result.add(BSLLexer.CONTINUE_KEYWORD);
    result.add(BSLLexer.AND_KEYWORD);
    result.add(BSLLexer.OR_KEYWORD);
    result.add(BSLLexer.NOT_KEYWORD);
    result.add(BSLLexer.TRY_KEYWORD);
    result.add(BSLLexer.EXCEPT_KEYWORD);
    result.add(BSLLexer.RAISE_KEYWORD);
    result.add(BSLLexer.ENDTRY_KEYWORD);
    result.add(BSLLexer.NEW_KEYWORD);
    result.add(BSLLexer.ADDHANDLER_KEYWORD);
    result.add(BSLLexer.REMOVEHANDLER_KEYWORD);
    result.add(BSLLexer.ASYNC_KEYWORD);
    result.add(BSLLexer.AWAIT_KEYWORD);
    result.add(BSLLexer.VAL_KEYWORD);
    result.add(BSLLexer.EXECUTE_KEYWORD);
    result.add(BSLLexer.EXPORT_KEYWORD);

    return result;
  }

  /**
   * @return мэппинг типа токена к паре, где слева английский текст, справа русский
   */
  private Map<Integer, MultiName> getKeywordsCanonicalText() {
    Map<Integer, MultiName> canonWords = new HashMap<>();

    canonWords.put(BSLLexer.IF_KEYWORD, Keywords.IF);
    canonWords.put(BSLLexer.THEN_KEYWORD, Keywords.THEN);
    canonWords.put(BSLLexer.ELSIF_KEYWORD, Keywords.ELSIF);
    canonWords.put(BSLLexer.ELSE_KEYWORD, Keywords.ELSE);
    canonWords.put(BSLLexer.ENDIF_KEYWORD, Keywords.ENDIF);
    canonWords.put(BSLLexer.FOR_KEYWORD, Keywords.FOR);
    canonWords.put(BSLLexer.EACH_KEYWORD, Keywords.EACH);
    canonWords.put(BSLLexer.IN_KEYWORD, Keywords.IN);
    canonWords.put(BSLLexer.TO_KEYWORD, Keywords.TO);
    canonWords.put(BSLLexer.WHILE_KEYWORD, Keywords.WHILE);
    canonWords.put(BSLLexer.DO_KEYWORD, Keywords.DO);
    canonWords.put(BSLLexer.ENDDO_KEYWORD, Keywords.END_DO);
    canonWords.put(BSLLexer.PROCEDURE_KEYWORD, Keywords.PROCEDURE);
    canonWords.put(BSLLexer.FUNCTION_KEYWORD, Keywords.FUNCTION);
    canonWords.put(BSLLexer.ENDFUNCTION_KEYWORD, Keywords.END_FUNCTION);
    canonWords.put(BSLLexer.ENDPROCEDURE_KEYWORD, Keywords.END_PROCEDURE);
    canonWords.put(BSLLexer.VAR_KEYWORD, Keywords.VAR);
    canonWords.put(BSLLexer.GOTO_KEYWORD, Keywords.GOTO);
    canonWords.put(BSLLexer.RETURN_KEYWORD, Keywords.RETURN);
    canonWords.put(BSLLexer.BREAK_KEYWORD, Keywords.BREAK);
    canonWords.put(BSLLexer.CONTINUE_KEYWORD, Keywords.CONTINUE);
    canonWords.put(BSLLexer.AND_KEYWORD, Keywords.AND);
    canonWords.put(BSLLexer.TRY_KEYWORD, Keywords.TRY);
    canonWords.put(BSLLexer.EXCEPT_KEYWORD, Keywords.EXCEPT);
    canonWords.put(BSLLexer.RAISE_KEYWORD, Keywords.RAISE);
    canonWords.put(BSLLexer.ENDTRY_KEYWORD, Keywords.END_TRY);
    canonWords.put(BSLLexer.NEW_KEYWORD, Keywords.NEW);
    canonWords.put(BSLLexer.ADDHANDLER_KEYWORD, Keywords.ADD_HANDLER);
    canonWords.put(BSLLexer.REMOVEHANDLER_KEYWORD, Keywords.REMOVE_HANDLER);
    canonWords.put(BSLLexer.ASYNC_KEYWORD, Keywords.ASYNC);
    canonWords.put(BSLLexer.AWAIT_KEYWORD, Keywords.AWAIT);
    canonWords.put(BSLLexer.VAL_KEYWORD, Keywords.VAL);
    canonWords.put(BSLLexer.EXECUTE_KEYWORD, Keywords.EXECUTE);
    canonWords.put(BSLLexer.EXPORT_KEYWORD, Keywords.EXPORT);

    putLogicalNotOrKeywords(canonWords);

    return canonWords;
  }

  private void putLogicalNotOrKeywords(Map<Integer, MultiName> canonWords) {
    var useUppercaseForLogicalOrNotAndKeywords = languageServerConfiguration
      .getFormattingOptions()
      .isUseUpperCaseForOrNotAndKeywords();

    MultiName orKeywordCanonText;
    MultiName notKeywordCanonText;
    MultiName andKeywordCanonText;

    if (useUppercaseForLogicalOrNotAndKeywords) {
      orKeywordCanonText = Keywords.OR_UP;
      notKeywordCanonText = Keywords.NOT_UP;
      andKeywordCanonText = Keywords.AND_UP;
    } else {
      orKeywordCanonText = Keywords.OR;
      notKeywordCanonText = Keywords.NOT;
      andKeywordCanonText = Keywords.AND;
    }

    canonWords.put(BSLLexer.OR_KEYWORD, orKeywordCanonText);
    canonWords.put(BSLLexer.NOT_KEYWORD, notKeywordCanonText);
    canonWords.put(BSLLexer.AND_KEYWORD, andKeywordCanonText);
  }
}

