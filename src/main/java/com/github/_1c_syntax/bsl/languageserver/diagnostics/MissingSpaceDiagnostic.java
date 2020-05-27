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

import com.ctc.wstx.util.StringUtil;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.providers.CodeActionProvider;
import com.github._1c_syntax.bsl.parser.BSLLexer;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.Token;
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

  private static final int INDEX_WORD_LEFT = 0;
  private static final int INDEX_WORD_RIGHT = 1;
  private static final int INDEX_WORD_LEFT_RIGHT = 2;
  private static final int COUNT_WORDS = 3;

  private final String[] sampleMessage = new String[COUNT_WORDS];

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
  private Boolean checkSpaceToRightOfUnary = DEFAULT_CHECK_SPACE_TO_RIGHT_OF_UNARY;

  @DiagnosticParameter(
    type = Boolean.class,
    defaultValue = "" + DEFAULT_ALLOW_MULTIPLE_COMMAS
  )
  private Boolean allowMultipleCommas = DEFAULT_ALLOW_MULTIPLE_COMMAS;

  private Set<String> setL = Set.of(listForCheckLeft.split(" "));
  private Set<String> setR = Set.of(listForCheckRight.split(" "));
  private Set<String> setLr = Set.of(listForCheckLeftAndRight.split(" "));
  private final Set<String> setUnary = Set.of(UNARY.split(" "));

  public MissingSpaceDiagnostic(DiagnosticInfo info) {
    super(info);
  }

  @Override
  public void check() {

    sampleMessage[INDEX_WORD_LEFT] = info.getResourceString("wordLeft");               // "Слева"
    sampleMessage[INDEX_WORD_RIGHT] = info.getResourceString("wordRight");             // "Справа"
    sampleMessage[INDEX_WORD_LEFT_RIGHT] = info.getResourceString("wordLeftAndRight"); // "Слева и справа"

    List<Token> tokens = documentContext.getTokens();

    for (Token token : tokens) {

      // проверяем слева
      String tokenText = token.getText();
      if (setL.contains(tokenText) && noSpaceLeft(tokens, token)) {
        diagnosticStorage.addDiagnostic(token, getErrorMessage(INDEX_WORD_LEFT, tokenText));
      }

      // проверяем справа
      boolean noSpaceRight = noSpaceRight(tokens, token);
      if (setR.contains(tokenText) && noSpaceRight) {
        diagnosticStorage.addDiagnostic(token, getErrorMessage(INDEX_WORD_RIGHT, tokenText));
      }

      // проверяем слева и справа
      if (setLr.contains(tokenText)) {
        boolean noSpaceLeft = noSpaceLeft(tokens, token);
        if (noSpaceLeft && !noSpaceRight) {
          diagnosticStorage.addDiagnostic(token, getErrorMessage(INDEX_WORD_LEFT, tokenText));
        }
        if (noSpaceLeft && noSpaceRight) {
          diagnosticStorage.addDiagnostic(token, getErrorMessage(INDEX_WORD_LEFT_RIGHT, tokenText));
        }
        if (!noSpaceLeft && noSpaceRight) {
          diagnosticStorage.addDiagnostic(token, getErrorMessage(INDEX_WORD_RIGHT, tokenText));
        }
      }
    }

  }

  @Override
  public void configure(Map<String, Object> configuration) {
    super.configure(configuration);

    setL = Set.of(listForCheckLeft.split(" "));
    setR = Set.of(listForCheckRight.split(" "));
    setLr = Set.of(listForCheckLeftAndRight.split(" "));

  }

  private boolean noSpaceLeft(List<Token> tokens, Token t) {

    Token previousToken = tokens.get(t.getTokenIndex() - 1);
    return previousToken.getType() != BSLParser.LPAREN
      && !StringUtil.isAllWhitespace(previousToken.getText());
  }

  private boolean noSpaceRight(List<Token> tokens, Token t) {

    // Если это унарный + или -, то пробел справа проверяем в соответствии с параметром checkSpaceToRightOfUnary
    // Надо понять, что они унарные
    if (!Boolean.TRUE.equals(checkSpaceToRightOfUnary)
      && (t.getType() == BSLLexer.PLUS || t.getType() == BSLLexer.MINUS)
      && isUnaryChar(tokens, t)) {
      return false;
    }

    Token nextToken;
    if (tokens.size() > t.getTokenIndex() + 1) {
      nextToken = tokens.get(t.getTokenIndex() + 1);

      // Если это запятая и включен allowMultipleCommas, то допустимо что бы справа от нее была еще запятая
      if (!Boolean.TRUE.equals(allowMultipleCommas)
        || t.getType() != BSLLexer.COMMA
        || nextToken.getType() != BSLLexer.COMMA) {
        return !StringUtil.isAllWhitespace(nextToken.getText());
      }
    }
    return false;
  }

  private boolean isUnaryChar(List<Token> tokens, Token t) {

    // Унарные + и -
    // Унарным считаем, если перед ним (пропуская пробельные символы) находим + - * / = % < > ( [ , Возврат <> <= >=
    int currentIndex = t.getTokenIndex() - 1;
    while (currentIndex > 0) {

      if (!StringUtil.isAllWhitespace(tokens.get(currentIndex).getText())) {
        return setUnary.contains(tokens.get(currentIndex).getText());
      }

      currentIndex--;
    }
    return true;
  }

  private String getErrorMessage(int errCode, String tokenText) {
    return info.getMessage(sampleMessage[errCode], tokenText);
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
      Boolean missedLeft = diagnosticMessage.contains("слева") || diagnosticMessage.contains("left");
      Boolean missedRight = diagnosticMessage.contains("справа") || diagnosticMessage.contains("right");

      Range range = diagnostic.getRange();

      if (Boolean.TRUE.equals(missedLeft)) {
        TextEdit textEdit = new TextEdit(
          new Range(range.getStart(), range.getStart()),
          " ");
        textEdits.add(textEdit);
      }
      if (Boolean.TRUE.equals(missedRight)) {
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
}
