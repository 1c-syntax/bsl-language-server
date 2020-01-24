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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.providers.CodeActionProvider;
import com.github._1c_syntax.bsl.languageserver.utils.Keywords;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.STANDARD
  }
)
public class CanonicalSpellingKeywordsDiagnostic extends AbstractDiagnostic implements QuickFixProvider {

  private static Map<Integer, List<String>> canonicalKeywords = getPreset();
  private static Map<String, String> canonicalStrings = getCanonical();

  public CanonicalSpellingKeywordsDiagnostic(DiagnosticInfo info) {
    super(info);
  }

  private static Map<Integer, List<String>> getPreset() {
    // Здесь возможно будет получить набор канонических слов из параметров.
    // Если входных параметров не задано, то используются значения по умолчанию.
    return getDefaultPreset();
  }

  private static Map<Integer, List<String>> getDefaultPreset() {

    Map<Integer, List<String>> result = new HashMap<>();

    // IF_KEYWORD
    List<String> ifKeywordSpelling = new ArrayList<>();
    ifKeywordSpelling.add(Keywords.IF_RU);
    ifKeywordSpelling.add(Keywords.IF_EN);
    result.put(BSLParser.IF_KEYWORD, ifKeywordSpelling);

    // THEN_KEYWORD
    List<String> thenKeywordSpelling = new ArrayList<>();
    thenKeywordSpelling.add(Keywords.THEN_RU);
    thenKeywordSpelling.add(Keywords.THEN_EN);
    result.put(BSLParser.THEN_KEYWORD, thenKeywordSpelling);

    // ELSE_KEYWORD
    List<String> elseKeywordSpelling = new ArrayList<>();
    elseKeywordSpelling.add(Keywords.ELSE_RU);
    elseKeywordSpelling.add(Keywords.ELSE_EN);
    result.put(BSLParser.ELSE_KEYWORD, elseKeywordSpelling);

    // ELSIF_KEYWORD
    List<String> elsIfKeywordSpelling = new ArrayList<>();
    elsIfKeywordSpelling.add(Keywords.ELSIF_RU);
    elsIfKeywordSpelling.add(Keywords.ELSIF_EN);
    result.put(BSLParser.ELSIF_KEYWORD, elsIfKeywordSpelling);

    // ENDIF_KEYWORD
    List<String> endIfKeywordSpelling = new ArrayList<>();
    endIfKeywordSpelling.add(Keywords.ENDIF_RU);
    endIfKeywordSpelling.add(Keywords.ENDIF_EN);
    result.put(BSLParser.ENDIF_KEYWORD, endIfKeywordSpelling);

    // FOR_KEYWORD
    List<String> forKeywordSpelling = new ArrayList<>();
    forKeywordSpelling.add(Keywords.FOR_RU);
    forKeywordSpelling.add(Keywords.FOR_EN);
    result.put(BSLParser.FOR_KEYWORD, forKeywordSpelling);

    // EACH_KEYWORD
    List<String> eachKeywordSpelling = new ArrayList<>();
    eachKeywordSpelling.add(Keywords.EACH_RU);
    eachKeywordSpelling.add(Keywords.EACH_LO_RU);
    eachKeywordSpelling.add(Keywords.EACH_EN);
    eachKeywordSpelling.add(Keywords.EACH_LO_EN);
    result.put(BSLParser.EACH_KEYWORD, eachKeywordSpelling);

    // IN_KEYWORD
    List<String> inKeywordSpelling = new ArrayList<>();
    inKeywordSpelling.add(Keywords.IN_RU);
    inKeywordSpelling.add(Keywords.IN_EN);
    result.put(BSLParser.IN_KEYWORD, inKeywordSpelling);

    // DO_KEYWORD
    List<String> doKeywordSpelling = new ArrayList<>();
    doKeywordSpelling.add(Keywords.DO_RU);
    doKeywordSpelling.add(Keywords.DO_EN);
    result.put(BSLParser.DO_KEYWORD, doKeywordSpelling);

    // WHILE_KEYWORD
    List<String> whileKeywordSpelling = new ArrayList<>();
    whileKeywordSpelling.add(Keywords.WHILE_RU);
    whileKeywordSpelling.add(Keywords.WHILE_EN);
    result.put(BSLParser.WHILE_KEYWORD, whileKeywordSpelling);

    // BREAK_KEYWORD
    List<String> breakKeywordSpelling = new ArrayList<>();
    breakKeywordSpelling.add(Keywords.BREAK_RU);
    breakKeywordSpelling.add(Keywords.BREAK_EN);
    result.put(BSLParser.BREAK_KEYWORD, breakKeywordSpelling);

    // CONTINUE_KEYWORD
    List<String> continueKeywordSpelling = new ArrayList<>();
    continueKeywordSpelling.add(Keywords.CONTINUE_RU);
    continueKeywordSpelling.add(Keywords.CONTINUE_EN);
    result.put(BSLParser.CONTINUE_KEYWORD, continueKeywordSpelling);

    // ENDDO_KEYWORD
    List<String> endDoKeywordSpelling = new ArrayList<>();
    endDoKeywordSpelling.add(Keywords.END_DO_RU);
    endDoKeywordSpelling.add(Keywords.END_DO_EN);
    result.put(BSLParser.ENDDO_KEYWORD, endDoKeywordSpelling);

    // TO_KEYWORD
    List<String> toKeywordSpelling = new ArrayList<>();
    toKeywordSpelling.add(Keywords.TO_RU);
    toKeywordSpelling.add(Keywords.TO_EN);
    result.put(BSLParser.TO_KEYWORD, toKeywordSpelling);

    // PROCEDURE_KEYWORD
    List<String> procedureKeywordSpelling = new ArrayList<>();
    procedureKeywordSpelling.add(Keywords.PROCEDURE_RU);
    procedureKeywordSpelling.add(Keywords.PROCEDURE_EN);
    result.put(BSLParser.PROCEDURE_KEYWORD, procedureKeywordSpelling);

    // VAL_KEYWORD
    List<String> valKeywordSpelling = new ArrayList<>();
    valKeywordSpelling.add(Keywords.VAL_RU);
    valKeywordSpelling.add(Keywords.VAL_EN);
    result.put(BSLParser.VAL_KEYWORD, valKeywordSpelling);

    // EXPORT_KEYWORD
    List<String> exportKeywordSpelling = new ArrayList<>();
    exportKeywordSpelling.add(Keywords.EXPORT_RU);
    exportKeywordSpelling.add(Keywords.EXPORT_EN);
    result.put(BSLParser.EXPORT_KEYWORD, exportKeywordSpelling);

    // VAR_KEYWORD
    List<String> varKeywordSpelling = new ArrayList<>();
    varKeywordSpelling.add(Keywords.VAR_RU);
    varKeywordSpelling.add(Keywords.VAR_EN);
    result.put(BSLParser.VAR_KEYWORD, varKeywordSpelling);

    // TRY_KEYWORD
    List<String> tryKeywordSpelling = new ArrayList<>();
    tryKeywordSpelling.add(Keywords.TRY_RU);
    tryKeywordSpelling.add(Keywords.TRY_EN);
    result.put(BSLParser.TRY_KEYWORD, tryKeywordSpelling);

    // EXECUTE_KEYWORD
    List<String> executeKeywordSpelling = new ArrayList<>();
    executeKeywordSpelling.add(Keywords.EXECUTE_RU);
    executeKeywordSpelling.add(Keywords.EXECUTE_EN);
    result.put(BSLParser.EXECUTE_KEYWORD, executeKeywordSpelling);

    // RETURN_KEYWORD
    List<String> returnKeywordSpelling = new ArrayList<>();
    returnKeywordSpelling.add(Keywords.RETURN_RU);
    returnKeywordSpelling.add(Keywords.RETURN_EN);
    result.put(BSLParser.RETURN_KEYWORD, returnKeywordSpelling);

    // TRUE
    List<String> trueKeywordSpelling = new ArrayList<>();
    trueKeywordSpelling.add(Keywords.TRUE_RU);
    trueKeywordSpelling.add(Keywords.TRUE_EN);
    result.put(BSLParser.TRUE, trueKeywordSpelling);

    // EXCEPT_KEYWORD
    List<String> exceptKeywordSpelling = new ArrayList<>();
    exceptKeywordSpelling.add(Keywords.EXCEPT_RU);
    exceptKeywordSpelling.add(Keywords.EXCEPT_EN);
    result.put(BSLParser.EXCEPT_KEYWORD, exceptKeywordSpelling);

    // RAISE_KEYWORD
    List<String> raiseKeywordSpelling = new ArrayList<>();
    raiseKeywordSpelling.add(Keywords.RAISE_RU);
    raiseKeywordSpelling.add(Keywords.RAISE_EN);
    result.put(BSLParser.RAISE_KEYWORD, raiseKeywordSpelling);

    // ENDTRY_KEYWORD
    List<String> endTryKeywordSpelling = new ArrayList<>();
    endTryKeywordSpelling.add(Keywords.END_TRY_RU);
    endTryKeywordSpelling.add(Keywords.END_TRY_EN);
    result.put(BSLParser.ENDTRY_KEYWORD, endTryKeywordSpelling);

    // ENDPROCEDURE_KEYWORD
    List<String> endProcedureKeywordSpelling = new ArrayList<>();
    endProcedureKeywordSpelling.add(Keywords.END_PROCEDURE_RU);
    endProcedureKeywordSpelling.add(Keywords.END_PROCEDURE_EN);
    result.put(BSLParser.ENDPROCEDURE_KEYWORD, endProcedureKeywordSpelling);

    // FUNCTION_KEYWORD
    List<String> functionKeywordSpelling = new ArrayList<>();
    functionKeywordSpelling.add(Keywords.FUNCTION_RU);
    functionKeywordSpelling.add(Keywords.FUNCTION_EN);
    result.put(BSLParser.FUNCTION_KEYWORD, functionKeywordSpelling);

    // ENDFUNCTION_KEYWORD
    List<String> endfunctionKeywordSpelling = new ArrayList<>();
    endfunctionKeywordSpelling.add(Keywords.END_FUNCTION_RU);
    endfunctionKeywordSpelling.add(Keywords.END_FUNCTION_EN);
    result.put(BSLParser.ENDFUNCTION_KEYWORD, endfunctionKeywordSpelling);

    // FALSE
    List<String> falseKeywordSpelling = new ArrayList<>();
    falseKeywordSpelling.add(Keywords.FALSE_RU);
    falseKeywordSpelling.add(Keywords.FALSE_EN);
    result.put(BSLParser.FALSE, falseKeywordSpelling);

    // ADDHANDLER_KEYWORD
    List<String> addHandlerKeywordSpelling = new ArrayList<>();
    addHandlerKeywordSpelling.add(Keywords.ADD_HANDLER_RU);
    addHandlerKeywordSpelling.add(Keywords.ADD_HANDLER_EN);
    result.put(BSLParser.ADDHANDLER_KEYWORD, addHandlerKeywordSpelling);

    // REMOVEHANDLER_KEYWORD
    List<String> removeHandlerKeywordSpelling = new ArrayList<>();
    removeHandlerKeywordSpelling.add(Keywords.REMOVE_HANDLER_RU);
    removeHandlerKeywordSpelling.add(Keywords.REMOVE_HANDLER_EN);
    result.put(BSLParser.REMOVEHANDLER_KEYWORD, removeHandlerKeywordSpelling);

    // GOTO_KEYWORD
    List<String> gotoHandlerKeywordSpelling = new ArrayList<>();
    gotoHandlerKeywordSpelling.add(Keywords.GOTO_RU);
    gotoHandlerKeywordSpelling.add(Keywords.GOTO_EN);
    result.put(BSLParser.GOTO_KEYWORD, gotoHandlerKeywordSpelling);

    // AND_KEYWORD
    List<String> andKeywordSpelling = new ArrayList<>();
    andKeywordSpelling.add(Keywords.AND_RU);
    andKeywordSpelling.add(Keywords.AND_UP_EN);
    andKeywordSpelling.add(Keywords.AND_EN);
    result.put(BSLParser.AND_KEYWORD, andKeywordSpelling);

    // OR_KEYWORD
    List<String> orKeywordSpelling = new ArrayList<>();
    orKeywordSpelling.add(Keywords.OR_RU);
    orKeywordSpelling.add(Keywords.OR_UP_RU);
    orKeywordSpelling.add(Keywords.OR_EN);
    orKeywordSpelling.add(Keywords.OR_UP_EN);
    result.put(BSLParser.OR_KEYWORD, orKeywordSpelling);

    // NOT_KEYWORD
    List<String> notKeywordSpelling = new ArrayList<>();
    notKeywordSpelling.add(Keywords.NOT_RU);
    notKeywordSpelling.add(Keywords.NOT_UP_RU);
    notKeywordSpelling.add(Keywords.NOT_EN);
    notKeywordSpelling.add(Keywords.NOT_UP_EN);
    result.put(BSLParser.NOT_KEYWORD, notKeywordSpelling);

    // NEW_KEYWORD
    List<String> newKeywordSpelling = new ArrayList<>();
    newKeywordSpelling.add(Keywords.NEW_RU);
    newKeywordSpelling.add(Keywords.NEW_EN);
    result.put(BSLParser.NEW_KEYWORD, newKeywordSpelling);

    // UNDEFINED
    List<String> undefinedKeywordSpelling = new ArrayList<>();
    undefinedKeywordSpelling.add(Keywords.UNDEFINED_RU);
    undefinedKeywordSpelling.add(Keywords.UNDEFINED_EN);
    result.put(BSLParser.UNDEFINED, undefinedKeywordSpelling);

    // PREPROC_REGION
    List<String> regionKeywordSpelling = new ArrayList<>();
    regionKeywordSpelling.add(Keywords.REGION_RU);
    regionKeywordSpelling.add(Keywords.REGION_EN);
    result.put(BSLParser.PREPROC_REGION, regionKeywordSpelling);

    // PREPROC_END_REGION
    List<String> endRegionKeywordSpelling = new ArrayList<>();
    endRegionKeywordSpelling.add(Keywords.ENDREGION_RU);
    endRegionKeywordSpelling.add(Keywords.ENDREGION_EN);
    result.put(BSLParser.PREPROC_END_REGION, endRegionKeywordSpelling);

    // PREPROC_IF_KEYWORD
    List<String> preprocIfKeywordSpelling = new ArrayList<>();
    preprocIfKeywordSpelling.add(Keywords.IF_RU);
    preprocIfKeywordSpelling.add(Keywords.IF_EN);
    result.put(BSLParser.PREPROC_IF_KEYWORD, preprocIfKeywordSpelling);

    // PREPROC_THEN_KEYWORD
    List<String> preprocThenKeywordSpelling = new ArrayList<>();
    preprocThenKeywordSpelling.add(Keywords.THEN_RU);
    preprocThenKeywordSpelling.add(Keywords.THEN_EN);
    result.put(BSLParser.PREPROC_THEN_KEYWORD, preprocThenKeywordSpelling);

    // PREPROC_ELSIF_KEYWORD
    List<String> preprocElsIfKeywordSpelling = new ArrayList<>();
    preprocElsIfKeywordSpelling.add(Keywords.ELSIF_RU);
    preprocElsIfKeywordSpelling.add(Keywords.ELSIF_EN);
    result.put(BSLParser.PREPROC_ELSIF_KEYWORD, preprocElsIfKeywordSpelling);

    // PREPROC_ELSE_KEYWORD
    List<String> preprocElseKeywordSpelling = new ArrayList<>();
    preprocElseKeywordSpelling.add(Keywords.ELSE_RU);
    preprocElseKeywordSpelling.add(Keywords.ELSE_EN);
    result.put(BSLParser.PREPROC_ELSE_KEYWORD, preprocElseKeywordSpelling);

    // PREPROC_ENDIF_KEYWORD
    List<String> preprocEndIfKeywordSpelling = new ArrayList<>();
    preprocEndIfKeywordSpelling.add(Keywords.ENDIF_RU);
    preprocEndIfKeywordSpelling.add(Keywords.ENDIF_EN);
    result.put(BSLParser.PREPROC_ENDIF_KEYWORD, preprocEndIfKeywordSpelling);

    // PREPROC_OR_KEYWORD
    List<String> preprocOrKeywordSpelling = new ArrayList<>();
    preprocOrKeywordSpelling.add(Keywords.OR_RU);
    preprocOrKeywordSpelling.add(Keywords.OR_UP_RU);
    preprocOrKeywordSpelling.add(Keywords.OR_EN);
    preprocOrKeywordSpelling.add(Keywords.OR_UP_EN);
    result.put(BSLParser.PREPROC_OR_KEYWORD, preprocOrKeywordSpelling);

    // PREPROC_AND_KEYWORD
    List<String> preprocAndKeywordSpelling = new ArrayList<>();
    preprocAndKeywordSpelling.add(Keywords.AND_RU);
    preprocAndKeywordSpelling.add(Keywords.AND_EN);
    preprocAndKeywordSpelling.add(Keywords.AND_UP_EN);
    result.put(BSLParser.PREPROC_AND_KEYWORD, preprocAndKeywordSpelling);

    // PREPROC_NOT_KEYWORD
    List<String> preprocNotKeywordSpelling = new ArrayList<>();
    preprocNotKeywordSpelling.add(Keywords.NOT_RU);
    preprocNotKeywordSpelling.add(Keywords.NOT_UP_RU);
    preprocNotKeywordSpelling.add(Keywords.NOT_EN);
    preprocNotKeywordSpelling.add(Keywords.NOT_UP_EN);
    result.put(BSLParser.PREPROC_NOT_KEYWORD, preprocNotKeywordSpelling);

    // PREPROC_SERVER_SYMBOL
    List<String> preprocServerKeywordSpelling = new ArrayList<>();
    preprocServerKeywordSpelling.add(Keywords.SERVER_RU);
    preprocServerKeywordSpelling.add(Keywords.SERVER_EN);
    result.put(BSLParser.PREPROC_SERVER_SYMBOL, preprocServerKeywordSpelling);

    // PREPROC_CLIENT_SYMBOL
    List<String> preprocClientKeywordSpelling = new ArrayList<>();
    preprocClientKeywordSpelling.add(Keywords.CLIENT_RU);
    preprocClientKeywordSpelling.add(Keywords.CLIENT_EN);
    result.put(BSLParser.PREPROC_CLIENT_SYMBOL, preprocClientKeywordSpelling);

    // PREPROC_MOBILEAPPCLIENT_SYMBOL
    List<String> preprocMobileAppClientKeywordSpelling = new ArrayList<>();
    preprocMobileAppClientKeywordSpelling.add(Keywords.MOBILE_APP_CLIENT_RU);
    preprocMobileAppClientKeywordSpelling.add(Keywords.MOBILE_APP_CLIENT_EN);
    result.put(BSLParser.PREPROC_MOBILEAPPCLIENT_SYMBOL, preprocMobileAppClientKeywordSpelling);

    // PREPROC_MOBILEAPPSERVER_SYMBOL
    List<String> preprocMobileAppServerKeywordSpelling = new ArrayList<>();
    preprocMobileAppServerKeywordSpelling.add(Keywords.MOBILE_APP_SERVER_RU);
    preprocMobileAppServerKeywordSpelling.add(Keywords.MOBILE_APP_SERVER_EN);
    result.put(BSLParser.PREPROC_MOBILEAPPSERVER_SYMBOL, preprocMobileAppServerKeywordSpelling);

    // PREPROC_MOBILECLIENT_SYMBOL
    List<String> preprocMobileClientKeywordSpelling = new ArrayList<>();
    preprocMobileClientKeywordSpelling.add(Keywords.MOBILE_CLIENT_RU);
    preprocMobileClientKeywordSpelling.add(Keywords.MOBILE_CLIENT_EN);
    result.put(BSLParser.PREPROC_MOBILECLIENT_SYMBOL, preprocMobileClientKeywordSpelling);

    // PREPROC_THICKCLIENTORDINARYAPPLICATION_SYMBOL
    List<String> preprocThickClientOrdinaryKeywordSpelling = new ArrayList<>();
    preprocThickClientOrdinaryKeywordSpelling.add(Keywords.THICK_CLIENT_ORDINARY_APPLICATION_RU);
    preprocThickClientOrdinaryKeywordSpelling.add(Keywords.THICK_CLIENT_ORDINARY_APPLICATION_EN);
    result.put(BSLParser.PREPROC_THICKCLIENTORDINARYAPPLICATION_SYMBOL, preprocThickClientOrdinaryKeywordSpelling);

    // PREPROC_THICKCLIENTMANAGEDAPPLICATION_SYMBOL
    List<String> preprocThickClientManagedKeywordSpelling = new ArrayList<>();
    preprocThickClientManagedKeywordSpelling.add(Keywords.THICK_CLIENT_MANAGED_APPLICATION_RU);
    preprocThickClientManagedKeywordSpelling.add(Keywords.THICK_CLIENT_MANAGED_APPLICATION_EN);
    result.put(BSLParser.PREPROC_THICKCLIENTMANAGEDAPPLICATION_SYMBOL, preprocThickClientManagedKeywordSpelling);

    // PREPROC_EXTERNALCONNECTION_SYMBOL
    List<String> preprocExternalConnKeywordSpelling = new ArrayList<>();
    preprocExternalConnKeywordSpelling.add(Keywords.EXTERNAL_CONNECTION_RU);
    preprocExternalConnKeywordSpelling.add(Keywords.EXTERNAL_CONNECTION_EN);
    result.put(BSLParser.PREPROC_EXTERNALCONNECTION_SYMBOL, preprocExternalConnKeywordSpelling);

    // PREPROC_THINCLIENT_SYMBOL
    List<String> preprocThinClientKeywordSpelling = new ArrayList<>();
    preprocThinClientKeywordSpelling.add(Keywords.THIN_CLIENT_RU);
    preprocThinClientKeywordSpelling.add(Keywords.THIN_CLIENT_EN);
    result.put(BSLParser.PREPROC_THINCLIENT_SYMBOL, preprocThinClientKeywordSpelling);

    // PREPROC_WEBCLIENT_SYMBOL
    List<String> preprocWebClientKeywordSpelling = new ArrayList<>();
    preprocWebClientKeywordSpelling.add(Keywords.WEB_CLIENT_RU);
    preprocWebClientKeywordSpelling.add(Keywords.WEB_CLIENT_EN);
    result.put(BSLParser.PREPROC_WEBCLIENT_SYMBOL, preprocWebClientKeywordSpelling);

    // PREPROC_ATCLIENT_SYMBOL
    List<String> preprocAtClientKeywordSpelling = new ArrayList<>();
    preprocAtClientKeywordSpelling.add(Keywords.AT_CLIENT_RU);
    preprocAtClientKeywordSpelling.add(Keywords.AT_CLIENT_EN);
    result.put(BSLParser.PREPROC_ATCLIENT_SYMBOL, preprocAtClientKeywordSpelling);

    // PREPROC_ATSERVER_SYMBOL
    List<String> preprocAtServerKeywordSpelling = new ArrayList<>();
    preprocAtServerKeywordSpelling.add(Keywords.AT_SERVER_RU);
    preprocAtServerKeywordSpelling.add(Keywords.AT_SERVER_EN);
    result.put(BSLParser.PREPROC_ATSERVER_SYMBOL, preprocAtServerKeywordSpelling);

    // ANNOTATION_ATCLIENT_SYMBOL
    List<String> annotAtClientKeywordSpelling = new ArrayList<>();
    annotAtClientKeywordSpelling.add(Keywords.AT_CLIENT_RU);
    annotAtClientKeywordSpelling.add(Keywords.AT_CLIENT_EN);
    result.put(BSLParser.ANNOTATION_ATCLIENT_SYMBOL, annotAtClientKeywordSpelling);

    // ANNOTATION_ATSERVER_SYMBOL_SYMBOL
    List<String> annotAtServerKeywordSpelling = new ArrayList<>();
    annotAtServerKeywordSpelling.add(Keywords.AT_SERVER_RU);
    annotAtServerKeywordSpelling.add(Keywords.AT_SERVER_EN);
    result.put(BSLParser.ANNOTATION_ATSERVER_SYMBOL, annotAtServerKeywordSpelling);

    // ANNOTATION_ATSERVERNOCONTEXT_SYMBOL
    List<String> annotAtServerNoContextKeywordSpelling = new ArrayList<>();
    annotAtServerNoContextKeywordSpelling.add(Keywords.AT_SERVER_NO_CONTEXT_RU);
    annotAtServerNoContextKeywordSpelling.add(Keywords.AT_SERVER_NO_CONTEXT_EN);
    result.put(BSLParser.ANNOTATION_ATSERVERNOCONTEXT_SYMBOL, annotAtServerNoContextKeywordSpelling);

    // ANNOTATION_ATCLIENTATSERVERNOCONTEXT_SYMBOL
    List<String> annotAtClientServerNoContextKeywordSpelling = new ArrayList<>();
    annotAtClientServerNoContextKeywordSpelling.add(Keywords.AT_CLIENT_AT_SERVER_NO_CONTEXT_RU);
    annotAtClientServerNoContextKeywordSpelling.add(Keywords.AT_CLIENT_AT_SERVER_NO_CONTEXT_EN);
    result.put(BSLParser.ANNOTATION_ATCLIENTATSERVERNOCONTEXT_SYMBOL, annotAtClientServerNoContextKeywordSpelling);

    // ANNOTATION_ATCLIENTATSERVER_SYMBOL
    List<String> annotAtClientServerKeywordSpelling = new ArrayList<>();
    annotAtClientServerKeywordSpelling.add(Keywords.AT_CLIENT_AT_SERVER_RU);
    annotAtClientServerKeywordSpelling.add(Keywords.AT_CLIENT_AT_SERVER_EN);
    result.put(BSLParser.ANNOTATION_ATCLIENTATSERVER_SYMBOL, annotAtClientServerKeywordSpelling);

    return result;
  }

  private static Map<String, String> getCanonical() {

    Map<String, String> result = new HashMap<>();

    result.put(Keywords.IF_UP_RU, Keywords.IF_RU);
    result.put(Keywords.IF_UP_EN, Keywords.IF_EN);
    result.put(Keywords.THEN_UP_RU, Keywords.THEN_RU);
    result.put(Keywords.THEN_UP_EN, Keywords.THEN_EN);
    result.put(Keywords.ELSE_UP_RU, Keywords.ELSE_RU);
    result.put(Keywords.ELSE_UP_EN, Keywords.ELSE_EN);
    result.put(Keywords.ELSIF_UP_RU, Keywords.ELSIF_RU);
    result.put(Keywords.ELSIF_UP_EN, Keywords.ELSIF_EN);
    result.put(Keywords.ENDIF_UP_RU, Keywords.ENDIF_RU);
    result.put(Keywords.ENDIF_UP_EN, Keywords.ENDIF_EN);
    result.put(Keywords.FOR_UP_RU, Keywords.FOR_RU);
    result.put(Keywords.FOR_UP_EN, Keywords.FOR_EN);
    result.put(Keywords.EACH_UP_RU, Keywords.EACH_RU);
    result.put(Keywords.EACH_UP_EN, Keywords.EACH_EN);
    result.put(Keywords.IN_UP_RU, Keywords.IN_RU);
    result.put(Keywords.IN_UP_EN, Keywords.IN_EN);
    result.put(Keywords.DO_UP_RU, Keywords.DO_RU);
    result.put(Keywords.DO_UP_EN, Keywords.DO_EN);
    result.put(Keywords.WHILE_UP_RU, Keywords.WHILE_RU);
    result.put(Keywords.WHILE_UP_EN, Keywords.WHILE_EN);
    result.put(Keywords.BREAK_UP_RU, Keywords.BREAK_RU);
    result.put(Keywords.BREAK_UP_EN, Keywords.BREAK_EN);
    result.put(Keywords.CONTINUE_UP_RU, Keywords.CONTINUE_RU);
    result.put(Keywords.CONTINUE_UP_EN, Keywords.CONTINUE_EN);
    result.put(Keywords.END_DO_UP_RU, Keywords.END_DO_RU);
    result.put(Keywords.END_DO_UP_EN, Keywords.END_DO_EN);
    result.put(Keywords.TO_UP_RU, Keywords.TO_RU);
    result.put(Keywords.TO_UP_EN, Keywords.TO_EN);
    result.put(Keywords.PROCEDURE_UP_RU, Keywords.PROCEDURE_RU);
    result.put(Keywords.PROCEDURE_UP_EN, Keywords.PROCEDURE_EN);
    result.put(Keywords.VAL_UP_RU, Keywords.VAL_RU);
    result.put(Keywords.VAL_UP_EN, Keywords.VAL_EN);
    result.put(Keywords.EXPORT_UP_RU, Keywords.EXPORT_RU);
    result.put(Keywords.EXPORT_UP_EN, Keywords.EXPORT_EN);
    result.put(Keywords.VAR_UP_RU, Keywords.VAR_RU);
    result.put(Keywords.VAR_UP_EN, Keywords.VAR_EN);
    result.put(Keywords.TRY_UP_RU, Keywords.TRY_RU);
    result.put(Keywords.TRY_UP_EN, Keywords.TRY_EN);
    result.put(Keywords.EXECUTE_UP_RU, Keywords.EXECUTE_RU);
    result.put(Keywords.EXECUTE_UP_EN, Keywords.EXECUTE_EN);
    result.put(Keywords.RETURN_UP_RU, Keywords.RETURN_RU);
    result.put(Keywords.RETURN_UP_EN, Keywords.RETURN_EN);
    result.put(Keywords.TRUE_UP_RU, Keywords.TRUE_RU);
    result.put(Keywords.TRUE_UP_EN, Keywords.TRUE_EN);
    result.put(Keywords.EXCEPT_UP_RU, Keywords.EXCEPT_RU);
    result.put(Keywords.EXCEPT_UP_EN, Keywords.EXCEPT_EN);
    result.put(Keywords.RAISE_UP_RU, Keywords.RAISE_RU);
    result.put(Keywords.RAISE_UP_EN, Keywords.RAISE_EN);
    result.put(Keywords.END_TRY_UP_RU, Keywords.END_TRY_RU);
    result.put(Keywords.END_TRY_UP_EN, Keywords.END_TRY_EN);
    result.put(Keywords.END_PROCEDURE_UP_RU, Keywords.END_PROCEDURE_RU);
    result.put(Keywords.END_PROCEDURE_UP_EN, Keywords.END_PROCEDURE_EN);
    result.put(Keywords.FUNCTION_UP_RU, Keywords.FUNCTION_RU);
    result.put(Keywords.FUNCTION_UP_EN, Keywords.FUNCTION_EN);
    result.put(Keywords.END_FUNCTION_UP_RU, Keywords.END_FUNCTION_RU);
    result.put(Keywords.END_FUNCTION_UP_EN, Keywords.END_FUNCTION_EN);
    result.put(Keywords.FALSE_UP_RU, Keywords.FALSE_RU);
    result.put(Keywords.FALSE_UP_EN, Keywords.FALSE_EN);
    result.put(Keywords.ADD_HANDLER_UP_RU, Keywords.ADD_HANDLER_RU);
    result.put(Keywords.ADD_HANDLER_UP_EN, Keywords.ADD_HANDLER_EN);
    result.put(Keywords.REMOVE_HANDLER_UP_RU, Keywords.REMOVE_HANDLER_RU);
    result.put(Keywords.REMOVE_HANDLER_UP_EN, Keywords.REMOVE_HANDLER_EN);
    result.put(Keywords.GOTO_UP_RU, Keywords.GOTO_RU);
    result.put(Keywords.GOTO_UP_EN, Keywords.GOTO_EN);
    result.put(Keywords.AND_RU, Keywords.AND_RU);
    result.put(Keywords.AND_UP_EN, Keywords.AND_UP_EN);
    result.put(Keywords.OR_UP_RU, Keywords.OR_UP_RU);
    result.put(Keywords.OR_UP_EN, Keywords.OR_UP_EN);
    result.put(Keywords.NOT_UP_RU, Keywords.NOT_UP_RU);
    result.put(Keywords.NOT_UP_EN, Keywords.NOT_UP_EN);
    result.put(Keywords.NEW_UP_RU, Keywords.NEW_RU);
    result.put(Keywords.NEW_UP_EN, Keywords.NEW_EN);
    result.put(Keywords.UNDEFINED_UP_RU, Keywords.UNDEFINED_RU);
    result.put(Keywords.UNDEFINED_UP_EN, Keywords.UNDEFINED_EN);
    result.put(Keywords.REGION_UP_RU, Keywords.REGION_RU);
    result.put(Keywords.REGION_UP_EN, Keywords.REGION_EN);
    result.put(Keywords.ENDREGION_UP_RU, Keywords.ENDREGION_RU);
    result.put(Keywords.ENDREGION_UP_EN, Keywords.ENDREGION_EN);
    result.put(Keywords.SERVER_UP_RU, Keywords.SERVER_RU);
    result.put(Keywords.SERVER_UP_EN, Keywords.SERVER_EN);
    result.put(Keywords.CLIENT_UP_RU, Keywords.CLIENT_RU);
    result.put(Keywords.CLIENT_UP_EN, Keywords.CLIENT_EN);
    result.put(Keywords.MOBILE_APP_CLIENT_UP_RU, Keywords.MOBILE_APP_CLIENT_RU);
    result.put(Keywords.MOBILE_APP_CLIENT_UP_EN, Keywords.MOBILE_APP_CLIENT_EN);
    result.put(Keywords.MOBILE_APP_SERVER_UP_RU, Keywords.MOBILE_APP_SERVER_RU);
    result.put(Keywords.MOBILE_APP_SERVER_UP_EN, Keywords.MOBILE_APP_SERVER_EN);
    result.put(Keywords.MOBILE_CLIENT_UP_RU, Keywords.MOBILE_CLIENT_RU);
    result.put(Keywords.MOBILE_CLIENT_UP_EN, Keywords.MOBILE_CLIENT_EN);
    result.put(Keywords.THICK_CLIENT_ORDINARY_APPLICATION_UP_RU, Keywords.THICK_CLIENT_ORDINARY_APPLICATION_RU);
    result.put(Keywords.THICK_CLIENT_ORDINARY_APPLICATION_UP_EN, Keywords.THICK_CLIENT_ORDINARY_APPLICATION_EN);
    result.put(Keywords.THICK_CLIENT_MANAGED_APPLICATION_UP_RU, Keywords.THICK_CLIENT_MANAGED_APPLICATION_RU);
    result.put(Keywords.THICK_CLIENT_MANAGED_APPLICATION_UP_EN, Keywords.THICK_CLIENT_MANAGED_APPLICATION_EN);
    result.put(Keywords.EXTERNAL_CONNECTION_UP_RU, Keywords.EXTERNAL_CONNECTION_RU);
    result.put(Keywords.EXTERNAL_CONNECTION_UP_EN, Keywords.EXTERNAL_CONNECTION_EN);
    result.put(Keywords.THIN_CLIENT_UP_RU, Keywords.THIN_CLIENT_RU);
    result.put(Keywords.THIN_CLIENT_UP_EN, Keywords.THIN_CLIENT_EN);
    result.put(Keywords.WEB_CLIENT_UP_RU, Keywords.WEB_CLIENT_RU);
    result.put(Keywords.WEB_CLIENT_UP_EN, Keywords.WEB_CLIENT_EN);
    result.put(Keywords.AT_CLIENT_UP_RU, Keywords.AT_CLIENT_RU);
    result.put(Keywords.AT_CLIENT_UP_EN, Keywords.AT_CLIENT_EN);
    result.put(Keywords.AT_SERVER_UP_RU, Keywords.AT_SERVER_RU);
    result.put(Keywords.AT_SERVER_UP_EN, Keywords.AT_SERVER_EN);
    result.put(Keywords.AT_SERVER_NO_CONTEXT_UP_RU, Keywords.AT_SERVER_NO_CONTEXT_RU);
    result.put(Keywords.AT_SERVER_NO_CONTEXT_UP_EN, Keywords.AT_SERVER_NO_CONTEXT_EN);
    result.put(Keywords.AT_CLIENT_AT_SERVER_NO_CONTEXT_UP_RU, Keywords.AT_CLIENT_AT_SERVER_NO_CONTEXT_RU);
    result.put(Keywords.AT_CLIENT_AT_SERVER_NO_CONTEXT_UP_EN, Keywords.AT_CLIENT_AT_SERVER_NO_CONTEXT_EN);
    result.put(Keywords.AT_CLIENT_AT_SERVER_UP_RU, Keywords.AT_CLIENT_AT_SERVER_RU);
    result.put(Keywords.AT_CLIENT_AT_SERVER_UP_EN, Keywords.AT_CLIENT_AT_SERVER_EN);

    return result;
  }

  protected void check(DocumentContext documentContext) {
    documentContext.getTokensFromDefaultChannel()
      .parallelStream()
      .filter((Token t) ->
        canonicalKeywords.get(t.getType()) != null &&
          !canonicalKeywords.get(t.getType()).contains(t.getText()))
      .forEach(token ->
        diagnosticStorage.addDiagnostic(
          token,
          info.getMessage(token.getText())
        )
      );
  }

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

      if (canonicalText != null) {
        TextEdit textEdit = new TextEdit(range, canonicalText);
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
