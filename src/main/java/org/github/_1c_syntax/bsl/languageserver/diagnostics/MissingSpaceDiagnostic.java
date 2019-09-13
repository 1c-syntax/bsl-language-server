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

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenSource;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.github._1c_syntax.bsl.languageserver.configuration.DiagnosticLanguage;
import org.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import org.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import org.github._1c_syntax.bsl.languageserver.providers.CodeActionProvider;
import org.github._1c_syntax.bsl.parser.BSLLexer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR, //TODO Не забыть заменить обратно
  severity = DiagnosticSeverity.BLOCKER,
  /*type = DiagnosticType.CODE_SMELL
  severity = DiagnosticSeverity.INFO,*/
  minutesToFix = 1,
  activatedByDefault = true   //TODO поставить false
)

public class MissingSpaceDiagnostic extends AbstractVisitorDiagnostic  {//

  private static final String symbols_L = ")";        // символы, требующие пробелы только слева
  private static final String symbols_R = ",;";        // символы, требующие пробелы только справа
  //private static final String symbols_LR = "-"; // символы, требующие пробелы слева и справа
  private static final String symbols_LR = "+-*/=%<>"; // символы, требующие пробелы слева и справа

  //TODO Извлечь Pattern.compile в метод
  //private static final Pattern PATTERN_LR = compilePatterm("[\\Q"+ symbols_LR +"\\E]");
  private static final Pattern PATTERN_LR = compilePatterm("[\\Q"+ symbols_LR +"\\E]|(<>)|(<=)|(>=)");
  private static final Pattern PATTERN_R = compilePatterm("[\\Q"+ symbols_R +"\\E]");
  private static final Pattern PATTERN_SPACE = compilePatterm("\\S+");


  @Override
  public List<Diagnostic> getDiagnostics(DocumentContext documentContext) {

    //TODO Задачи
    // 1. Унарные + и -
    //    - Унарным считаем, если перед ним (пропуская пробельные символы) находим + - * / = % < > ( [ , Возврат <> <= >=
    // +2. Дописать тест
    // +3. Справа от запятой может быть запятая
    // 4. Реализовать быстрое исправление
    // 5. Вынести в параметры:
    //    - для запятой есть исключение - справа может быть другая запятая. Сделать параметр
    //        типа "Допускать справа от запятой другую запятую" или "Допускать несколько запятых подряд"
    //    - символы, у которых проверять слева и справа
    //    - символы, у которых проверять только слева
    //    - символы, у которых проверять только справа
    //    - для унарных знаков, параметр, проверять ли справа наличие пробела - по умолчанию проверять

    List<Token> tokens = documentContext.getTokens();


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
      .forEach((Token t) ->
        diagnosticStorage.addDiagnostic(t,
          getDiagnosticMessage(getErrorMessage(2), t.getText())));
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


  private static Pattern compilePatterm(String s) {
    return Pattern.compile(
      s,
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
  }

  private String getErrorMessage(int errCode) {
    //TODO Локализовать сообщения

    LanguageServerConfiguration configuration = LanguageServerConfiguration.create();
    Boolean isRU = configuration.getDiagnosticLanguage() == DiagnosticLanguage.RU;


    String errMessage = "Слева или справа"; // Left or right

    switch (errCode){
      case 1:
        errMessage = "Слева"; // To the left
        break;
      case 2:
        errMessage = "Справа"; // To the right
        break;
      case 3:
        errMessage = "Слева и справа"; // Left and right
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

    return PATTERN_SPACE.matcher(tokens.get(t.getTokenIndex() - 1).getText()).matches();
  }
  private boolean noSpaceRight(List<Token> tokens, Token t) {
    /*
    int tokenIndex = t.getTokenIndex();

    Token nextToken = tokens.get(tokenIndex + 1);
    String nextTokenText = nextToken.getText();

    return PATTERN_SPACE.matcher(nextTokenText).matches();
*/
    // Если это запятая, то допустимо что бы справа от нее была запятая
    if (t.getType() == BSLLexer.COMMA && tokens.get(t.getTokenIndex() + 1).getType() == BSLLexer.COMMA)
      return false;

    return PATTERN_SPACE.matcher(tokens.get(t.getTokenIndex() + 1).getText()).matches();
  }

  private boolean noSpaceLeftAndRight1(List<Token> tokens, Token t) {
    int tokenIndex = t.getTokenIndex();
    Token prevToken = tokens.get(tokenIndex - 1);
    String prevTokenText = prevToken.getText();

    Token nextToken = tokens.get(tokenIndex + 1);
    String nextTokenText = nextToken.getText();

    return PATTERN_SPACE.matcher(prevTokenText).matches()
      ||
      PATTERN_SPACE.matcher(nextTokenText).matches()
      ;

    //return PATTERN_SPACE.matcher(tokens.get(t.getTokenIndex() + 1).getText()).matches();
  }
  private boolean noSpaceRight1(List<Token> tokens, Token t) {
    int tokenIndex = t.getTokenIndex();

    Token nextToken = tokens.get(tokenIndex + 1);
    String nextTokenText = nextToken.getText();

    return PATTERN_SPACE.matcher(nextTokenText).matches();

    //return PATTERN_SPACE.matcher(tokens.get(t.getTokenIndex() + 1).getText()).matches();
  }



/*
  @Override
  public List<CodeAction> getQuickFixes(
    List<Diagnostic> diagnostics,
    CodeActionParams params,
    DocumentContext documentContext
  ) {

    List<TextEdit> textEdits = new ArrayList<>();

    diagnostics.forEach((Diagnostic diagnostic) -> {
      Range range = diagnostic.getRange();
      String originalText = documentContext.getText(range);
      String canonicalText = canonicalStrings.get(originalText.toUpperCase(Locale.ENGLISH));

      TextEdit textEdit = new TextEdit(range, canonicalText);
      textEdits.add(textEdit);
    });

    return CodeActionProvider.createCodeActions(
      textEdits,
      getResourceString("quickFixMessage"),
      documentContext.getUri(),
      diagnostics
    );
  }*/
}
