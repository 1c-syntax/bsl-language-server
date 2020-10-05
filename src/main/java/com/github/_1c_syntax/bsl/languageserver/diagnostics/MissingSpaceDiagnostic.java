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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.providers.CodeActionProvider;
import com.github._1c_syntax.bsl.parser.BSLLexer;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.Token;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.BADPRACTICE
  }
)
public class MissingSpaceDiagnostic extends AbstractDiagnostic implements QuickFixProvider {

  // символы, требующие пробелы только слева
  private static final String DEFAULT_LIST_FOR_CHECK_LEFT = "";
  // ... только справа
  private static final String DEFAULT_LIST_FOR_CHECK_RIGHT = ", ;";
  // ... с обеих сторон
  private static final String DEFAULT_LIST_FOR_CHECK_LEFT_AND_RIGHT = "+ - * / = % < > <> <= >=";
  // Проверять пробел справа от унарного знака
  private static final boolean DEFAULT_CHECK_SPACE_TO_RIGHT_OF_UNARY = false;
  // Разрешить несколько запятых подряд
  private static final boolean DEFAULT_ALLOW_MULTIPLE_COMMAS = false;

  private static final String UNARY = "+ - * / = % < > ( [ , Возврат Return <> <= >=";

  /**
   * Ключевые слова, требующие пробел слева и справа
   */
  private static final List<Integer> KEYWORDS_WITH_LEFT_RIGHT_SPACE = computeKeywordsWithLeftRightSpace();
  /**
   * Ключевые слова, требующие пробел слева
   */
  private static final List<Integer> KEYWORDS_WITH_LEFT_SPACE = computeKeywordsWithLeftSpace();
  /**
   * Ключевые слова, требующие пробел справа
   */
  private static final List<Integer> KEYWORDS_WITH_RIGHT_SPACE = computeKeywordsWithRightSpace();

  @DiagnosticParameter(
    type = String.class,
    defaultValue = "" + DEFAULT_LIST_FOR_CHECK_LEFT
  )
  private String listForCheckLeft = DEFAULT_LIST_FOR_CHECK_LEFT;

  @DiagnosticParameter(
    type = String.class,
    defaultValue = "" + DEFAULT_LIST_FOR_CHECK_RIGHT
  )
  private String listForCheckRight = DEFAULT_LIST_FOR_CHECK_RIGHT;

  @DiagnosticParameter(
    type = String.class,
    defaultValue = "" + DEFAULT_LIST_FOR_CHECK_LEFT_AND_RIGHT
  )
  private String listForCheckLeftAndRight = DEFAULT_LIST_FOR_CHECK_LEFT_AND_RIGHT;

  @DiagnosticParameter(
    type = Boolean.class,
    defaultValue = "" + DEFAULT_CHECK_SPACE_TO_RIGHT_OF_UNARY
  )
  private boolean checkSpaceToRightOfUnary = DEFAULT_CHECK_SPACE_TO_RIGHT_OF_UNARY;

  @DiagnosticParameter(
    type = Boolean.class,
    defaultValue = "" + DEFAULT_ALLOW_MULTIPLE_COMMAS
  )
  private boolean allowMultipleCommas = DEFAULT_ALLOW_MULTIPLE_COMMAS;

  private String mainMessage;
  private String indexWordLeftMsg;
  private String indexWordRightMsg;
  private String indexWordLeftRightMsg;

  private Set<String> setL = Set.of(listForCheckLeft.split(" "));
  private Set<String> setR = Set.of(listForCheckRight.split(" "));
  private Set<String> setLR = Set.of(listForCheckLeftAndRight.split(" "));
  private final Set<String> setUnary = Set.of(UNARY.split(" "));

  @Override
  public void check() {

    mainMessage = info.getMessage();
    indexWordLeftMsg = info.getResourceString("wordLeft");
    indexWordRightMsg = info.getResourceString("wordRight");
    indexWordLeftRightMsg = info.getResourceString("wordLeftAndRight");

    List<Token> tokens = documentContext.getTokens();

    for (Token token : tokens) {

      boolean leftComputed = false;
      boolean rightComputed = false;

      boolean noSpaceLeft = false;
      boolean noSpaceRight = false;

      String tokenText = token.getText();

      // проверяем слева
      if ((setL.contains(tokenText) || KEYWORDS_WITH_LEFT_SPACE.contains(token.getType()))
        && (noSpaceLeft = noSpaceLeft(tokens, token))) {
        leftComputed = true;
        addDiagnostic(token, mainMessage, indexWordLeftMsg);
      }

      // проверяем справа
      if ((setR.contains(tokenText) || KEYWORDS_WITH_RIGHT_SPACE.contains(token.getType()))
        && (noSpaceRight = noSpaceRight(tokens, token))) {
        rightComputed = true;
        addDiagnostic(token, mainMessage, indexWordRightMsg);
      }

      // проверяем слева и справа
      if (setLR.contains(tokenText) || KEYWORDS_WITH_LEFT_RIGHT_SPACE.contains(token.getType())) {
        if (!leftComputed) {
          noSpaceLeft = noSpaceLeft(tokens, token);
        }
        if (!rightComputed) {
          noSpaceRight = noSpaceRight(tokens, token);
        }
        addDiagnosticLeftRight(token, noSpaceLeft, noSpaceRight);
      }

    }

  }

  @Override
  public void configure(Map<String, Object> configuration) {
    super.configure(configuration);

    setL = Set.of(listForCheckLeft.split(" "));
    setR = Set.of(listForCheckRight.split(" "));
    setLR = Set.of(listForCheckLeftAndRight.split(" "));
  }

  @Override
  public List<CodeAction> getQuickFixes(
    List<Diagnostic> diagnostics,
    CodeActionParams params,
    DocumentContext documentContext
  ) {

    List<TextEdit> textEdits = new ArrayList<>();

    diagnostics.forEach((Diagnostic diagnostic) -> {
      String diagnosticMessage = diagnostic.getMessage().toLowerCase(Locale.ENGLISH);

      // TODO @YanSergey. Переделать после выполнения issue #371 'Доработки ядра. Хранение информации для квикфиксов'
      boolean missedLeft = diagnosticMessage.contains("слева") || diagnosticMessage.contains("left");
      boolean missedRight = diagnosticMessage.contains("справа") || diagnosticMessage.contains("right");

      Range range = diagnostic.getRange();

      if (missedLeft) {
        TextEdit textEdit = new TextEdit(
          new Range(range.getStart(), range.getStart()),
          " ");
        textEdits.add(textEdit);
      }
      if (missedRight) {
        TextEdit textEdit = new TextEdit(
          new Range(range.getEnd(), range.getEnd()),
          " ");
        textEdits.add(textEdit);
      }
    });

    return CodeActionProvider.createCodeActions(
      textEdits,
      info.getResourceString("quickFixMessage"),
      documentContext.getUri(),
      diagnostics
    );
  }

  private void addDiagnostic(Token t, String mainMessage, String errorMessage) {
    diagnosticStorage.addDiagnostic(t, getErrorMessage(mainMessage, errorMessage, t.getText()));
  }

  private void addDiagnosticLeftRight(Token token, boolean noSpaceLeft, boolean noSpaceRight) {
    String errorMessage;
    if (noSpaceLeft && !noSpaceRight) {
      errorMessage = indexWordLeftMsg;
    } else if (noSpaceLeft) {
      errorMessage = indexWordLeftRightMsg;
    } else if (noSpaceRight) {
      errorMessage = indexWordRightMsg;
    } else {
      return;
    }

    addDiagnostic(token, mainMessage, errorMessage);
  }

  private static boolean noSpaceLeft(List<Token> tokens, Token t) {

    Token previousToken = tokens.get(t.getTokenIndex() - 1);
    return previousToken.getType() != BSLParser.LPAREN
      && !StringUtils.isWhitespace(previousToken.getText());
  }

  private boolean noSpaceRight(List<Token> tokens, Token t) {

    // Если это унарный + или -, то пробел справа проверяем в соответствии с параметром checkSpaceToRightOfUnary
    // Надо понять, что они унарные
    if (!checkSpaceToRightOfUnary
      && (t.getType() == BSLLexer.PLUS || t.getType() == BSLLexer.MINUS)
      && isUnaryChar(tokens, t)) {
      return false;
    }

    Token nextToken;
    if (tokens.size() > t.getTokenIndex() + 1) {
      nextToken = tokens.get(t.getTokenIndex() + 1);
      if (nextToken.getType() == Token.EOF) {
        return false;
      }

      // Если это запятая и включен allowMultipleCommas, то допустимо что бы справа от нее была еще запятая
      if (!allowMultipleCommas
        || t.getType() != BSLLexer.COMMA
        || nextToken.getType() != BSLLexer.COMMA) {
        return !StringUtils.isWhitespace(nextToken.getText());
      }
    }
    return false;
  }

  private boolean isUnaryChar(List<Token> tokens, Token t) {

    // Унарные + и -
    // Унарным считаем, если перед ним (пропуская пробельные символы) находим + - * / = % < > ( [ , Возврат <> <= >=
    int currentIndex = t.getTokenIndex() - 1;
    while (currentIndex > 0) {

      if (!StringUtils.isWhitespace(tokens.get(currentIndex).getText())) {
        return setUnary.contains(tokens.get(currentIndex).getText());
      }

      currentIndex--;
    }
    return true;
  }

  private static String getErrorMessage(String formatString, String errorMessage, String tokenText) {
    return String.format(formatString, errorMessage, tokenText).intern();
  }

  private static List<Integer> computeKeywordsWithLeftRightSpace() {
    List<Integer> leftRight = new ArrayList<>();
    leftRight.add(BSLParser.TO_KEYWORD);
    leftRight.add(BSLParser.IN_KEYWORD);
    leftRight.add(BSLParser.OR_KEYWORD);
    leftRight.add(BSLParser.AND_KEYWORD);
    return leftRight;
  }

  private static List<Integer> computeKeywordsWithLeftSpace() {
    List<Integer> left = new ArrayList<>();
    left.add(BSLParser.EXPORT_KEYWORD);
    left.add(BSLParser.THEN_KEYWORD);
    left.add(BSLParser.DO_KEYWORD);
    return left;
  }

  private static List<Integer> computeKeywordsWithRightSpace() {
    List<Integer> right = new ArrayList<>();
    right.add(BSLParser.IF_KEYWORD);
    right.add(BSLParser.ELSIF_KEYWORD);
    right.add(BSLParser.WHILE_KEYWORD);
    right.add(BSLParser.FOR_KEYWORD);
    right.add(BSLParser.NOT_KEYWORD);
    right.add(BSLParser.EACH_KEYWORD);
    return right;
  }

}
