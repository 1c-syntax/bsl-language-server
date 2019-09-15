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
import org.github._1c_syntax.bsl.languageserver.configuration.DiagnosticLanguage;
import org.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
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

  private static final String symbols_L = ")";        // символы, требующие пробелы только слева
  private static final String symbols_R = ",;";        // символы, требующие пробелы только справа
//  private static final String symbols_LR = "-"; // символы, требующие пробелы слева и справа
//  private static final String DEFAULT_SYMBOLS_LR = "+ - * / = % < >"; // символы, требующие пробелы слева и справа
  private static final String default_listForCheckLeftAndRight = "+ - * / = % < > <> <= >="; // символы, требующие пробелы слева и справа
//  private static final String DEFAULT_checkSpacesLeftAndRight = "+-*/=%<>"; // символы, требующие пробелы слева и справа
  private static final String DEFAULT_checkSpaceToRightOfUnary = "false";

  @DiagnosticParameter(
    type = String.class,
    defaultValue = "" + default_listForCheckLeftAndRight,
    description = "Список проверяемых символов через пробел (пробелы с обоих сторон). Например: + - * / = % < >"
  )
//  private static String SYMBOLS_LR = DEFAULT_SYMBOLS_LR.trim().replace(" ", "");
  private static String listForCheckLeftAndRight = getRegularString(default_listForCheckLeftAndRight);

  @DiagnosticParameter(
    type = String.class,
    defaultValue = "" + DEFAULT_checkSpaceToRightOfUnary,
    description = "Проверять наличие пробела справа от унарных знаков (+ -)"
  )
  private static Boolean checkSpaceToRightOfUnary = DEFAULT_checkSpaceToRightOfUnary == "true";



  private static String getRegularString(String string) {

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


  //TODO Извлечь Pattern.compile в метод
  //private static final Pattern PATTERN_LR = compilePatterm("[\\Q"+ symbols_LR +"\\E]");
  //private static Pattern PATTERN_LR = compilePatterm("[\\Q"+ checkSpacesLeftAndRight +"\\E]|(<>)|(<=)|(>=)");
  private static Pattern PATTERN_LR = compilePattern(listForCheckLeftAndRight);
  private static final Pattern PATTERN_R = compilePattern("[\\Q"+ symbols_R +"\\E]");
  private static final Pattern PATTERN_NOT_SPACE = compilePattern("\\S+");


  @Override
  public void configure(Map<String, Object> configuration) {
    if (configuration == null) {
      return;
    }

    String SYMBOLS_LR_String = (String) configuration.get("listForCheckLeftAndRight");
    this.listForCheckLeftAndRight = getRegularString(SYMBOLS_LR_String);
    PATTERN_LR = compilePattern(listForCheckLeftAndRight);

    String Enable_chech_Unary = (String) configuration.get("checkSpaceToRightOfUnary");
    this.checkSpaceToRightOfUnary = Enable_chech_Unary == "true";

  }

  @Override
  public List<Diagnostic> getDiagnostics(DocumentContext documentContext) {

    //TODO Задачи
    // 1. Унарные + и -
    //    - Унарным считаем, если перед ним (пропуская пробельные символы) находим + - * / = % < > ( [ , Возврат <> <= >=
    // +2. Дописать тест
    // +3. Справа от запятой может быть запятая
    // +4. Реализовать быстрое исправление
    // 5. Вынести в параметры:
    //    - для запятой есть исключение - справа может быть другая запятая. Сделать параметр
    //        типа "Допускать справа от запятой другую запятую" или "Разрешить несколько запятых подряд"
    //    - символы, у которых проверять слева и справа
    //    - символы, у которых проверять только слева
    //    - символы, у которых проверять только справа
    //    - для унарных знаков, параметр, проверять ли справа наличие пробела - по умолчанию проверять
    //        типа "Проверять наличие пробела справа от унарных знаков"

    List<Token> tokens = documentContext.getTokens();

    //LanguageServerConfiguration configuration = LanguageServerConfiguration.create();
    //Boolean isRU = configuration.getDiagnosticLanguage() == DiagnosticLanguage.RU;

    // проверяем слева и справа
    //Вариант с одним проходом, но без указания где пропущено
/*    tokens.stream()
      .filter((Token t) ->
        PATTERN_LR.matcher( t.getText() ).matches())
      .filter((Token t) ->
        noSpaceLeft(tokens, t) || noSpaceRight(tokens, t))
      .forEach((Token t) ->
        diagnosticStorage.addDiagnostic(t));*/

    List<Token> tokensLoR = tokens
      .parallelStream()
      .filter((Token t) -> PATTERN_LR.matcher( t.getText() ).matches()).collect(Collectors.toList());


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

    /*
    tokens.stream()
      .filter((Token t) -> PATTERN_LR.matcher( t.getText() ).matches())
      .filter((Token t) -> noSpaceLeft(tokens, t) & !noSpaceRight(tokens, t))
      .forEach((Token t) ->
        diagnosticStorage.addDiagnostic(t,
          getDiagnosticMessage(getErrorMessage(1), t.getText())));

    tokens.stream()
      .filter((Token t) -> PATTERN_LR.matcher( t.getText() ).matches())
      .filter((Token t) -> !noSpaceLeft(tokens, t) & noSpaceRight(tokens, t))
      .forEach((Token t) ->
        diagnosticStorage.addDiagnostic(t,
          getDiagnosticMessage(getErrorMessage(2), t.getText())));

    tokens.stream()
      .filter((Token t) -> PATTERN_LR.matcher( t.getText() ).matches())
      .filter((Token t) -> noSpaceLeft(tokens, t) & noSpaceRight(tokens, t))
      .forEach((Token t) ->
        diagnosticStorage.addDiagnostic(t,
          getDiagnosticMessage(getErrorMessage(3), t.getText())));
*/

    // проверяем справа
    List<Token> tokensR = tokens
      .parallelStream()
      .filter((Token t) -> PATTERN_R.matcher( t.getText() ).matches()).collect(Collectors.toList());

    tokensR.stream()
      .filter((Token t) -> noSpaceRight(tokens, t))
      .forEach((Token t) ->{

          /*List<DiagnosticRelatedInformation> relatedInformation = new ArrayList<>();
          String relatedMessage = getDiagnosticMessage(getErrorMessage(2), t.getText());
          relatedInformation.add(RangeHelper.createRelatedInformation(
            documentContext.getUri(),
            RangeHelper.newRange(t.getLine()-1, t.getTokenIndex(), t.getLine()-1, t.getTokenIndex()),
            relatedMessage
          ));

        diagnosticStorage.addDiagnostic(t, relatedInformation);*/

        diagnosticStorage.addDiagnostic(t, getDiagnosticMessage(getErrorMessage(2), t.getText()));
      });

    /*
    tokens.stream()
      .filter((Token t) -> PATTERN_R.matcher( t.getText() ).matches())
      .filter((Token t) -> noSpaceRight(tokens, t))
      .forEach((Token t) ->
        diagnosticStorage.addDiagnostic(t,
         getDiagnosticMessage(getErrorMessage(2), t.getText())));
    */


    /*
    Stream<Token> tokens3 = tokens.stream()
      .filter((Token t) ->
        PATTERN_R.matcher( t.getText() ).matches());

    Stream<Token> tokens4 = tokens3.filter((Token t) ->
      noSpaceRight(tokens, t)
    );
    tokens4.forEach((Token t) ->
      diagnosticStorage.addDiagnostic(t));
*/

    /*documentContext.getTokens()
      .parallelStream()
      .filter((Token t) ->
        PATTERN_LR.matcher(t.getText()).matches())
      .forEach((Token t) ->
        diagnosticStorage.addDiagnostic(t));*/

    return diagnosticStorage.getDiagnostics();
  }


  private static Pattern compilePattern(String s) {
    return Pattern.compile(
      s,
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
  }

  private String getErrorMessage(int errCode) {
    //TODO Локализовать сообщения

    LanguageServerConfiguration configuration = LanguageServerConfiguration.create();
    Boolean isRU = configuration.getDiagnosticLanguage() == DiagnosticLanguage.RU;


    String errMessage = isRU ? "Слева или справа" : "Left or right";

    switch (errCode){
      case 1:
        errMessage = isRU ? "Слева" : "To the left";
        break;
      case 2:
        errMessage = isRU ? "Справа" : "To the right";
        break;
      case 3:
        errMessage = isRU ? "Слева и справа" : "Left and right";
        break;
      default:
        break;
    }

    return errMessage;
  }

  private boolean noSpaceLeft(List<Token> tokens, Token t) {
    /*
    int tokenIndex = t.getTokenIndex();

    Token prevToken = tokens.get(tokenIndex - 1);
    String prevTokenText = prevToken.getText();

    return PATTERN_SPACE.matcher(prevTokenText).matches();
*/

    return PATTERN_NOT_SPACE.matcher(tokens.get(t.getTokenIndex() - 1).getText()).matches();
  }
  private boolean noSpaceRight(List<Token> tokens, Token t) {
    /*
    int tokenIndex = t.getTokenIndex();

    Token nextToken = tokens.get(tokenIndex + 1);
    String nextTokenText = nextToken.getText();

    return PATTERN_SPACE.matcher(nextTokenText).matches();
*/

    // Если это унарный + или - то пробел справа проверяем в соответствии с параметром
    if (t.getType() == BSLLexer.PLUS || t.getType() == BSLLexer.MINUS){
      // Надо понять что они унарные
      if (isUnaryChar(tokens, t) && !checkSpaceToRightOfUnary) // TODO false - если настройка выключена
        return false;
    }

    // Если это запятая, то допустимо что бы справа от нее была запятая
    if (t.getType() == BSLLexer.COMMA && tokens.get(t.getTokenIndex() + 1).getType() == BSLLexer.COMMA)
      return false;

    return PATTERN_NOT_SPACE.matcher(tokens.get(t.getTokenIndex() + 1).getText()).matches();
  }

  private boolean isUnaryChar(List<Token> tokens, Token t) {
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
      Boolean missedLeft = diagnosticMessage.contains("слева"); // TODO локализовать
      Boolean missedRight = diagnosticMessage.contains("справа");

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
