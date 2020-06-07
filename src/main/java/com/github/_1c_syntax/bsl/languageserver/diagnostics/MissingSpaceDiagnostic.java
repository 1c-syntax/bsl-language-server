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
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
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

  @Nonnull
  private static final Pattern patternNotSpace = CaseInsensitivePattern.compile("\\S+");
  private static final Pattern BEFORE_UNARY_CHAR_PATTERN = CaseInsensitivePattern.compile(
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
  private boolean checkSpaceToRightOfUnary = DEFAULT_CHECK_SPACE_TO_RIGHT_OF_UNARY;

  @DiagnosticParameter(
    type = Boolean.class,
    defaultValue = "" + DEFAULT_ALLOW_MULTIPLE_COMMAS
  )
  private boolean allowMultipleCommas = DEFAULT_ALLOW_MULTIPLE_COMMAS;

  @Nullable
  private Pattern patternL = compilePattern(listForCheckLeft);
  @Nullable
  private Pattern patternR = compilePattern(listForCheckRight);
  @Nullable
  private Pattern patternLr = compilePattern(listForCheckLeftAndRight);
  private String mainMessage;
  private String indexWordLeftMsg;
  private String indexWordRightMsg;
  private String indexWordLeftRightMsg;

  public MissingSpaceDiagnostic(DiagnosticInfo info) {
    super(info);
  }

  @Override
  public void check() {

    if (patternL == null && patternR == null && patternLr == null) {
      return;
    }

    mainMessage = info.getMessage();
    indexWordLeftMsg = info.getResourceString("wordLeft");
    indexWordRightMsg = info.getResourceString("wordRight");
    indexWordLeftRightMsg = info.getResourceString("wordLeftAndRight");

    List<Token> tokens = documentContext.getTokensFromDefaultChannel();
    boolean noSpaceLeft = false;
    boolean noSpaceRight = false;

    for (int i = 0; i < tokens.size(); i++) {
      var token = tokens.get(i);

      boolean checkLeft = false;
      boolean checkRight = false;

      final var text = token.getText();

      if (patternL != null && patternL.matcher(text).matches()) {
        noSpaceLeft = noSpaceLeft(tokens, token, i);
        checkLeft = true;

        if (noSpaceLeft) {
          addDiagnostic(token, mainMessage, indexWordLeftMsg);
        }
      }
      if (patternR != null && patternR.matcher(text).matches()) {
        noSpaceRight = noSpaceRight(tokens, token, i);
        checkRight = true;

        if (noSpaceRight) {
          addDiagnostic(token, mainMessage, indexWordRightMsg);
        }
      }
      if (patternLr != null && patternLr.matcher(text).matches()) {
        if (!checkLeft) {
          noSpaceLeft = noSpaceLeft(tokens, token, i);
        }
        if (!checkRight) {
          noSpaceRight = noSpaceRight(tokens, token, i);
        }
        checkLeftRight(token, noSpaceLeft, noSpaceRight);
      }
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

  private void checkLeftRight(Token t, boolean noSpaceLeft, boolean noSpaceRight) {

    String errorMessage = null;
    if (noSpaceLeft && !noSpaceRight) {
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
    if (errorMessage != null) {
      addDiagnostic(t, mainMessage, errorMessage);
    }
  }

  private void addDiagnostic(Token t, String mainMessage, String errorMessage) {
    diagnosticStorage.addDiagnostic(t, getErrorMessage(mainMessage, errorMessage, t.getText()));
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

    return CaseInsensitivePattern.compile(string);
  }

  private static boolean noSpaceLeft(List<Token> tokens, Token t, int i) {

    Token previousToken = tokens.get(i - 1);

    return previousToken.getType() != BSLParser.LPAREN
      && noSpaceBetween(previousToken, t) && patternNotSpace.matcher(previousToken.getText()).find();
  }

  private static boolean noSpaceBetween(Token prev, Token next) {
    return prev.getLine() == next.getLine()
      && getLastCharPositionInLine(prev) + 1 == next.getCharPositionInLine();
  }

  private static int getLastCharPositionInLine(Token t) {
    return t.getCharPositionInLine() + t.getStopIndex() - t.getStartIndex();
  }

  private boolean noSpaceRight(List<Token> tokens, Token t, int i) {

    // Если это унарный + или -, то пробел справа проверяем в соответствии с параметром checkSpaceToRightOfUnary
    // Надо понять, что они унарные
    if (i + 1 >= tokens.size() || !checkSpaceToRightOfUnary
      && (t.getType() == BSLLexer.PLUS || t.getType() == BSLLexer.MINUS)
      && isUnaryChar(tokens, i)) {
      return false;
    }

    Token nextToken = tokens.get(i + 1);

    // Если это запятая и включен allowMultipleCommas, то допустимо что бы справа от нее была еще запятая
    if (allowMultipleCommas
      && t.getType() == BSLLexer.COMMA
      && nextToken.getType() == BSLLexer.COMMA) {
      return false;
    }

    return noSpaceBetween(t, nextToken) && patternNotSpace.matcher(nextToken.getText()).find();
  }

  private static boolean isUnaryChar(List<Token> tokens, int i) {

    // Унарные + и -
    // Унарным считаем, если перед ним (пропуская пробельные символы) находим + - * / = % < > ( [ , Возврат <> <= >=

    int currentIndex = i - 1;
    while (currentIndex > 0) {

      final var text = tokens.get(currentIndex).getText();
      if (patternNotSpace.matcher(text).find()) {
        return BEFORE_UNARY_CHAR_PATTERN.matcher(text).find();
      }

      currentIndex--;
    }
    return true;
  }

  private static String getErrorMessage(String formatString, String errorMessage, String tokenText) {
    return String.format(formatString, errorMessage, tokenText).intern();
  }
}
