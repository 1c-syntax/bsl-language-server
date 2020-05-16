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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.providers.CodeActionProvider;
import com.github._1c_syntax.bsl.languageserver.utils.DiagnosticHelper;
import com.github._1c_syntax.bsl.parser.BSLLexer;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.BADPRACTICE
  }
)
public class MissingSpaceDiagnostic extends AbstractVisitorDiagnostic implements QuickFixProvider {

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

  @Nonnull private static final Pattern patternNotSpace = compilePattern("\\S+");
  private static final Pattern BEFORE_UNARY_CHAR_PATTERN = compilePattern(
    getRegularString("+ - * / = % < > ( [ , Возврат <> <= >="));

  @DiagnosticParameter(
    type = String.class,
    defaultValue = "" + DEFAULT_LIST_FOR_CHECK_LEFT
  )
  private String listForCheckLeft = getRegularString(DEFAULT_LIST_FOR_CHECK_LEFT);

  @DiagnosticParameter(
    type = String.class,
    defaultValue = "" + DEFAULT_LIST_FOR_CHECK_RIGHT
  )
  private String listForCheckRight = getRegularString(DEFAULT_LIST_FOR_CHECK_RIGHT);

  @DiagnosticParameter(
    type = String.class,
    defaultValue = "" + DEFAULT_LIST_FOR_CHECK_LEFT_AND_RIGHT
  )
  private String listForCheckLeftAndRight = getRegularString(DEFAULT_LIST_FOR_CHECK_LEFT_AND_RIGHT);

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

  private @Nullable Pattern patternL = compilePattern(listForCheckLeft);
  private @Nullable Pattern patternR = compilePattern(listForCheckRight);
  private @Nullable Pattern patternLr = compilePattern(listForCheckLeftAndRight);
  private final String mainMessage;
  private final String indexWordLeftMsg;
  private final String indexWordRightMsg;
  private final String indexWordLeftRightMsg;

  public MissingSpaceDiagnostic(DiagnosticInfo info) {
    super(info);
    mainMessage = this.info.getMessage();
    indexWordLeftMsg = this.info.getResourceString("wordLeft");
    indexWordRightMsg = this.info.getResourceString("wordRight");
    indexWordLeftRightMsg = this.info.getResourceString("wordLeftAndRight");
  }

  private static String getRegularString(String string) {

    if (string.isEmpty()) {
      return "";
    }

    StringBuilder singleChar = new StringBuilder();
    StringBuilder doubleChar = new StringBuilder();

    String[] listOfString = string.trim().split(" ");

    for (String s : listOfString) {
      if (s.length() == 1) {
        singleChar.append(s);
      } else {
        doubleChar.append("|(?:").append(s).append(")");
      }
    }

    return "[\\Q" + singleChar + "\\E]" + doubleChar;
  }

  private static Pattern compilePattern(String string) {

    if (string.isEmpty()) {
      return null;
    }

    return Pattern.compile(string, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  }

  @Override
  public List<Diagnostic> getDiagnostics(DocumentContext documentContext) {

    if (patternL == null && patternR == null && patternLr == null){
      return Collections.emptyList();
    }

    diagnosticStorage.clearDiagnostics();

    List<Token> tokens = documentContext.getTokens();
    boolean noSpaceLeft = false;
    boolean noSpaceRight = false;

    for (var token:tokens){
      boolean checkLeft = false;
      boolean checkRight = false;

      final var text = token.getText();

      if (patternL != null && patternL.matcher(text).matches()){
        noSpaceLeft = noSpaceLeft(tokens, token);
        checkLeft = true;

        if (noSpaceLeft){
          addDiagnostic(token, mainMessage, indexWordLeftMsg);
        }
      }
      if (patternR != null && patternR.matcher(text).matches()){
        noSpaceRight = noSpaceRight(tokens, token);
        checkRight = true;

        if (noSpaceRight){
          addDiagnostic(token, mainMessage, indexWordRightMsg);
        }
      }
      if (patternLr != null && patternLr.matcher(text).matches()){
        if (!checkLeft){
          noSpaceLeft = noSpaceLeft(tokens, token);
        }
        if (!checkRight){
          noSpaceRight = noSpaceRight(tokens, token);
        }
        checkLeftRight(token, noSpaceLeft, noSpaceRight);
      }
    }

    return diagnosticStorage.getDiagnostics();
  }

  private void checkLeftRight(Token t, boolean noSpaceLeft, boolean noSpaceRight) {

    String errorMessage = null;
    if (noSpaceLeft && !noSpaceRight){
      errorMessage = indexWordLeftMsg;
    } else {
      if (!noSpaceLeft && noSpaceRight) {
        errorMessage = indexWordRightMsg;
      } else {
        if (noSpaceLeft) {
          errorMessage = indexWordLeftRightMsg;
        }
      }
    }
    addDiagnostic(t, mainMessage, errorMessage);
  }

  private void addDiagnostic(Token t, String mainMessage, String errorMessage) {
    if (errorMessage != null){
      diagnosticStorage.addDiagnostic(t, getErrorMessage(mainMessage, errorMessage, t.getText()));
    }
  }

  @Override
  public void configure(Map<String, Object> configuration) {
    if (configuration == null) {
      return;
    }

    DiagnosticHelper.configureDiagnostic(this, configuration,
      "checkSpaceToRightOfUnary", "allowMultipleCommas");

    String listLParam =
      (String) configuration.getOrDefault("listForCheckLeft", DEFAULT_LIST_FOR_CHECK_LEFT);
    listForCheckLeft = getRegularString(listLParam);
    patternL = compilePattern(listForCheckLeft);

    String listRParam =
      (String) configuration.getOrDefault("listForCheckRight", DEFAULT_LIST_FOR_CHECK_RIGHT);
    listForCheckRight = getRegularString(listRParam);
    patternR = compilePattern(listForCheckRight);

    String listLRParam =
      (String) configuration.getOrDefault("listForCheckLeftAndRight", DEFAULT_LIST_FOR_CHECK_LEFT_AND_RIGHT);
    listForCheckLeftAndRight = getRegularString(listLRParam);
    patternLr = compilePattern(listForCheckLeftAndRight);

  }

  private boolean noSpaceLeft(List<Token> tokens, Token t) {

    Token previousToken = tokens.get(t.getTokenIndex() - 1);
    return previousToken.getType() != BSLParser.LPAREN
      && patternNotSpace.matcher(previousToken.getText()).find();
  }

  private boolean noSpaceRight(List<Token> tokens, Token t) {

    // Если это унарный + или -, то пробел справа проверяем в соответствии с параметром checkSpaceToRightOfUnary
    // Надо понять, что они унарные
    if (t.getTokenIndex() + 1 >= tokens.size() || (t.getType() == BSLLexer.PLUS || t.getType() == BSLLexer.MINUS)
      && isUnaryChar(tokens, t) && !Boolean.TRUE.equals(checkSpaceToRightOfUnary)) {
      return false;
    }

    Token nextToken = tokens.get(t.getTokenIndex() + 1);

    // Если это запятая и включен allowMultipleCommas, то допустимо что бы справа от нее была еще запятая
    if (Boolean.TRUE.equals(allowMultipleCommas)
      && t.getType() == BSLLexer.COMMA
      && nextToken.getType() == BSLLexer.COMMA) {
      return false;
    }
    return patternNotSpace.matcher(nextToken.getText()).find();
  }

  private boolean isUnaryChar(List<Token> tokens, Token t) {

    // Унарные + и -
    // Унарным считаем, если перед ним (пропуская пробельные символы) находим + - * / = % < > ( [ , Возврат <> <= >=

    if (BEFORE_UNARY_CHAR_PATTERN == null) {
      return false;
    }

    int currentIndex = t.getTokenIndex() - 1;
    while (currentIndex > 0) {

      final var text = tokens.get(currentIndex).getText();
      if (patternNotSpace.matcher(text).find()) {
        return BEFORE_UNARY_CHAR_PATTERN.matcher(text).find();
      }

      currentIndex--;
    }
    return true;
  }

  private String getErrorMessage(String formatString, String errorMessage, String tokenText) {
    return String.format(formatString, errorMessage, tokenText).intern();
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
