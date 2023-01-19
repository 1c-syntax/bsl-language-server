/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2023
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Fedkin <nixel2007@gmail.com> and contributors
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

  private static final Map<Integer, List<String>> canonicalKeywords = getPreset();
  private static final Map<String, String> canonicalStrings = getCanonical();

  private static Map<Integer, List<String>> getPreset() {
    // Здесь возможно будет получить набор канонических слов из параметров.
    // Если входных параметров не задано, то используются значения по умолчанию.
    return getDefaultPreset();
  }

  private static Map<Integer, List<String>> getDefaultPreset() {

    Map<Integer, List<String>> result = new HashMap<>();

    result.put(BSLParser.IF_KEYWORD, List.of(Keywords.IF_RU, Keywords.IF_EN));
    result.put(BSLParser.THEN_KEYWORD, List.of(Keywords.THEN_RU, Keywords.THEN_EN));
    result.put(BSLParser.ELSE_KEYWORD, List.of(Keywords.ELSE_RU, Keywords.ELSE_EN));
    result.put(BSLParser.ELSIF_KEYWORD, List.of(Keywords.ELSIF_RU, Keywords.ELSIF_EN));
    result.put(BSLParser.ENDIF_KEYWORD, List.of(Keywords.ENDIF_RU, Keywords.ENDIF_EN));
    result.put(BSLParser.FOR_KEYWORD, List.of(Keywords.FOR_RU, Keywords.FOR_EN));
    result.put(BSLParser.EACH_KEYWORD, List.of(
      Keywords.EACH_RU, Keywords.EACH_LO_RU, Keywords.EACH_EN, Keywords.EACH_LO_EN));
    result.put(BSLParser.IN_KEYWORD, List.of(Keywords.IN_RU, Keywords.IN_EN));
    result.put(BSLParser.DO_KEYWORD, List.of(Keywords.DO_RU, Keywords.DO_EN));
    result.put(BSLParser.WHILE_KEYWORD, List.of(Keywords.WHILE_RU, Keywords.WHILE_EN));
    result.put(BSLParser.BREAK_KEYWORD, List.of(Keywords.BREAK_RU, Keywords.BREAK_EN));
    result.put(BSLParser.CONTINUE_KEYWORD, List.of(Keywords.CONTINUE_RU, Keywords.CONTINUE_EN));
    result.put(BSLParser.ENDDO_KEYWORD, List.of(Keywords.END_DO_RU, Keywords.END_DO_EN));
    result.put(BSLParser.TO_KEYWORD, List.of(Keywords.TO_RU, Keywords.TO_EN));
    result.put(BSLParser.PROCEDURE_KEYWORD, List.of(Keywords.PROCEDURE_RU, Keywords.PROCEDURE_EN));
    result.put(BSLParser.VAL_KEYWORD, List.of(Keywords.VAL_RU, Keywords.VAL_EN));
    result.put(BSLParser.EXPORT_KEYWORD, List.of(Keywords.EXPORT_RU, Keywords.EXPORT_EN));
    result.put(BSLParser.VAR_KEYWORD, List.of(Keywords.VAR_RU, Keywords.VAR_EN));
    result.put(BSLParser.TRY_KEYWORD, List.of(Keywords.TRY_RU, Keywords.TRY_EN));
    result.put(BSLParser.EXECUTE_KEYWORD, List.of(Keywords.EXECUTE_RU, Keywords.EXECUTE_EN));
    result.put(BSLParser.RETURN_KEYWORD, List.of(Keywords.RETURN_RU, Keywords.RETURN_EN));
    result.put(BSLParser.TRUE, List.of(Keywords.TRUE_RU, Keywords.TRUE_EN));
    result.put(BSLParser.EXCEPT_KEYWORD, List.of(Keywords.EXCEPT_RU, Keywords.EXCEPT_EN));
    result.put(BSLParser.RAISE_KEYWORD, List.of(Keywords.RAISE_RU, Keywords.RAISE_EN));
    result.put(BSLParser.ENDTRY_KEYWORD, List.of(Keywords.END_TRY_RU, Keywords.END_TRY_EN));
    result.put(BSLParser.ENDPROCEDURE_KEYWORD, List.of(Keywords.END_PROCEDURE_RU, Keywords.END_PROCEDURE_EN));
    result.put(BSLParser.FUNCTION_KEYWORD, List.of(Keywords.FUNCTION_RU, Keywords.FUNCTION_EN));
    result.put(BSLParser.ENDFUNCTION_KEYWORD, List.of(Keywords.END_FUNCTION_RU, Keywords.END_FUNCTION_EN));
    result.put(BSLParser.FALSE, List.of(Keywords.FALSE_RU, Keywords.FALSE_EN));
    result.put(BSLParser.ADDHANDLER_KEYWORD, List.of(Keywords.ADD_HANDLER_RU, Keywords.ADD_HANDLER_EN));
    result.put(BSLParser.REMOVEHANDLER_KEYWORD, List.of(
      Keywords.REMOVE_HANDLER_RU, Keywords.REMOVE_HANDLER_EN));
    result.put(BSLParser.GOTO_KEYWORD, List.of(Keywords.GOTO_RU, Keywords.GOTO_EN));
    result.put(BSLParser.AND_KEYWORD, List.of(
      Keywords.AND_RU, Keywords.AND_UP_EN, Keywords.AND_EN));
    result.put(BSLParser.OR_KEYWORD, List.of(
      Keywords.OR_RU, Keywords.OR_UP_RU, Keywords.OR_EN, Keywords.OR_UP_EN));
    result.put(BSLParser.NOT_KEYWORD, List.of(
      Keywords.NOT_RU, Keywords.NOT_UP_RU, Keywords.NOT_EN, Keywords.NOT_UP_EN));
    result.put(BSLParser.NEW_KEYWORD, List.of(Keywords.NEW_RU, Keywords.NEW_EN));
    result.put(BSLParser.UNDEFINED, List.of(Keywords.UNDEFINED_RU, Keywords.UNDEFINED_EN));
    result.put(BSLParser.PREPROC_REGION, List.of(Keywords.REGION_RU, Keywords.REGION_EN));
    result.put(BSLParser.PREPROC_END_REGION, List.of(Keywords.ENDREGION_RU, Keywords.ENDREGION_EN));
    result.put(BSLParser.PREPROC_IF_KEYWORD, List.of(Keywords.IF_RU, Keywords.IF_EN));
    result.put(BSLParser.PREPROC_THEN_KEYWORD, List.of(Keywords.THEN_RU, Keywords.THEN_EN));
    result.put(BSLParser.PREPROC_ELSIF_KEYWORD, List.of(Keywords.ELSIF_RU, Keywords.ELSIF_EN));
    result.put(BSLParser.PREPROC_ELSE_KEYWORD, List.of(Keywords.ELSE_RU, Keywords.ELSE_EN));
    result.put(BSLParser.PREPROC_ENDIF_KEYWORD, List.of(Keywords.ENDIF_RU, Keywords.ENDIF_EN));
    result.put(BSLParser.PREPROC_OR_KEYWORD, List.of(
      Keywords.OR_RU, Keywords.OR_UP_RU, Keywords.OR_EN, Keywords.OR_UP_EN));
    result.put(BSLParser.PREPROC_AND_KEYWORD, List.of(
      Keywords.AND_RU, Keywords.AND_EN, Keywords.AND_UP_EN));
    result.put(BSLParser.PREPROC_NOT_KEYWORD, List.of(
      Keywords.NOT_RU, Keywords.NOT_UP_RU, Keywords.NOT_EN, Keywords.NOT_UP_EN));
    result.put(BSLParser.PREPROC_SERVER_SYMBOL, List.of(Keywords.SERVER_RU, Keywords.SERVER_EN));
    result.put(BSLParser.PREPROC_CLIENT_SYMBOL, List.of(Keywords.CLIENT_RU, Keywords.CLIENT_EN));
    result.put(BSLParser.PREPROC_MOBILEAPPCLIENT_SYMBOL, List.of(
      Keywords.MOBILE_APP_CLIENT_RU, Keywords.MOBILE_APP_CLIENT_EN));
    result.put(BSLParser.PREPROC_MOBILEAPPSERVER_SYMBOL, List.of(
      Keywords.MOBILE_APP_SERVER_RU, Keywords.MOBILE_APP_SERVER_EN));
    result.put(BSLParser.PREPROC_MOBILECLIENT_SYMBOL, List.of(
      Keywords.MOBILE_CLIENT_RU, Keywords.MOBILE_CLIENT_EN));
    result.put(BSLParser.PREPROC_THICKCLIENTORDINARYAPPLICATION_SYMBOL, List.of(
      Keywords.THICK_CLIENT_ORDINARY_APPLICATION_RU, Keywords.THICK_CLIENT_ORDINARY_APPLICATION_EN));
    result.put(BSLParser.PREPROC_THICKCLIENTMANAGEDAPPLICATION_SYMBOL, List.of(
      Keywords.THICK_CLIENT_MANAGED_APPLICATION_RU, Keywords.THICK_CLIENT_MANAGED_APPLICATION_EN));
    result.put(BSLParser.PREPROC_EXTERNALCONNECTION_SYMBOL, List.of(
      Keywords.EXTERNAL_CONNECTION_RU, Keywords.EXTERNAL_CONNECTION_EN));
    result.put(BSLParser.PREPROC_THINCLIENT_SYMBOL, List.of(Keywords.THIN_CLIENT_RU, Keywords.THIN_CLIENT_EN));
    result.put(BSLParser.PREPROC_WEBCLIENT_SYMBOL, List.of(Keywords.WEB_CLIENT_RU, Keywords.WEB_CLIENT_EN));
    result.put(BSLParser.PREPROC_ATCLIENT_SYMBOL, List.of(Keywords.AT_CLIENT_RU, Keywords.AT_CLIENT_EN));
    result.put(BSLParser.PREPROC_ATSERVER_SYMBOL, List.of(Keywords.AT_SERVER_RU, Keywords.AT_SERVER_EN));
    result.put(BSLParser.ANNOTATION_ATCLIENT_SYMBOL, List.of(Keywords.AT_CLIENT_RU, Keywords.AT_CLIENT_EN));
    result.put(BSLParser.ANNOTATION_ATSERVER_SYMBOL, List.of(Keywords.AT_SERVER_RU, Keywords.AT_SERVER_EN));
    result.put(BSLParser.ANNOTATION_ATSERVERNOCONTEXT_SYMBOL, List.of(
      Keywords.AT_SERVER_NO_CONTEXT_RU, Keywords.AT_SERVER_NO_CONTEXT_EN));
    result.put(BSLParser.ANNOTATION_ATCLIENTATSERVERNOCONTEXT_SYMBOL, List.of(
      Keywords.AT_CLIENT_AT_SERVER_NO_CONTEXT_RU, Keywords.AT_CLIENT_AT_SERVER_NO_CONTEXT_EN));
    result.put(BSLParser.ANNOTATION_ATCLIENTATSERVER_SYMBOL, List.of(
      Keywords.AT_CLIENT_AT_SERVER_RU, Keywords.AT_CLIENT_AT_SERVER_EN));

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

  protected void check() {
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
