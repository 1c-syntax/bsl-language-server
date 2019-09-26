/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
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
package org.github._1c_syntax.bsl.languageserver.diagnostics;

import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import org.github._1c_syntax.bsl.languageserver.providers.CodeActionProvider;
import org.github._1c_syntax.bsl.parser.BSLLexer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  minutesToFix = 1,
  activatedByDefault = true
)

public class MissingSpaceDiagnostic extends AbstractVisitorDiagnostic implements QuickFixProvider {

  private static final String default_listForCheckLeft          = "";      // символы, требующие пробелы только слева
  private static final String default_listForCheckRight         = ", ;";   // ... только справа
  private static final String default_listForCheckLeftAndRight  = "+ - * / = % < > <> <= >="; // ... с обеих сторон
  private static final String default_checkSpaceToRightOfUnary  = "false"; // Проверять пробел справа от унарного знака
  private static final String default_allowMultipleCommas       = "false"; // Разрешить несколько запятых подряд

  @DiagnosticParameter(
    type = String.class,
    defaultValue = "" + default_listForCheckLeft,
    description = "Список символов для проверки слева (разделенные пробелом). Например: ) ="
  )
  private String listForCheckLeft = getRegularString(default_listForCheckLeft);

  @DiagnosticParameter(
    type = String.class,
    defaultValue = "" + default_listForCheckRight,
    description = "Список символов для проверки справа (разделенные пробелом). Например: ( ="
  )
  private String listForCheckRight = getRegularString(default_listForCheckRight);

  @DiagnosticParameter(
    type = String.class,
    defaultValue = "" + default_listForCheckLeftAndRight,
    description = "Список символов для проверки с обоих сторон (разделенные пробелом). Например: + - * / = % < >"
  )
  private String listForCheckLeftAndRight = getRegularString(default_listForCheckLeftAndRight);

  @DiagnosticParameter(
    type = Boolean.class,
    defaultValue = "" + default_checkSpaceToRightOfUnary,
    description = "Проверять наличие пробела справа от унарных знаков (+ -)"
  )
  private Boolean checkSpaceToRightOfUnary = default_checkSpaceToRightOfUnary.equals("true");

  @DiagnosticParameter(
    type = Boolean.class,
    defaultValue = "" + default_allowMultipleCommas,
    description = "Разрешать несколько запятых подряд"
  )
  private Boolean allowMultipleCommas = default_allowMultipleCommas.equals("true");

  private Pattern PATTERN_L  = compilePattern(listForCheckLeft);
  private Pattern PATTERN_R  = compilePattern(listForCheckRight);
  private Pattern PATTERN_LR = compilePattern(listForCheckLeftAndRight);
  private Pattern PATTERN_NOT_SPACE = compilePattern("\\S+");

  @Override
  public List<Diagnostic> getDiagnostics(DocumentContext documentContext) {
    diagnosticStorage.clearDiagnostics();

    List<Token> tokens = documentContext.getTokens();
    List<Token> foundTokens;

    // проверяем слева
    if (PATTERN_L != null) {
      foundTokens = findTokensByPattern(tokens, PATTERN_L);

      foundTokens.stream()
        .filter((Token t) -> noSpaceLeft(tokens, t))
        .forEach((Token t) ->
          diagnosticStorage.addDiagnostic(t, getErrorMessage(1, t.getText()))
        );
    }

    // проверяем справа
    if (PATTERN_R != null) {
      foundTokens = findTokensByPattern(tokens, PATTERN_R);

      foundTokens.stream()
        .filter((Token t) -> noSpaceRight(tokens, t))
        .forEach((Token t) ->
          diagnosticStorage.addDiagnostic(t, getErrorMessage(2, t.getText()))
        );
    }

    // проверяем слева и справа
    if (PATTERN_LR != null) {
      foundTokens = findTokensByPattern(tokens, PATTERN_LR);

      foundTokens.stream()
        .filter((Token t) -> noSpaceLeft(tokens, t) & !noSpaceRight(tokens, t))
        .forEach((Token t) ->
          diagnosticStorage.addDiagnostic(t, getErrorMessage(1, t.getText()))
        );

      foundTokens.stream()
        .filter((Token t) -> !noSpaceLeft(tokens, t) & noSpaceRight(tokens, t))
        .forEach((Token t) ->
          diagnosticStorage.addDiagnostic(t, getErrorMessage(2, t.getText()))
        );

      foundTokens.stream()
        .filter((Token t) -> noSpaceLeft(tokens, t) & noSpaceRight(tokens, t))
        .forEach((Token t) ->
          diagnosticStorage.addDiagnostic(t, getErrorMessage(3, t.getText()))
        );

    }

    return diagnosticStorage.getDiagnostics();
  }

  @Override
  public void configure(Map<String, Object> configuration) {
    if (configuration == null) {
      return;
    }

    String listL_Param = (String) configuration.get("listForCheckLeft");
    if (listL_Param != null){
      listForCheckLeft = getRegularString(listL_Param);
      PATTERN_L = compilePattern(listForCheckLeft);
    }

    String listR_Param = (String) configuration.get("listForCheckRight");
    if (listR_Param != null){
      listForCheckRight = getRegularString(listR_Param);
      PATTERN_R = compilePattern(listForCheckRight);
    }

    String listLR_Param = (String) configuration.get("listForCheckLeftAndRight");
    if (listLR_Param != null){
      listForCheckLeftAndRight = getRegularString(listLR_Param);
      PATTERN_LR = compilePattern(listForCheckLeftAndRight);
    }

    Boolean enableCheckUnary_Param = (Boolean) configuration.get("checkSpaceToRightOfUnary");
    if (enableCheckUnary_Param != null)
      checkSpaceToRightOfUnary = enableCheckUnary_Param;

    Boolean allowMultipleCommas_Param = (Boolean) configuration.get("allowMultipleCommas");
    if (allowMultipleCommas_Param != null)
      allowMultipleCommas = allowMultipleCommas_Param;

  }

  private List<Token> findTokensByPattern(List<Token> tokens, Pattern pattern) {
    return tokens
      .parallelStream()
      .filter((Token t) -> pattern.matcher(t.getText()).matches())
      .collect(Collectors.toList());
  }

  private static String getRegularString(String string) {

    if (string.isEmpty())
      return "";

    StringBuilder singleChar = new StringBuilder();
    StringBuilder doubleChar = new StringBuilder();

    String[] listOfString = string.trim().split(" ");

    for (String s:listOfString) {
      if (s.length() == 1){
        singleChar.append(s);
      } else {
        doubleChar.append("|(").append(s).append(")");
      }
    }

    return "[\\Q"+ singleChar +"\\E]"+doubleChar;
  }

  private static Pattern compilePattern(String string) {

    if (string.isEmpty())
      return null;

    return Pattern.compile(string, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  }

  private boolean noSpaceLeft(List<Token> tokens, Token t) {

    Token previousToken = tokens.get(t.getTokenIndex() - 1);
    return PATTERN_NOT_SPACE.matcher(previousToken.getText()).matches();
  }

  private boolean noSpaceRight(List<Token> tokens, Token t) {

    // Если это унарный + или -, то пробел справа проверяем в соответствии с параметром checkSpaceToRightOfUnary
    if (t.getType() == BSLLexer.PLUS || t.getType() == BSLLexer.MINUS){
      // Надо понять, что они унарные
      if (isUnaryChar(tokens, t) && !checkSpaceToRightOfUnary)
        return false;
    }

    Token nextToken = tokens.get(t.getTokenIndex() + 1);

    // Если это запятая и включен allowMultipleCommas, то допустимо что бы справа от нее была еще запятая
    if (allowMultipleCommas
      && (t.getType() == BSLLexer.COMMA && nextToken.getType() == BSLLexer.COMMA))
      return false;

    return PATTERN_NOT_SPACE.matcher(nextToken.getText()).matches();
  }

  private boolean isUnaryChar(List<Token> tokens, Token t) {

    // Унарные + и -
    // Унарным считаем, если перед ним (пропуская пробельные символы) находим + - * / = % < > ( [ , Возврат <> <= >=

    Pattern CHECK_CHAR = compilePattern(getRegularString("+ - * / = % < > ( [ , Возврат <> <= >="));

    Integer currentIndex = t.getTokenIndex() - 1;
    while (currentIndex > 0){

      if (PATTERN_NOT_SPACE.matcher(tokens.get(currentIndex).getText()).matches())
        if (CHECK_CHAR.matcher(tokens.get(currentIndex).getText()).matches())
          return true;
        else
          return false;

      currentIndex--;
    }
    return true;
  }

  private String getErrorMessage(int errCode, String tokenText) {

    String errorKey;
    String[] sampleMessage = new String[4];

    sampleMessage[0] = getResourceString("wordLeftOrRight");  // "Слева или справа";
    sampleMessage[1] = getResourceString("wordLeft");         // "Слева";
    sampleMessage[2] = getResourceString("wordRight");        // "Справа";
    sampleMessage[3] = getResourceString("wordLeftAndRight"); // "Слева и справа";

    if (errCode == 1 || errCode == 2 || errCode == 3)
      errorKey = sampleMessage[errCode];
    else
      errorKey = sampleMessage[0];

    return getDiagnosticMessage(errorKey, tokenText);
  }

  @Override
  public List<CodeAction> getQuickFixes(
    List<Diagnostic> diagnostics,
    CodeActionParams params,
    DocumentContext documentContext
  ) {

    List<TextEdit> textEdits = new ArrayList<>();

    diagnostics.forEach((Diagnostic diagnostic) -> {
      String diagnosticMessage = diagnostic.getMessage().toLowerCase();

      // TODO @YanSergey. Переделать после выполнения issue #371 'Доработки ядра. Хранение информации для квикфиксов'
      Boolean missedLeft = diagnosticMessage.contains("слева") || diagnosticMessage.contains("left");
      Boolean missedRight = diagnosticMessage.contains("справа") || diagnosticMessage.contains("right");

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
      getResourceString("quickFixMessage"),
      documentContext.getUri(),
      diagnostics
    );
  }
}
