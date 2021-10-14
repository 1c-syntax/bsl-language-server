/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2021
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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.providers.CodeActionProvider;
import com.github._1c_syntax.bsl.languageserver.utils.BSLKeywords;
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

    result.put(BSLParser.IF_KEYWORD, List.of(BSLKeywords.IF_RU, BSLKeywords.IF_EN));
    result.put(BSLParser.THEN_KEYWORD, List.of(BSLKeywords.THEN_RU, BSLKeywords.THEN_EN));
    result.put(BSLParser.ELSE_KEYWORD, List.of(BSLKeywords.ELSE_RU, BSLKeywords.ELSE_EN));
    result.put(BSLParser.ELSIF_KEYWORD, List.of(BSLKeywords.ELSIF_RU, BSLKeywords.ELSIF_EN));
    result.put(BSLParser.ENDIF_KEYWORD, List.of(BSLKeywords.ENDIF_RU, BSLKeywords.ENDIF_EN));
    result.put(BSLParser.FOR_KEYWORD, List.of(BSLKeywords.FOR_RU, BSLKeywords.FOR_EN));
    result.put(BSLParser.EACH_KEYWORD, List.of(
      BSLKeywords.EACH_RU, BSLKeywords.EACH_LO_RU, BSLKeywords.EACH_EN, BSLKeywords.EACH_LO_EN));
    result.put(BSLParser.IN_KEYWORD, List.of(BSLKeywords.IN_RU, BSLKeywords.IN_EN));
    result.put(BSLParser.DO_KEYWORD, List.of(BSLKeywords.DO_RU, BSLKeywords.DO_EN));
    result.put(BSLParser.WHILE_KEYWORD, List.of(BSLKeywords.WHILE_RU, BSLKeywords.WHILE_EN));
    result.put(BSLParser.BREAK_KEYWORD, List.of(BSLKeywords.BREAK_RU, BSLKeywords.BREAK_EN));
    result.put(BSLParser.CONTINUE_KEYWORD, List.of(BSLKeywords.CONTINUE_RU, BSLKeywords.CONTINUE_EN));
    result.put(BSLParser.ENDDO_KEYWORD, List.of(BSLKeywords.END_DO_RU, BSLKeywords.END_DO_EN));
    result.put(BSLParser.TO_KEYWORD, List.of(BSLKeywords.TO_RU, BSLKeywords.TO_EN));
    result.put(BSLParser.PROCEDURE_KEYWORD, List.of(BSLKeywords.PROCEDURE_RU, BSLKeywords.PROCEDURE_EN));
    result.put(BSLParser.VAL_KEYWORD, List.of(BSLKeywords.VAL_RU, BSLKeywords.VAL_EN));
    result.put(BSLParser.EXPORT_KEYWORD, List.of(BSLKeywords.EXPORT_RU, BSLKeywords.EXPORT_EN));
    result.put(BSLParser.VAR_KEYWORD, List.of(BSLKeywords.VAR_RU, BSLKeywords.VAR_EN));
    result.put(BSLParser.TRY_KEYWORD, List.of(BSLKeywords.TRY_RU, BSLKeywords.TRY_EN));
    result.put(BSLParser.EXECUTE_KEYWORD, List.of(BSLKeywords.EXECUTE_RU, BSLKeywords.EXECUTE_EN));
    result.put(BSLParser.RETURN_KEYWORD, List.of(BSLKeywords.RETURN_RU, BSLKeywords.RETURN_EN));
    result.put(BSLParser.TRUE, List.of(BSLKeywords.TRUE_RU, BSLKeywords.TRUE_EN));
    result.put(BSLParser.EXCEPT_KEYWORD, List.of(BSLKeywords.EXCEPT_RU, BSLKeywords.EXCEPT_EN));
    result.put(BSLParser.RAISE_KEYWORD, List.of(BSLKeywords.RAISE_RU, BSLKeywords.RAISE_EN));
    result.put(BSLParser.ENDTRY_KEYWORD, List.of(BSLKeywords.END_TRY_RU, BSLKeywords.END_TRY_EN));
    result.put(BSLParser.ENDPROCEDURE_KEYWORD, List.of(BSLKeywords.END_PROCEDURE_RU, BSLKeywords.END_PROCEDURE_EN));
    result.put(BSLParser.FUNCTION_KEYWORD, List.of(BSLKeywords.FUNCTION_RU, BSLKeywords.FUNCTION_EN));
    result.put(BSLParser.ENDFUNCTION_KEYWORD, List.of(BSLKeywords.END_FUNCTION_RU, BSLKeywords.END_FUNCTION_EN));
    result.put(BSLParser.FALSE, List.of(BSLKeywords.FALSE_RU, BSLKeywords.FALSE_EN));
    result.put(BSLParser.ADDHANDLER_KEYWORD, List.of(BSLKeywords.ADD_HANDLER_RU, BSLKeywords.ADD_HANDLER_EN));
    result.put(BSLParser.REMOVEHANDLER_KEYWORD, List.of(
      BSLKeywords.REMOVE_HANDLER_RU, BSLKeywords.REMOVE_HANDLER_EN));
    result.put(BSLParser.GOTO_KEYWORD, List.of(BSLKeywords.GOTO_RU, BSLKeywords.GOTO_EN));
    result.put(BSLParser.AND_KEYWORD, List.of(
      BSLKeywords.AND_RU, BSLKeywords.AND_UP_EN, BSLKeywords.AND_EN));
    result.put(BSLParser.OR_KEYWORD, List.of(
      BSLKeywords.OR_RU, BSLKeywords.OR_UP_RU, BSLKeywords.OR_EN, BSLKeywords.OR_UP_EN));
    result.put(BSLParser.NOT_KEYWORD, List.of(
      BSLKeywords.NOT_RU, BSLKeywords.NOT_UP_RU, BSLKeywords.NOT_EN, BSLKeywords.NOT_UP_EN));
    result.put(BSLParser.NEW_KEYWORD, List.of(BSLKeywords.NEW_RU, BSLKeywords.NEW_EN));
    result.put(BSLParser.UNDEFINED, List.of(BSLKeywords.UNDEFINED_RU, BSLKeywords.UNDEFINED_EN));
    result.put(BSLParser.PREPROC_REGION, List.of(BSLKeywords.REGION_RU, BSLKeywords.REGION_EN));
    result.put(BSLParser.PREPROC_END_REGION, List.of(BSLKeywords.ENDREGION_RU, BSLKeywords.ENDREGION_EN));
    result.put(BSLParser.PREPROC_IF_KEYWORD, List.of(BSLKeywords.IF_RU, BSLKeywords.IF_EN));
    result.put(BSLParser.PREPROC_THEN_KEYWORD, List.of(BSLKeywords.THEN_RU, BSLKeywords.THEN_EN));
    result.put(BSLParser.PREPROC_ELSIF_KEYWORD, List.of(BSLKeywords.ELSIF_RU, BSLKeywords.ELSIF_EN));
    result.put(BSLParser.PREPROC_ELSE_KEYWORD, List.of(BSLKeywords.ELSE_RU, BSLKeywords.ELSE_EN));
    result.put(BSLParser.PREPROC_ENDIF_KEYWORD, List.of(BSLKeywords.ENDIF_RU, BSLKeywords.ENDIF_EN));
    result.put(BSLParser.PREPROC_OR_KEYWORD, List.of(
      BSLKeywords.OR_RU, BSLKeywords.OR_UP_RU, BSLKeywords.OR_EN, BSLKeywords.OR_UP_EN));
    result.put(BSLParser.PREPROC_AND_KEYWORD, List.of(
      BSLKeywords.AND_RU, BSLKeywords.AND_EN, BSLKeywords.AND_UP_EN));
    result.put(BSLParser.PREPROC_NOT_KEYWORD, List.of(
      BSLKeywords.NOT_RU, BSLKeywords.NOT_UP_RU, BSLKeywords.NOT_EN, BSLKeywords.NOT_UP_EN));
    result.put(BSLParser.PREPROC_SERVER_SYMBOL, List.of(BSLKeywords.SERVER_RU, BSLKeywords.SERVER_EN));
    result.put(BSLParser.PREPROC_CLIENT_SYMBOL, List.of(BSLKeywords.CLIENT_RU, BSLKeywords.CLIENT_EN));
    result.put(BSLParser.PREPROC_MOBILEAPPCLIENT_SYMBOL, List.of(
      BSLKeywords.MOBILE_APP_CLIENT_RU, BSLKeywords.MOBILE_APP_CLIENT_EN));
    result.put(BSLParser.PREPROC_MOBILEAPPSERVER_SYMBOL, List.of(
      BSLKeywords.MOBILE_APP_SERVER_RU, BSLKeywords.MOBILE_APP_SERVER_EN));
    result.put(BSLParser.PREPROC_MOBILECLIENT_SYMBOL, List.of(
      BSLKeywords.MOBILE_CLIENT_RU, BSLKeywords.MOBILE_CLIENT_EN));
    result.put(BSLParser.PREPROC_THICKCLIENTORDINARYAPPLICATION_SYMBOL, List.of(
      BSLKeywords.THICK_CLIENT_ORDINARY_APPLICATION_RU, BSLKeywords.THICK_CLIENT_ORDINARY_APPLICATION_EN));
    result.put(BSLParser.PREPROC_THICKCLIENTMANAGEDAPPLICATION_SYMBOL, List.of(
      BSLKeywords.THICK_CLIENT_MANAGED_APPLICATION_RU, BSLKeywords.THICK_CLIENT_MANAGED_APPLICATION_EN));
    result.put(BSLParser.PREPROC_EXTERNALCONNECTION_SYMBOL, List.of(
      BSLKeywords.EXTERNAL_CONNECTION_RU, BSLKeywords.EXTERNAL_CONNECTION_EN));
    result.put(BSLParser.PREPROC_THINCLIENT_SYMBOL, List.of(BSLKeywords.THIN_CLIENT_RU, BSLKeywords.THIN_CLIENT_EN));
    result.put(BSLParser.PREPROC_WEBCLIENT_SYMBOL, List.of(BSLKeywords.WEB_CLIENT_RU, BSLKeywords.WEB_CLIENT_EN));
    result.put(BSLParser.PREPROC_ATCLIENT_SYMBOL, List.of(BSLKeywords.AT_CLIENT_RU, BSLKeywords.AT_CLIENT_EN));
    result.put(BSLParser.PREPROC_ATSERVER_SYMBOL, List.of(BSLKeywords.AT_SERVER_RU, BSLKeywords.AT_SERVER_EN));
    result.put(BSLParser.ANNOTATION_ATCLIENT_SYMBOL, List.of(BSLKeywords.AT_CLIENT_RU, BSLKeywords.AT_CLIENT_EN));
    result.put(BSLParser.ANNOTATION_ATSERVER_SYMBOL, List.of(BSLKeywords.AT_SERVER_RU, BSLKeywords.AT_SERVER_EN));
    result.put(BSLParser.ANNOTATION_ATSERVERNOCONTEXT_SYMBOL, List.of(
      BSLKeywords.AT_SERVER_NO_CONTEXT_RU, BSLKeywords.AT_SERVER_NO_CONTEXT_EN));
    result.put(BSLParser.ANNOTATION_ATCLIENTATSERVERNOCONTEXT_SYMBOL, List.of(
      BSLKeywords.AT_CLIENT_AT_SERVER_NO_CONTEXT_RU, BSLKeywords.AT_CLIENT_AT_SERVER_NO_CONTEXT_EN));
    result.put(BSLParser.ANNOTATION_ATCLIENTATSERVER_SYMBOL, List.of(
      BSLKeywords.AT_CLIENT_AT_SERVER_RU, BSLKeywords.AT_CLIENT_AT_SERVER_EN));

    return result;
  }

  private static Map<String, String> getCanonical() {
    Map<String, String> result = new HashMap<>();

    result.put(BSLKeywords.IF_UP_RU, BSLKeywords.IF_RU);
    result.put(BSLKeywords.IF_UP_EN, BSLKeywords.IF_EN);
    result.put(BSLKeywords.THEN_UP_RU, BSLKeywords.THEN_RU);
    result.put(BSLKeywords.THEN_UP_EN, BSLKeywords.THEN_EN);
    result.put(BSLKeywords.ELSE_UP_RU, BSLKeywords.ELSE_RU);
    result.put(BSLKeywords.ELSE_UP_EN, BSLKeywords.ELSE_EN);
    result.put(BSLKeywords.ELSIF_UP_RU, BSLKeywords.ELSIF_RU);
    result.put(BSLKeywords.ELSIF_UP_EN, BSLKeywords.ELSIF_EN);
    result.put(BSLKeywords.ENDIF_UP_RU, BSLKeywords.ENDIF_RU);
    result.put(BSLKeywords.ENDIF_UP_EN, BSLKeywords.ENDIF_EN);
    result.put(BSLKeywords.FOR_UP_RU, BSLKeywords.FOR_RU);
    result.put(BSLKeywords.FOR_UP_EN, BSLKeywords.FOR_EN);
    result.put(BSLKeywords.EACH_UP_RU, BSLKeywords.EACH_RU);
    result.put(BSLKeywords.EACH_UP_EN, BSLKeywords.EACH_EN);
    result.put(BSLKeywords.IN_UP_RU, BSLKeywords.IN_RU);
    result.put(BSLKeywords.IN_UP_EN, BSLKeywords.IN_EN);
    result.put(BSLKeywords.DO_UP_RU, BSLKeywords.DO_RU);
    result.put(BSLKeywords.DO_UP_EN, BSLKeywords.DO_EN);
    result.put(BSLKeywords.WHILE_UP_RU, BSLKeywords.WHILE_RU);
    result.put(BSLKeywords.WHILE_UP_EN, BSLKeywords.WHILE_EN);
    result.put(BSLKeywords.BREAK_UP_RU, BSLKeywords.BREAK_RU);
    result.put(BSLKeywords.BREAK_UP_EN, BSLKeywords.BREAK_EN);
    result.put(BSLKeywords.CONTINUE_UP_RU, BSLKeywords.CONTINUE_RU);
    result.put(BSLKeywords.CONTINUE_UP_EN, BSLKeywords.CONTINUE_EN);
    result.put(BSLKeywords.END_DO_UP_RU, BSLKeywords.END_DO_RU);
    result.put(BSLKeywords.END_DO_UP_EN, BSLKeywords.END_DO_EN);
    result.put(BSLKeywords.TO_UP_RU, BSLKeywords.TO_RU);
    result.put(BSLKeywords.TO_UP_EN, BSLKeywords.TO_EN);
    result.put(BSLKeywords.PROCEDURE_UP_RU, BSLKeywords.PROCEDURE_RU);
    result.put(BSLKeywords.PROCEDURE_UP_EN, BSLKeywords.PROCEDURE_EN);
    result.put(BSLKeywords.VAL_UP_RU, BSLKeywords.VAL_RU);
    result.put(BSLKeywords.VAL_UP_EN, BSLKeywords.VAL_EN);
    result.put(BSLKeywords.EXPORT_UP_RU, BSLKeywords.EXPORT_RU);
    result.put(BSLKeywords.EXPORT_UP_EN, BSLKeywords.EXPORT_EN);
    result.put(BSLKeywords.VAR_UP_RU, BSLKeywords.VAR_RU);
    result.put(BSLKeywords.VAR_UP_EN, BSLKeywords.VAR_EN);
    result.put(BSLKeywords.TRY_UP_RU, BSLKeywords.TRY_RU);
    result.put(BSLKeywords.TRY_UP_EN, BSLKeywords.TRY_EN);
    result.put(BSLKeywords.EXECUTE_UP_RU, BSLKeywords.EXECUTE_RU);
    result.put(BSLKeywords.EXECUTE_UP_EN, BSLKeywords.EXECUTE_EN);
    result.put(BSLKeywords.RETURN_UP_RU, BSLKeywords.RETURN_RU);
    result.put(BSLKeywords.RETURN_UP_EN, BSLKeywords.RETURN_EN);
    result.put(BSLKeywords.TRUE_UP_RU, BSLKeywords.TRUE_RU);
    result.put(BSLKeywords.TRUE_UP_EN, BSLKeywords.TRUE_EN);
    result.put(BSLKeywords.EXCEPT_UP_RU, BSLKeywords.EXCEPT_RU);
    result.put(BSLKeywords.EXCEPT_UP_EN, BSLKeywords.EXCEPT_EN);
    result.put(BSLKeywords.RAISE_UP_RU, BSLKeywords.RAISE_RU);
    result.put(BSLKeywords.RAISE_UP_EN, BSLKeywords.RAISE_EN);
    result.put(BSLKeywords.END_TRY_UP_RU, BSLKeywords.END_TRY_RU);
    result.put(BSLKeywords.END_TRY_UP_EN, BSLKeywords.END_TRY_EN);
    result.put(BSLKeywords.END_PROCEDURE_UP_RU, BSLKeywords.END_PROCEDURE_RU);
    result.put(BSLKeywords.END_PROCEDURE_UP_EN, BSLKeywords.END_PROCEDURE_EN);
    result.put(BSLKeywords.FUNCTION_UP_RU, BSLKeywords.FUNCTION_RU);
    result.put(BSLKeywords.FUNCTION_UP_EN, BSLKeywords.FUNCTION_EN);
    result.put(BSLKeywords.END_FUNCTION_UP_RU, BSLKeywords.END_FUNCTION_RU);
    result.put(BSLKeywords.END_FUNCTION_UP_EN, BSLKeywords.END_FUNCTION_EN);
    result.put(BSLKeywords.FALSE_UP_RU, BSLKeywords.FALSE_RU);
    result.put(BSLKeywords.FALSE_UP_EN, BSLKeywords.FALSE_EN);
    result.put(BSLKeywords.ADD_HANDLER_UP_RU, BSLKeywords.ADD_HANDLER_RU);
    result.put(BSLKeywords.ADD_HANDLER_UP_EN, BSLKeywords.ADD_HANDLER_EN);
    result.put(BSLKeywords.REMOVE_HANDLER_UP_RU, BSLKeywords.REMOVE_HANDLER_RU);
    result.put(BSLKeywords.REMOVE_HANDLER_UP_EN, BSLKeywords.REMOVE_HANDLER_EN);
    result.put(BSLKeywords.GOTO_UP_RU, BSLKeywords.GOTO_RU);
    result.put(BSLKeywords.GOTO_UP_EN, BSLKeywords.GOTO_EN);
    result.put(BSLKeywords.AND_RU, BSLKeywords.AND_RU);
    result.put(BSLKeywords.AND_UP_EN, BSLKeywords.AND_UP_EN);
    result.put(BSLKeywords.OR_UP_RU, BSLKeywords.OR_UP_RU);
    result.put(BSLKeywords.OR_UP_EN, BSLKeywords.OR_UP_EN);
    result.put(BSLKeywords.NOT_UP_RU, BSLKeywords.NOT_UP_RU);
    result.put(BSLKeywords.NOT_UP_EN, BSLKeywords.NOT_UP_EN);
    result.put(BSLKeywords.NEW_UP_RU, BSLKeywords.NEW_RU);
    result.put(BSLKeywords.NEW_UP_EN, BSLKeywords.NEW_EN);
    result.put(BSLKeywords.UNDEFINED_UP_RU, BSLKeywords.UNDEFINED_RU);
    result.put(BSLKeywords.UNDEFINED_UP_EN, BSLKeywords.UNDEFINED_EN);
    result.put(BSLKeywords.REGION_UP_RU, BSLKeywords.REGION_RU);
    result.put(BSLKeywords.REGION_UP_EN, BSLKeywords.REGION_EN);
    result.put(BSLKeywords.ENDREGION_UP_RU, BSLKeywords.ENDREGION_RU);
    result.put(BSLKeywords.ENDREGION_UP_EN, BSLKeywords.ENDREGION_EN);
    result.put(BSLKeywords.SERVER_UP_RU, BSLKeywords.SERVER_RU);
    result.put(BSLKeywords.SERVER_UP_EN, BSLKeywords.SERVER_EN);
    result.put(BSLKeywords.CLIENT_UP_RU, BSLKeywords.CLIENT_RU);
    result.put(BSLKeywords.CLIENT_UP_EN, BSLKeywords.CLIENT_EN);
    result.put(BSLKeywords.MOBILE_APP_CLIENT_UP_RU, BSLKeywords.MOBILE_APP_CLIENT_RU);
    result.put(BSLKeywords.MOBILE_APP_CLIENT_UP_EN, BSLKeywords.MOBILE_APP_CLIENT_EN);
    result.put(BSLKeywords.MOBILE_APP_SERVER_UP_RU, BSLKeywords.MOBILE_APP_SERVER_RU);
    result.put(BSLKeywords.MOBILE_APP_SERVER_UP_EN, BSLKeywords.MOBILE_APP_SERVER_EN);
    result.put(BSLKeywords.MOBILE_CLIENT_UP_RU, BSLKeywords.MOBILE_CLIENT_RU);
    result.put(BSLKeywords.MOBILE_CLIENT_UP_EN, BSLKeywords.MOBILE_CLIENT_EN);
    result.put(BSLKeywords.THICK_CLIENT_ORDINARY_APPLICATION_UP_RU, BSLKeywords.THICK_CLIENT_ORDINARY_APPLICATION_RU);
    result.put(BSLKeywords.THICK_CLIENT_ORDINARY_APPLICATION_UP_EN, BSLKeywords.THICK_CLIENT_ORDINARY_APPLICATION_EN);
    result.put(BSLKeywords.THICK_CLIENT_MANAGED_APPLICATION_UP_RU, BSLKeywords.THICK_CLIENT_MANAGED_APPLICATION_RU);
    result.put(BSLKeywords.THICK_CLIENT_MANAGED_APPLICATION_UP_EN, BSLKeywords.THICK_CLIENT_MANAGED_APPLICATION_EN);
    result.put(BSLKeywords.EXTERNAL_CONNECTION_UP_RU, BSLKeywords.EXTERNAL_CONNECTION_RU);
    result.put(BSLKeywords.EXTERNAL_CONNECTION_UP_EN, BSLKeywords.EXTERNAL_CONNECTION_EN);
    result.put(BSLKeywords.THIN_CLIENT_UP_RU, BSLKeywords.THIN_CLIENT_RU);
    result.put(BSLKeywords.THIN_CLIENT_UP_EN, BSLKeywords.THIN_CLIENT_EN);
    result.put(BSLKeywords.WEB_CLIENT_UP_RU, BSLKeywords.WEB_CLIENT_RU);
    result.put(BSLKeywords.WEB_CLIENT_UP_EN, BSLKeywords.WEB_CLIENT_EN);
    result.put(BSLKeywords.AT_CLIENT_UP_RU, BSLKeywords.AT_CLIENT_RU);
    result.put(BSLKeywords.AT_CLIENT_UP_EN, BSLKeywords.AT_CLIENT_EN);
    result.put(BSLKeywords.AT_SERVER_UP_RU, BSLKeywords.AT_SERVER_RU);
    result.put(BSLKeywords.AT_SERVER_UP_EN, BSLKeywords.AT_SERVER_EN);
    result.put(BSLKeywords.AT_SERVER_NO_CONTEXT_UP_RU, BSLKeywords.AT_SERVER_NO_CONTEXT_RU);
    result.put(BSLKeywords.AT_SERVER_NO_CONTEXT_UP_EN, BSLKeywords.AT_SERVER_NO_CONTEXT_EN);
    result.put(BSLKeywords.AT_CLIENT_AT_SERVER_NO_CONTEXT_UP_RU, BSLKeywords.AT_CLIENT_AT_SERVER_NO_CONTEXT_RU);
    result.put(BSLKeywords.AT_CLIENT_AT_SERVER_NO_CONTEXT_UP_EN, BSLKeywords.AT_CLIENT_AT_SERVER_NO_CONTEXT_EN);
    result.put(BSLKeywords.AT_CLIENT_AT_SERVER_UP_RU, BSLKeywords.AT_CLIENT_AT_SERVER_RU);
    result.put(BSLKeywords.AT_CLIENT_AT_SERVER_UP_EN, BSLKeywords.AT_CLIENT_AT_SERVER_EN);
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
