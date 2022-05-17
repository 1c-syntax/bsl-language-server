/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2022
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
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentRangeFormattingParams;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
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

@Component
public final class FormatProvider {

  private static final Set<Integer> keywordTypes = keywordsTokenTypes();
  private static final Map<Integer, Pair<String, String>> keywordCanonText = getKeywordsCanonicalText();
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
    var locale = documentContext.getScriptVariantLocale();
    if (tokens.isEmpty()) {
      return Collections.emptyList();
    }
    Token firstToken = tokens.get(0);
    Token lastToken = tokens.get(tokens.size() - 1);

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

    return getTextEdits(tokens, documentContext.getScriptVariantLocale(), params.getRange(), startCharacter, params.getOptions());
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

  public static String getNewText(
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

  private static String checkAndFormatKeyword(Token token, Locale languageLocale) {
    var canonicalText = keywordCanonText.get(token.getType());

    if (canonicalText == null) {
      return token.getText();
    }

    if (languageLocale.equals(Locale.forLanguageTag("en"))) {
      var tokenCanonText = canonicalText.getLeft();
      if (!tokenCanonText.equals(token.getText())) {
        return tokenCanonText;
      }
    } else if (languageLocale.equals(Locale.forLanguageTag("ru"))) {
      var tokenCanonText = canonicalText.getRight();
      if (!tokenCanonText.equals(token.getText())) {
        return tokenCanonText;
      }
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
  private static Map<Integer, Pair<String, String>> getKeywordsCanonicalText() {
    Map<Integer, Pair<String, String>> result = new HashMap<>();

    result.put(BSLLexer.IF_KEYWORD, Pair.of("If", "Если"));
    result.put(BSLLexer.THEN_KEYWORD, Pair.of("Then", "Тогда"));
    result.put(BSLLexer.ELSIF_KEYWORD, Pair.of("ElsIf", "ИначеЕсли"));
    result.put(BSLLexer.ELSE_KEYWORD, Pair.of("Else", "Иначе"));
    result.put(BSLLexer.ENDIF_KEYWORD, Pair.of("EndIf", "КонецЕсли"));
    result.put(BSLLexer.FOR_KEYWORD, Pair.of("For", "Для"));
    result.put(BSLLexer.EACH_KEYWORD, Pair.of("Each", "Каждого"));
    result.put(BSLLexer.IN_KEYWORD, Pair.of("In", "Из"));
    result.put(BSLLexer.TO_KEYWORD, Pair.of("To", "По"));
    result.put(BSLLexer.WHILE_KEYWORD, Pair.of("While", "Пока"));
    result.put(BSLLexer.DO_KEYWORD, Pair.of("Do", "Цикл"));
    result.put(BSLLexer.ENDDO_KEYWORD, Pair.of("EndDo", "КонецЦикла"));
    result.put(BSLLexer.PROCEDURE_KEYWORD, Pair.of("Procedure", "Процедура"));
    result.put(BSLLexer.FUNCTION_KEYWORD, Pair.of("Function", "Функция"));
    result.put(BSLLexer.ENDFUNCTION_KEYWORD, Pair.of("EndFunction", "КонецФункции"));
    result.put(BSLLexer.ENDPROCEDURE_KEYWORD, Pair.of("EndProcedure", "КонецПроцедуры"));
    result.put(BSLLexer.VAR_KEYWORD, Pair.of("Var", "Перем"));
    result.put(BSLLexer.GOTO_KEYWORD, Pair.of("Goto", "Перейти"));
    result.put(BSLLexer.RETURN_KEYWORD, Pair.of("Return", "Возврат"));
    result.put(BSLLexer.BREAK_KEYWORD, Pair.of("Break", "Прервать"));
    result.put(BSLLexer.CONTINUE_KEYWORD, Pair.of("Continue", "Продолжить"));
    result.put(BSLLexer.AND_KEYWORD, Pair.of("And", "И"));
    result.put(BSLLexer.OR_KEYWORD, Pair.of("Or", "Или"));
    result.put(BSLLexer.NOT_KEYWORD, Pair.of("Not", "Не"));
    result.put(BSLLexer.TRY_KEYWORD, Pair.of("Try", "Попытка"));
    result.put(BSLLexer.EXCEPT_KEYWORD, Pair.of("Except", "Исключение"));
    result.put(BSLLexer.RAISE_KEYWORD, Pair.of("Raise", "ВызватьИсключение"));
    result.put(BSLLexer.ENDTRY_KEYWORD, Pair.of("EndTry", "КонецПопытки"));
    result.put(BSLLexer.NEW_KEYWORD, Pair.of("New", "Новый"));
    result.put(BSLLexer.ADDHANDLER_KEYWORD, Pair.of("AddHandler", "ДобавитьОбработчик"));
    result.put(BSLLexer.REMOVEHANDLER_KEYWORD, Pair.of("RemoveHandler", "УдалитьОбработчик"));
    result.put(BSLLexer.ASYNC_KEYWORD, Pair.of("Async", "Асинх"));
    result.put(BSLLexer.AWAIT_KEYWORD, Pair.of("Await", "Ждать"));
    result.put(BSLLexer.VAL_KEYWORD, Pair.of("Val", "Знач"));
    result.put(BSLLexer.EXECUTE_KEYWORD, Pair.of("Execute", "Выполнить"));
    result.put(BSLLexer.EXPORT_KEYWORD, Pair.of("Export", "Экспорт"));

    return result;
  }

}

