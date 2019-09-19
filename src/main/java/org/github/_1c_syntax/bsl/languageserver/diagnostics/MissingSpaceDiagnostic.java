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
  type = DiagnosticType.ERROR, //TODO Не забыть заменить обратно
  severity = DiagnosticSeverity.BLOCKER,
  /*type = DiagnosticType.CODE_SMELL
  severity = DiagnosticSeverity.INFO,*/
  minutesToFix = 1,
  activatedByDefault = true   //TODO решить, включено ли по умолчанию
)

public class MissingSpaceDiagnostic extends AbstractVisitorDiagnostic implements QuickFixProvider {

  private static final String default_listForCheckLeft = "";          // символы, требующие пробелы только слева
  private static final String default_listForCheckRight = ", ;";      // символы, требующие пробелы только справа
  private static final String default_listForCheckLeftAndRight = "+ - * / = % < > <> <= >="; // символы, требующие пробелы с обоих сторон
  private static final String default_checkSpaceToRightOfUnary = "false";   // Проверять пробел справа от унарного знака
  private static final String default_allowMultipleCommas = "false";        // Разрешить несколько запятых подряд
  private static Boolean diagnosticLanguageIsRU = true;

  @DiagnosticParameter(
    type = String.class,
    defaultValue = "" + default_listForCheckLeft,
    description = "Список символов для проверки слева (разделенные пробелом). Например: ) ="
  )
  private static String listForCheckLeft = getRegularString(default_listForCheckLeft);

  @DiagnosticParameter(
    type = String.class,
    defaultValue = "" + default_listForCheckRight,
    description = "Список символов для проверки справа (разделенные пробелом). Например: ( ="
  )
  private static String listForCheckRight = getRegularString(default_listForCheckRight);

  @DiagnosticParameter(
    type = String.class,
    defaultValue = "" + default_listForCheckLeftAndRight,
    description = "Список символов для проверки с обоих сторон (разделенные пробелом). Например: + - * / = % < >"
  )
  private static String listForCheckLeftAndRight = getRegularString(default_listForCheckLeftAndRight);

  @DiagnosticParameter(
    type = String.class,
    defaultValue = "" + default_checkSpaceToRightOfUnary,
    description = "Проверять наличие пробела справа от унарных знаков (+ -)"
  )
  private static Boolean checkSpaceToRightOfUnary = default_checkSpaceToRightOfUnary == "true";

  @DiagnosticParameter(
    type = String.class,
    defaultValue = "" + default_allowMultipleCommas,
    description = "Разрешать несколько запятых подряд"
  )
  private static Boolean allowMultipleCommas = default_allowMultipleCommas == "true";

  private static Pattern PATTERN_L  = compilePattern(listForCheckLeft);
  private static Pattern PATTERN_R  = compilePattern(listForCheckRight);
  private static Pattern PATTERN_LR = compilePattern(listForCheckLeftAndRight);
  private static Pattern PATTERN_NOT_SPACE = compilePattern("\\S+");



  @Override
  public List<Diagnostic> getDiagnostics(DocumentContext documentContext) {
    diagnosticStorage.clearDiagnostics();

    //TODO Задачи
    // 1. Унарные + и -
    //    - Унарным считаем, если перед ним (пропуская пробельные символы) находим + - * / = % < > ( [ , Возврат <> <= >=
    // +2. Дописать тест
    // +3. Справа от запятой может быть запятая
    // +4. Реализовать быстрое исправление
    // +5. Вынести в параметры:
    //    - для запятой есть исключение - справа может быть другая запятая. Сделать параметр
    //        типа "Допускать справа от запятой другую запятую" или "Разрешить несколько запятых подряд"
    //    - символы, у которых проверять слева и справа
    //    - символы, у которых проверять только слева
    //    - символы, у которых проверять только справа
    //    - для унарных знаков, параметр, проверять ли справа наличие пробела - по умолчанию проверять
    //        типа "Проверять наличие пробела справа от унарных знаков"

    List<Token> tokens = documentContext.getTokens();


    // проверяем справа
    if (PATTERN_R != null) {
      List<Token> tokensR = findTokensByPattern(tokens, PATTERN_R);

      tokensR.stream()
        .filter((Token t) -> noSpaceRight(tokens, t))
        .forEach((Token t) ->{
          diagnosticStorage.addDiagnostic(t,
            getDiagnosticMessage(getErrorMessage(2), t.getText()));
        });
    }


    // проверяем слева и справа
    if (PATTERN_LR != null) {
      List<Token> tokensLoR = findTokensByPattern(tokens, PATTERN_LR);

      tokensLoR.stream()
        .filter((Token t) -> noSpaceLeft(tokens, t) & !noSpaceRight(tokens, t))
        .forEach((Token t) ->
          diagnosticStorage.addDiagnostic(t,
            getDiagnosticMessage(getErrorMessage(1), t.getText())));

      tokensLoR.stream()
        .filter((Token t) -> !noSpaceLeft(tokens, t) & noSpaceRight(tokens, t))
        .forEach((Token t) ->
          diagnosticStorage.addDiagnostic(t,
            getDiagnosticMessage(getErrorMessage(2), t.getText())));

      tokensLoR.stream()
        .filter((Token t) -> noSpaceLeft(tokens, t) & noSpaceRight(tokens, t))
        .forEach((Token t) ->
          diagnosticStorage.addDiagnostic(t,
            getDiagnosticMessage(getErrorMessage(3), t.getText())));

    }


    return diagnosticStorage.getDiagnostics();
  }

  private List<Token> findTokensByPattern(List<Token> tokens, Pattern pattern) {
    return tokens
      .parallelStream()
      .filter((Token t) -> pattern.matcher(t.getText()).matches()).collect(Collectors.toList());
  }


  @Override
  public void configure(Map<String, Object> configuration) {
    if (configuration == null) {
      return;
    }

    String diagnosticLanguage_Param = (String) configuration.get("diagnosticLanguage");
    if (diagnosticLanguage_Param != null)
      this.diagnosticLanguageIsRU = diagnosticLanguage_Param == "ru";

    String listL_Param = (String) configuration.get("listForCheckLeft");
    if (listL_Param != null){
      this.listForCheckLeft = getRegularString(listL_Param);
      PATTERN_L = compilePattern(listForCheckLeft);
    }

    String listR_Param = (String) configuration.get("listForCheckRight");
    if (listR_Param != null){
      this.listForCheckRight = getRegularString(listR_Param);
      PATTERN_R = compilePattern(listForCheckRight);
    }

    String listLR_Param = (String) configuration.get("listForCheckLeftAndRight");
    if (listLR_Param != null){
      this.listForCheckLeftAndRight = getRegularString(listLR_Param);
      PATTERN_LR = compilePattern(listForCheckLeftAndRight);
    }

    String enableCheckUnary_Param = (String) configuration.get("checkSpaceToRightOfUnary");
    if (enableCheckUnary_Param != null)
      this.checkSpaceToRightOfUnary = enableCheckUnary_Param == "true";

    String allowMultipleCommas_Param = (String) configuration.get("allowMultipleCommas");
    if (allowMultipleCommas_Param != null)
      this.allowMultipleCommas = allowMultipleCommas_Param == "true";

  }



  private static String getRegularString(String string) {

    if (string.isEmpty())
      return "";

    String singleChar = "", doubleChar = "";

    String[] listOfString = string.trim().split(" ");

    for (String s:listOfString) {
      if (s.length() == 1){
        singleChar = singleChar + s;
      } else {
        doubleChar = doubleChar + "|(" + s + ")";
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

    //return PATTERN_NOT_SPACE.matcher(tokens.get(t.getTokenIndex() - 1).getText()).matches();
  }
  private boolean noSpaceRight(List<Token> tokens, Token t) {

    // Если это унарный + или - то пробел справа проверяем в соответствии с параметром
    if (t.getType() == BSLLexer.PLUS || t.getType() == BSLLexer.MINUS){
      // Надо понять что они унарные
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
    // TODO Что то неверно работает в определении унарности. Тест работает, в VSC - нет
    // 1. Унарные + и -
    //    - Унарным считаем, если перед ним (пропуская пробельные символы) находим + - * / = % < > ( [ , Возврат <> <= >=

    Pattern CHECK_CHAR = compilePattern(getRegularString("+ - * / = % < > ( [ , Возврат <> <= >="));

    Integer currentIndex = t.getTokenIndex() - 1;
    while (true){

      if (PATTERN_NOT_SPACE.matcher(tokens.get(currentIndex).getText()).matches())
        if (CHECK_CHAR.matcher(tokens.get(currentIndex).getText()).matches())
          return true;
        else
          return false;

      currentIndex--;
    }
  }

  private String getErrorMessage(int errCode) {
    //TODO Локализовать сообщения. Переделать, чтоб красиво было

    String sampleLeftOrRight, sampleLeft, sampleRight, sampleLeftAndRight;

    String[] sampleMessage = new String[4];

    if (diagnosticLanguageIsRU) {
      /*sampleLeftOrRight   = "Слева или справа";
      sampleLeft          = "Слева";
      sampleRight         = "Справа";
      sampleLeftAndRight  = "Слева и справа";*/

      sampleMessage[0] = "Слева или справа";
      sampleMessage[1] = "Слева";
      sampleMessage[2] = "Справа";
      sampleMessage[3] = "Слева и справа";

    } else {
      /*sampleLeftOrRight   = "Left or right";
      sampleLeft          = "To the left";
      sampleRight         = "To the right";
      sampleLeftAndRight  = "Left and right";*/

      sampleMessage[0] = "Left or right";
      sampleMessage[1] = "To the left";
      sampleMessage[2] = "To the right";
      sampleMessage[3] = "Left and right";

    }

    if (errCode == 1 || errCode == 2 || errCode == 3)
      return sampleMessage[errCode];
    else
      return sampleMessage[0];


    /*String errMessage = sampleLeftOrRight;

    switch (errCode){
      case 1:
        errMessage = sampleLeft;
        break;
      case 2:
        errMessage = sampleRight;
        break;
      case 3:
        errMessage = sampleLeftAndRight;
        break;
      default:
        break;
    }*/

    //return errMessage;
  }

  private boolean noSpaceLeftAndRight1(List<Token> tokens, Token t) {
    int tokenIndex = t.getTokenIndex();
    Token prevToken = tokens.get(tokenIndex - 1);
    String prevTokenText = prevToken.getText();

    Token nextToken = tokens.get(tokenIndex + 1);
    String nextTokenText = nextToken.getText();

    return PATTERN_NOT_SPACE.matcher(prevTokenText).matches()
      ||
      PATTERN_NOT_SPACE.matcher(nextTokenText).matches()
      ;

    //return PATTERN_SPACE.matcher(tokens.get(t.getTokenIndex() + 1).getText()).matches();
  }
  private boolean noSpaceRight1(List<Token> tokens, Token t) {
    int tokenIndex = t.getTokenIndex();

    Token nextToken = tokens.get(tokenIndex + 1);
    String nextTokenText = nextToken.getText();

    return PATTERN_NOT_SPACE.matcher(nextTokenText).matches();

    //return PATTERN_SPACE.matcher(tokens.get(t.getTokenIndex() + 1).getText()).matches();
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

      // TODO переделать после выполнения issue #371 'Доработки ядра. Хранение информации для квикфиксов'
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
