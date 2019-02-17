/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com>
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
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.github._1c_syntax.bsl.languageserver.utils.RangeHelper;
import org.github._1c_syntax.bsl.parser.BSLParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CanonicalSpellingKeywordsDiagnostic implements BSLDiagnostic {

  private static  Map<Integer, List<String>> canonicalKeywords = getPreset();

  private static Map<Integer, List<String>> getPreset() {
    // Здесь возможно будет получить набор канонических слов из параметров.
    // Если входных параметров не задано, то используются значения по умолчанию.
    Map<Integer, List<String>> result = new HashMap<>();
    if (true)
      result = getDefaultPreset();
    return result;
  }

  private static Map<Integer, List<String>> getDefaultPreset(){

    Map<Integer, List<String>> result = new HashMap<>();

    // IF_KEYWORD
    List<String> ifKeywordSpelling = new ArrayList<>();
    ifKeywordSpelling.add("Если");
    ifKeywordSpelling.add("If");
    result.put(BSLParser.IF_KEYWORD, ifKeywordSpelling);

    // THEN_KEYWORD
    List<String> thenKeywordSpelling = new ArrayList<>();
    thenKeywordSpelling.add("Тогда");
    thenKeywordSpelling.add("Then");
    result.put(BSLParser.THEN_KEYWORD, thenKeywordSpelling);

    // ELSE_KEYWORD
    List<String> elseKeywordSpelling = new ArrayList<>();
    elseKeywordSpelling.add("Иначе");
    elseKeywordSpelling.add("Else");
    result.put(BSLParser.ELSE_KEYWORD, elseKeywordSpelling);

    // ELSIF_KEYWORD
    List<String> elsIfKeywordSpelling = new ArrayList<>();
    elsIfKeywordSpelling.add("ИначеЕсли");
    elsIfKeywordSpelling.add("ElsIf");
    result.put(BSLParser.ELSIF_KEYWORD, elsIfKeywordSpelling);

    // ENDIF_KEYWORD
    List<String> endIfKeywordSpelling = new ArrayList<>();
    endIfKeywordSpelling.add("КонецЕсли");
    endIfKeywordSpelling.add("EndIf");
    result.put(BSLParser.ENDIF_KEYWORD, endIfKeywordSpelling);

    // FOR_KEYWORD
    List<String> forKeywordSpelling = new ArrayList<>();
    forKeywordSpelling.add("Для");
    forKeywordSpelling.add("For");
    result.put(BSLParser.FOR_KEYWORD, forKeywordSpelling);

    // EACH_KEYWORD
    List<String> eachKeywordSpelling = new ArrayList<>();
    eachKeywordSpelling.add("Каждого");
    eachKeywordSpelling.add("каждого");
    eachKeywordSpelling.add("Each");
    eachKeywordSpelling.add("each");
    result.put(BSLParser.EACH_KEYWORD, eachKeywordSpelling);

    // IN_KEYWORD
    List<String> inKeywordSpelling = new ArrayList<>();
    inKeywordSpelling.add("Из");
    inKeywordSpelling.add("In");
    result.put(BSLParser.IN_KEYWORD, inKeywordSpelling);

    // DO_KEYWORD
    List<String> doKeywordSpelling = new ArrayList<>();
    doKeywordSpelling.add("Цикл");
    doKeywordSpelling.add("Do");
    result.put(BSLParser.DO_KEYWORD, doKeywordSpelling);

    // WHILE_KEYWORD
    List<String> whileKeywordSpelling = new ArrayList<>();
    whileKeywordSpelling.add("Пока");
    whileKeywordSpelling.add("While");
    result.put(BSLParser.WHILE_KEYWORD, whileKeywordSpelling);

    // BREAK_KEYWORD
    List<String> breakKeywordSpelling = new ArrayList<>();
    breakKeywordSpelling.add("Прервать");
    breakKeywordSpelling.add("Break");
    result.put(BSLParser.BREAK_KEYWORD, breakKeywordSpelling);

    // CONTINUE_KEYWORD
    List<String> continueKeywordSpelling = new ArrayList<>();
    continueKeywordSpelling.add("Продолжить");
    continueKeywordSpelling.add("Continue");
    result.put(BSLParser.CONTINUE_KEYWORD, continueKeywordSpelling);

    // ENDDO_KEYWORD
    List<String> endDoKeywordSpelling = new ArrayList<>();
    endDoKeywordSpelling.add("КонецЦикла");
    endDoKeywordSpelling.add("EndDo");
    result.put(BSLParser.ENDDO_KEYWORD, endDoKeywordSpelling);

    // TO_KEYWORD
    List<String> toKeywordSpelling = new ArrayList<>();
    toKeywordSpelling.add("По");
    toKeywordSpelling.add("To");
    result.put(BSLParser.TO_KEYWORD, toKeywordSpelling);

    // PROCEDURE_KEYWORD
    List<String> procedureKeywordSpelling = new ArrayList<>();
    procedureKeywordSpelling.add("Процедура");
    procedureKeywordSpelling.add("Procedure");
    result.put(BSLParser.PROCEDURE_KEYWORD, procedureKeywordSpelling);

    // VAL_KEYWORD
    List<String> valKeywordSpelling = new ArrayList<>();
    valKeywordSpelling.add("Знач");
    valKeywordSpelling.add("Val");
    result.put(BSLParser.VAL_KEYWORD, valKeywordSpelling);

    // EXPORT_KEYWORD
    List<String> exportKeywordSpelling = new ArrayList<>();
    exportKeywordSpelling.add("Экспорт");
    exportKeywordSpelling.add("Export");
    result.put(BSLParser.EXPORT_KEYWORD, exportKeywordSpelling);

    // VAR_KEYWORD
    List<String> varKeywordSpelling = new ArrayList<>();
    varKeywordSpelling.add("Перем");
    varKeywordSpelling.add("Var");
    result.put(BSLParser.VAR_KEYWORD, varKeywordSpelling);

    // TRY_KEYWORD
    List<String> tryKeywordSpelling = new ArrayList<>();
    tryKeywordSpelling.add("Попытка");
    tryKeywordSpelling.add("Try");
    result.put(BSLParser.TRY_KEYWORD, tryKeywordSpelling);

    // EXECUTE_KEYWORD
    List<String> executeKeywordSpelling = new ArrayList<>();
    executeKeywordSpelling.add("Выполнить");
    executeKeywordSpelling.add("Execute");
    result.put(BSLParser.EXECUTE_KEYWORD, executeKeywordSpelling);

    // RETURN_KEYWORD
    List<String> returnKeywordSpelling = new ArrayList<>();
    returnKeywordSpelling.add("Возврат");
    returnKeywordSpelling.add("Return");
    result.put(BSLParser.RETURN_KEYWORD, returnKeywordSpelling);

    // TRUE
    List<String> trueKeywordSpelling = new ArrayList<>();
    trueKeywordSpelling.add("Истина");
    trueKeywordSpelling.add("True");
    result.put(BSLParser.TRUE, trueKeywordSpelling);

    // EXCEPT_KEYWORD
    List<String> exceptKeywordSpelling = new ArrayList<>();
    exceptKeywordSpelling.add("Исключение");
    exceptKeywordSpelling.add("Except");
    result.put(BSLParser.EXCEPT_KEYWORD, exceptKeywordSpelling);

    // RAISE_KEYWORD
    List<String> raiseKeywordSpelling = new ArrayList<>();
    raiseKeywordSpelling.add("ВызватьИсключение");
    raiseKeywordSpelling.add("Raise");
    result.put(BSLParser.RAISE_KEYWORD, raiseKeywordSpelling);

    // ENDTRY_KEYWORD
    List<String> endTryKeywordSpelling = new ArrayList<>();
    endTryKeywordSpelling.add("КонецПопытки");
    endTryKeywordSpelling.add("EndTry");
    result.put(BSLParser.ENDTRY_KEYWORD, endTryKeywordSpelling);

    // ENDPROCEDURE_KEYWORD
    List<String> endProcedureKeywordSpelling = new ArrayList<>();
    endProcedureKeywordSpelling.add("КонецПроцедуры");
    endProcedureKeywordSpelling.add("EndProcedure");
    result.put(BSLParser.ENDPROCEDURE_KEYWORD, endProcedureKeywordSpelling);

    // FUNCTION_KEYWORD
    List<String> functionKeywordSpelling = new ArrayList<>();
    functionKeywordSpelling.add("Функция");
    functionKeywordSpelling.add("Function");
    result.put(BSLParser.FUNCTION_KEYWORD, functionKeywordSpelling);

    // ENDFUNCTION_KEYWORD
    List<String> endfunctionKeywordSpelling = new ArrayList<>();
    endfunctionKeywordSpelling.add("КонецФункции");
    endfunctionKeywordSpelling.add("EndFunction");
    result.put(BSLParser.ENDFUNCTION_KEYWORD, endfunctionKeywordSpelling);

    // FALSE
    List<String> falseKeywordSpelling = new ArrayList<>();
    falseKeywordSpelling.add("Ложь");
    falseKeywordSpelling.add("False");
    result.put(BSLParser.FALSE, falseKeywordSpelling);

    // ADDHANDLER_KEYWORD
    List<String> addHandlerKeywordSpelling = new ArrayList<>();
    addHandlerKeywordSpelling.add("ДобавитьОбработчик");
    addHandlerKeywordSpelling.add("AddHandler");
    result.put(BSLParser.ADDHANDLER_KEYWORD, addHandlerKeywordSpelling);

    // REMOVEHANDLER_KEYWORD
    List<String> removeHandlerKeywordSpelling = new ArrayList<>();
    removeHandlerKeywordSpelling.add("УдалитьОбработчик");
    removeHandlerKeywordSpelling.add("RemoveHandler");
    result.put(BSLParser.REMOVEHANDLER_KEYWORD, removeHandlerKeywordSpelling);

    // GOTO_KEYWORD
    List<String> gotoHandlerKeywordSpelling = new ArrayList<>();
    gotoHandlerKeywordSpelling.add("Перейти");
    gotoHandlerKeywordSpelling.add("Goto");
    result.put(BSLParser.GOTO_KEYWORD, gotoHandlerKeywordSpelling);

    // AND_KEYWORD
    List<String> andKeywordSpelling = new ArrayList<>();
    andKeywordSpelling.add("И");
    andKeywordSpelling.add("AND");
    andKeywordSpelling.add("And");
    result.put(BSLParser.AND_KEYWORD, andKeywordSpelling);

    // OR_KEYWORD
    List<String> orKeywordSpelling = new ArrayList<>();
    orKeywordSpelling.add("Или");
    orKeywordSpelling.add("ИЛИ");
    orKeywordSpelling.add("Or");
    orKeywordSpelling.add("OR");
    result.put(BSLParser.OR_KEYWORD, orKeywordSpelling);

    // NOT_KEYWORD
    List<String> notKeywordSpelling = new ArrayList<>();
    notKeywordSpelling.add("Не");
    notKeywordSpelling.add("НЕ");
    notKeywordSpelling.add("Not");
    notKeywordSpelling.add("NOT");
    result.put(BSLParser.NOT_KEYWORD, notKeywordSpelling);

    // NEW_KEYWORD
    List<String> newKeywordSpelling = new ArrayList<>();
    newKeywordSpelling.add("Новый");
    newKeywordSpelling.add("New");
    result.put(BSLParser.NEW_KEYWORD, newKeywordSpelling);

    // UNDEFINED
    List<String> undefinedKeywordSpelling = new ArrayList<>();
    undefinedKeywordSpelling.add("Неопределено");
    undefinedKeywordSpelling.add("Undefined");
    result.put(BSLParser.UNDEFINED, undefinedKeywordSpelling);

    return result;
  }

  @Override
  public DiagnosticSeverity getSeverity() {
    return DiagnosticSeverity.Warning;
  }

  @Override
  public List<Diagnostic> getDiagnostics(BSLParser.FileContext fileTree) {

    List<Token> keywords = fileTree.getTokens()
      .parallelStream()
      .filter((Token t) ->
        canonicalKeywords.get(t.getType()) != null &&
          canonicalKeywords.get(t.getType()).contains(t.getText()) == false)
      .collect(Collectors.toList());

    List<Diagnostic> diagnostics = new ArrayList<>();

    for (Token token : keywords) {
        diagnostics.add(BSLDiagnostic.createDiagnostic(
          this,
          RangeHelper.newRange(
            token.getLine() - 1,
            token.getCharPositionInLine(),
            token.getLine() - 1,
            token.getCharPositionInLine() + token.getText().length() - 1),
          getDiagnosticMessage(token)));
    }

    return diagnostics;

  }

  private String getDiagnosticMessage(Token token) {
    String diagnosticMessage = getDiagnosticMessage();
    return String.format(diagnosticMessage, token.getText());
  }

}
