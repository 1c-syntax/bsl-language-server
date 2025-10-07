/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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

    result.put(BSLParser.IF_KEYWORD, List.of(Keywords.IF.getRu(), Keywords.IF.getEn()));
    result.put(BSLParser.THEN_KEYWORD, List.of(Keywords.THEN.getRu(), Keywords.THEN.getEn()));
    result.put(BSLParser.ELSE_KEYWORD, List.of(Keywords.ELSE.getRu(), Keywords.ELSE.getEn()));
    result.put(BSLParser.ELSIF_KEYWORD, List.of(Keywords.ELSIF.getRu(), Keywords.ELSIF.getEn()));
    result.put(BSLParser.ENDIF_KEYWORD, List.of(Keywords.ENDIF.getRu(), Keywords.ENDIF.getEn()));
    result.put(BSLParser.FOR_KEYWORD, List.of(Keywords.FOR.getRu(), Keywords.FOR.getEn()));
    result.put(BSLParser.EACH_KEYWORD, List.of(
      Keywords.EACH.getRu(), Keywords.EACH_LO.getRu(), Keywords.EACH.getEn(), Keywords.EACH_LO.getEn()));
    result.put(BSLParser.IN_KEYWORD, List.of(Keywords.IN.getRu(), Keywords.IN.getEn()));
    result.put(BSLParser.DO_KEYWORD, List.of(Keywords.DO.getRu(), Keywords.DO.getEn()));
    result.put(BSLParser.WHILE_KEYWORD, List.of(Keywords.WHILE.getRu(), Keywords.WHILE.getEn()));
    result.put(BSLParser.BREAK_KEYWORD, List.of(Keywords.BREAK.getRu(), Keywords.BREAK.getEn()));
    result.put(BSLParser.CONTINUE_KEYWORD, List.of(Keywords.CONTINUE.getRu(), Keywords.CONTINUE.getEn()));
    result.put(BSLParser.ENDDO_KEYWORD, List.of(Keywords.END_DO.getRu(), Keywords.END_DO.getEn()));
    result.put(BSLParser.TO_KEYWORD, List.of(Keywords.TO.getRu(), Keywords.TO.getEn()));
    result.put(BSLParser.PROCEDURE_KEYWORD, List.of(Keywords.PROCEDURE.getRu(), Keywords.PROCEDURE.getEn()));
    result.put(BSLParser.VAL_KEYWORD, List.of(Keywords.VAL.getRu(), Keywords.VAL.getEn()));
    result.put(BSLParser.EXPORT_KEYWORD, List.of(Keywords.EXPORT.getRu(), Keywords.EXPORT.getEn()));
    result.put(BSLParser.VAR_KEYWORD, List.of(Keywords.VAR.getRu(), Keywords.VAR.getEn()));
    result.put(BSLParser.TRY_KEYWORD, List.of(Keywords.TRY.getRu(), Keywords.TRY.getEn()));
    result.put(BSLParser.EXECUTE_KEYWORD, List.of(Keywords.EXECUTE.getRu(), Keywords.EXECUTE.getEn()));
    result.put(BSLParser.RETURN_KEYWORD, List.of(Keywords.RETURN.getRu(), Keywords.RETURN.getEn()));
    result.put(BSLParser.TRUE, List.of(Keywords.TRUE.getRu(), Keywords.TRUE.getEn()));
    result.put(BSLParser.EXCEPT_KEYWORD, List.of(Keywords.EXCEPT.getRu(), Keywords.EXCEPT.getEn()));
    result.put(BSLParser.RAISE_KEYWORD, List.of(Keywords.RAISE.getRu(), Keywords.RAISE.getEn()));
    result.put(BSLParser.ENDTRY_KEYWORD, List.of(Keywords.END_TRY.getRu(), Keywords.END_TRY.getEn()));
    result.put(BSLParser.ENDPROCEDURE_KEYWORD, List.of(Keywords.END_PROCEDURE.getRu(), Keywords.END_PROCEDURE.getEn()));
    result.put(BSLParser.FUNCTION_KEYWORD, List.of(Keywords.FUNCTION.getRu(), Keywords.FUNCTION.getEn()));
    result.put(BSLParser.ENDFUNCTION_KEYWORD, List.of(Keywords.END_FUNCTION.getRu(), Keywords.END_FUNCTION.getEn()));
    result.put(BSLParser.FALSE, List.of(Keywords.FALSE.getRu(), Keywords.FALSE.getEn()));
    result.put(BSLParser.ADDHANDLER_KEYWORD, List.of(Keywords.ADD_HANDLER.getRu(), Keywords.ADD_HANDLER.getEn()));
    result.put(BSLParser.REMOVEHANDLER_KEYWORD, List.of(
      Keywords.REMOVE_HANDLER.getRu(), Keywords.REMOVE_HANDLER.getEn()));
    result.put(BSLParser.GOTO_KEYWORD, List.of(Keywords.GOTO.getRu(), Keywords.GOTO.getEn()));
    result.put(BSLParser.AND_KEYWORD, List.of(
      Keywords.AND.getRu(), Keywords.AND_UP.getEn(), Keywords.AND.getEn()));
    result.put(BSLParser.OR_KEYWORD, List.of(
      Keywords.OR.getRu(), Keywords.OR_UP.getRu(), Keywords.OR.getEn(), Keywords.OR_UP.getEn()));
    result.put(BSLParser.NOT_KEYWORD, List.of(
      Keywords.NOT.getRu(), Keywords.NOT_UP.getRu(), Keywords.NOT.getEn(), Keywords.NOT_UP.getEn()));
    result.put(BSLParser.NEW_KEYWORD, List.of(Keywords.NEW.getRu(), Keywords.NEW.getEn()));
    result.put(BSLParser.UNDEFINED, List.of(Keywords.UNDEFINED.getRu(), Keywords.UNDEFINED.getEn()));
    result.put(BSLParser.PREPROC_REGION, List.of(Keywords.REGION.getRu(), Keywords.REGION.getEn()));
    result.put(BSLParser.PREPROC_END_REGION, List.of(Keywords.ENDREGION.getRu(), Keywords.ENDREGION.getEn()));
    result.put(BSLParser.PREPROC_IF_KEYWORD, List.of(Keywords.IF.getRu(), Keywords.IF.getEn()));
    result.put(BSLParser.PREPROC_THEN_KEYWORD, List.of(Keywords.THEN.getRu(), Keywords.THEN.getEn()));
    result.put(BSLParser.PREPROC_ELSIF_KEYWORD, List.of(Keywords.ELSIF.getRu(), Keywords.ELSIF.getEn()));
    result.put(BSLParser.PREPROC_ELSE_KEYWORD, List.of(Keywords.ELSE.getRu(), Keywords.ELSE.getEn()));
    result.put(BSLParser.PREPROC_ENDIF_KEYWORD, List.of(Keywords.ENDIF.getRu(), Keywords.ENDIF.getEn()));
    result.put(BSLParser.PREPROC_OR_KEYWORD, List.of(
      Keywords.OR.getRu(), Keywords.OR_UP.getRu(), Keywords.OR.getEn(), Keywords.OR_UP.getEn()));
    result.put(BSLParser.PREPROC_AND_KEYWORD, List.of(
      Keywords.AND.getRu(), Keywords.AND.getEn(), Keywords.AND_UP.getEn()));
    result.put(BSLParser.PREPROC_NOT_KEYWORD, List.of(
      Keywords.NOT.getRu(), Keywords.NOT_UP.getRu(), Keywords.NOT.getEn(), Keywords.NOT_UP.getEn()));
    result.put(BSLParser.PREPROC_SERVER_SYMBOL, List.of(Keywords.SERVER.getRu(), Keywords.SERVER.getEn()));
    result.put(BSLParser.PREPROC_CLIENT_SYMBOL, List.of(Keywords.CLIENT.getRu(), Keywords.CLIENT.getEn()));
    result.put(BSLParser.PREPROC_MOBILEAPPCLIENT_SYMBOL, List.of(
      Keywords.MOBILE_APP_CLIENT.getRu(), Keywords.MOBILE_APP_CLIENT.getEn()));
    result.put(BSLParser.PREPROC_MOBILEAPPSERVER_SYMBOL, List.of(
      Keywords.MOBILE_APP_SERVER.getRu(), Keywords.MOBILE_APP_SERVER.getEn()));
    result.put(BSLParser.PREPROC_MOBILECLIENT_SYMBOL, List.of(
      Keywords.MOBILE_CLIENT.getRu(), Keywords.MOBILE_CLIENT.getEn()));
    result.put(BSLParser.PREPROC_THICKCLIENTORDINARYAPPLICATION_SYMBOL, List.of(
      Keywords.THICK_CLIENT_ORDINARY_APPLICATION.getRu(), Keywords.THICK_CLIENT_ORDINARY_APPLICATION.getEn()));
    result.put(BSLParser.PREPROC_THICKCLIENTMANAGEDAPPLICATION_SYMBOL, List.of(
      Keywords.THICK_CLIENT_MANAGED_APPLICATION.getRu(), Keywords.THICK_CLIENT_MANAGED_APPLICATION.getEn()));
    result.put(BSLParser.PREPROC_EXTERNALCONNECTION_SYMBOL, List.of(
      Keywords.EXTERNAL_CONNECTION.getRu(), Keywords.EXTERNAL_CONNECTION.getEn()));
    result.put(BSLParser.PREPROC_THINCLIENT_SYMBOL, List.of(Keywords.THIN_CLIENT.getRu(), Keywords.THIN_CLIENT.getEn()));
    result.put(BSLParser.PREPROC_WEBCLIENT_SYMBOL, List.of(Keywords.WEB_CLIENT.getRu(), Keywords.WEB_CLIENT.getEn()));
    result.put(BSLParser.PREPROC_ATCLIENT_SYMBOL, List.of(Keywords.AT_CLIENT.getRu(), Keywords.AT_CLIENT.getEn()));
    result.put(BSLParser.PREPROC_ATSERVER_SYMBOL, List.of(Keywords.AT_SERVER.getRu(), Keywords.AT_SERVER.getEn()));
    result.put(BSLParser.ANNOTATION_ATCLIENT_SYMBOL, List.of(Keywords.AT_CLIENT.getRu(), Keywords.AT_CLIENT.getEn()));
    result.put(BSLParser.ANNOTATION_ATSERVER_SYMBOL, List.of(Keywords.AT_SERVER.getRu(), Keywords.AT_SERVER.getEn()));
    result.put(BSLParser.ANNOTATION_ATSERVERNOCONTEXT_SYMBOL, List.of(
      Keywords.AT_SERVER_NO_CONTEXT.getRu(), Keywords.AT_SERVER_NO_CONTEXT.getEn()));
    result.put(BSLParser.ANNOTATION_ATCLIENTATSERVERNOCONTEXT_SYMBOL, List.of(
      Keywords.AT_CLIENT_AT_SERVER_NO_CONTEXT.getRu(), Keywords.AT_CLIENT_AT_SERVER_NO_CONTEXT.getEn()));
    result.put(BSLParser.ANNOTATION_ATCLIENTATSERVER_SYMBOL, List.of(
      Keywords.AT_CLIENT_AT_SERVER.getRu(), Keywords.AT_CLIENT_AT_SERVER.getEn()));

    return result;
  }

  private static Map<String, String> getCanonical() {
    Map<String, String> result = new HashMap<>();

    result.put(Keywords.IF_UP.getRu(), Keywords.IF.getRu());
    result.put(Keywords.IF_UP.getEn(), Keywords.IF.getEn());
    result.put(Keywords.THEN_UP.getRu(), Keywords.THEN.getRu());
    result.put(Keywords.THEN_UP.getEn(), Keywords.THEN.getEn());
    result.put(Keywords.ELSE_UP.getRu(), Keywords.ELSE.getRu());
    result.put(Keywords.ELSE_UP.getEn(), Keywords.ELSE.getEn());
    result.put(Keywords.ELSIF_UP.getRu(), Keywords.ELSIF.getRu());
    result.put(Keywords.ELSIF_UP.getEn(), Keywords.ELSIF.getEn());
    result.put(Keywords.ENDIF_UP.getRu(), Keywords.ENDIF.getRu());
    result.put(Keywords.ENDIF_UP.getEn(), Keywords.ENDIF.getEn());
    result.put(Keywords.FOR_UP.getRu(), Keywords.FOR.getRu());
    result.put(Keywords.FOR_UP.getEn(), Keywords.FOR.getEn());
    result.put(Keywords.EACH_UP.getRu(), Keywords.EACH.getRu());
    result.put(Keywords.EACH_UP.getEn(), Keywords.EACH.getEn());
    result.put(Keywords.IN_UP.getRu(), Keywords.IN.getRu());
    result.put(Keywords.IN_UP.getEn(), Keywords.IN.getEn());
    result.put(Keywords.DO_UP.getRu(), Keywords.DO.getRu());
    result.put(Keywords.DO_UP.getEn(), Keywords.DO.getEn());
    result.put(Keywords.WHILE_UP.getRu(), Keywords.WHILE.getRu());
    result.put(Keywords.WHILE_UP.getEn(), Keywords.WHILE.getEn());
    result.put(Keywords.BREAK_UP.getRu(), Keywords.BREAK.getRu());
    result.put(Keywords.BREAK_UP.getEn(), Keywords.BREAK.getEn());
    result.put(Keywords.CONTINUE_UP.getRu(), Keywords.CONTINUE.getRu());
    result.put(Keywords.CONTINUE_UP.getEn(), Keywords.CONTINUE.getEn());
    result.put(Keywords.END_DO_UP.getRu(), Keywords.END_DO.getRu());
    result.put(Keywords.END_DO_UP.getEn(), Keywords.END_DO.getEn());
    result.put(Keywords.TO_UP.getRu(), Keywords.TO.getRu());
    result.put(Keywords.TO_UP.getEn(), Keywords.TO.getEn());
    result.put(Keywords.PROCEDURE_UP.getRu(), Keywords.PROCEDURE.getRu());
    result.put(Keywords.PROCEDURE_UP.getEn(), Keywords.PROCEDURE.getEn());
    result.put(Keywords.VAL_UP.getRu(), Keywords.VAL.getRu());
    result.put(Keywords.VAL_UP.getEn(), Keywords.VAL.getEn());
    result.put(Keywords.EXPORT_UP.getRu(), Keywords.EXPORT.getRu());
    result.put(Keywords.EXPORT_UP.getEn(), Keywords.EXPORT.getEn());
    result.put(Keywords.VAR_UP.getRu(), Keywords.VAR.getRu());
    result.put(Keywords.VAR_UP.getEn(), Keywords.VAR.getEn());
    result.put(Keywords.TRY_UP.getRu(), Keywords.TRY.getRu());
    result.put(Keywords.TRY_UP.getEn(), Keywords.TRY.getEn());
    result.put(Keywords.EXECUTE_UP.getRu(), Keywords.EXECUTE.getRu());
    result.put(Keywords.EXECUTE_UP.getEn(), Keywords.EXECUTE.getEn());
    result.put(Keywords.RETURN_UP.getRu(), Keywords.RETURN.getRu());
    result.put(Keywords.RETURN_UP.getEn(), Keywords.RETURN.getEn());
    result.put(Keywords.TRUE_UP.getRu(), Keywords.TRUE.getRu());
    result.put(Keywords.TRUE_UP.getEn(), Keywords.TRUE.getEn());
    result.put(Keywords.EXCEPT_UP.getRu(), Keywords.EXCEPT.getRu());
    result.put(Keywords.EXCEPT_UP.getEn(), Keywords.EXCEPT.getEn());
    result.put(Keywords.RAISE_UP.getRu(), Keywords.RAISE.getRu());
    result.put(Keywords.RAISE_UP.getEn(), Keywords.RAISE.getEn());
    result.put(Keywords.END_TRY_UP.getRu(), Keywords.END_TRY.getRu());
    result.put(Keywords.END_TRY_UP.getEn(), Keywords.END_TRY.getEn());
    result.put(Keywords.END_PROCEDURE_UP.getRu(), Keywords.END_PROCEDURE.getRu());
    result.put(Keywords.END_PROCEDURE_UP.getEn(), Keywords.END_PROCEDURE.getEn());
    result.put(Keywords.FUNCTION_UP.getRu(), Keywords.FUNCTION.getRu());
    result.put(Keywords.FUNCTION_UP.getEn(), Keywords.FUNCTION.getEn());
    result.put(Keywords.END_FUNCTION_UP.getRu(), Keywords.END_FUNCTION.getRu());
    result.put(Keywords.END_FUNCTION_UP.getEn(), Keywords.END_FUNCTION.getEn());
    result.put(Keywords.FALSE_UP.getRu(), Keywords.FALSE.getRu());
    result.put(Keywords.FALSE_UP.getEn(), Keywords.FALSE.getEn());
    result.put(Keywords.ADD_HANDLER_UP.getRu(), Keywords.ADD_HANDLER.getRu());
    result.put(Keywords.ADD_HANDLER_UP.getEn(), Keywords.ADD_HANDLER.getEn());
    result.put(Keywords.REMOVE_HANDLER_UP.getRu(), Keywords.REMOVE_HANDLER.getRu());
    result.put(Keywords.REMOVE_HANDLER_UP.getEn(), Keywords.REMOVE_HANDLER.getEn());
    result.put(Keywords.GOTO_UP.getRu(), Keywords.GOTO.getRu());
    result.put(Keywords.GOTO_UP.getEn(), Keywords.GOTO.getEn());
    result.put(Keywords.AND.getRu(), Keywords.AND.getRu());
    result.put(Keywords.AND_UP.getEn(), Keywords.AND_UP.getEn());
    result.put(Keywords.OR_UP.getRu(), Keywords.OR_UP.getRu());
    result.put(Keywords.OR_UP.getEn(), Keywords.OR_UP.getEn());
    result.put(Keywords.NOT_UP.getRu(), Keywords.NOT_UP.getRu());
    result.put(Keywords.NOT_UP.getEn(), Keywords.NOT_UP.getEn());
    result.put(Keywords.NEW_UP.getRu(), Keywords.NEW.getRu());
    result.put(Keywords.NEW_UP.getEn(), Keywords.NEW.getEn());
    result.put(Keywords.UNDEFINED_UP.getRu(), Keywords.UNDEFINED.getRu());
    result.put(Keywords.UNDEFINED_UP.getEn(), Keywords.UNDEFINED.getEn());
    result.put(Keywords.REGION_UP.getRu(), Keywords.REGION.getRu());
    result.put(Keywords.REGION_UP.getEn(), Keywords.REGION.getEn());
    result.put(Keywords.ENDREGION_UP.getRu(), Keywords.ENDREGION.getRu());
    result.put(Keywords.ENDREGION_UP.getEn(), Keywords.ENDREGION.getEn());
    result.put(Keywords.SERVER_UP.getRu(), Keywords.SERVER.getRu());
    result.put(Keywords.SERVER_UP.getEn(), Keywords.SERVER.getEn());
    result.put(Keywords.CLIENT_UP.getRu(), Keywords.CLIENT.getRu());
    result.put(Keywords.CLIENT_UP.getEn(), Keywords.CLIENT.getEn());
    result.put(Keywords.MOBILE_APP_CLIENT_UP.getRu(), Keywords.MOBILE_APP_CLIENT.getRu());
    result.put(Keywords.MOBILE_APP_CLIENT_UP.getEn(), Keywords.MOBILE_APP_CLIENT.getEn());
    result.put(Keywords.MOBILE_APP_SERVER_UP.getRu(), Keywords.MOBILE_APP_SERVER.getRu());
    result.put(Keywords.MOBILE_APP_SERVER_UP.getEn(), Keywords.MOBILE_APP_SERVER.getEn());
    result.put(Keywords.MOBILE_CLIENT_UP.getRu(), Keywords.MOBILE_CLIENT.getRu());
    result.put(Keywords.MOBILE_CLIENT_UP.getEn(), Keywords.MOBILE_CLIENT.getEn());
    result.put(Keywords.THICK_CLIENT_ORDINARY_APPLICATION_UP.getRu(), Keywords.THICK_CLIENT_ORDINARY_APPLICATION.getRu());
    result.put(Keywords.THICK_CLIENT_ORDINARY_APPLICATION_UP.getEn(), Keywords.THICK_CLIENT_ORDINARY_APPLICATION.getEn());
    result.put(Keywords.THICK_CLIENT_MANAGED_APPLICATION_UP.getRu(), Keywords.THICK_CLIENT_MANAGED_APPLICATION.getRu());
    result.put(Keywords.THICK_CLIENT_MANAGED_APPLICATION_UP.getEn(), Keywords.THICK_CLIENT_MANAGED_APPLICATION.getEn());
    result.put(Keywords.EXTERNAL_CONNECTION_UP.getRu(), Keywords.EXTERNAL_CONNECTION.getRu());
    result.put(Keywords.EXTERNAL_CONNECTION_UP.getEn(), Keywords.EXTERNAL_CONNECTION.getEn());
    result.put(Keywords.THIN_CLIENT_UP.getRu(), Keywords.THIN_CLIENT.getRu());
    result.put(Keywords.THIN_CLIENT_UP.getEn(), Keywords.THIN_CLIENT.getEn());
    result.put(Keywords.WEB_CLIENT_UP.getRu(), Keywords.WEB_CLIENT.getRu());
    result.put(Keywords.WEB_CLIENT_UP.getEn(), Keywords.WEB_CLIENT.getEn());
    result.put(Keywords.AT_CLIENT_UP.getRu(), Keywords.AT_CLIENT.getRu());
    result.put(Keywords.AT_CLIENT_UP.getEn(), Keywords.AT_CLIENT.getEn());
    result.put(Keywords.AT_SERVER_UP.getRu(), Keywords.AT_SERVER.getRu());
    result.put(Keywords.AT_SERVER_UP.getEn(), Keywords.AT_SERVER.getEn());
    result.put(Keywords.AT_SERVER_NO_CONTEXT_UP.getRu(), Keywords.AT_SERVER_NO_CONTEXT.getRu());
    result.put(Keywords.AT_SERVER_NO_CONTEXT_UP.getEn(), Keywords.AT_SERVER_NO_CONTEXT.getEn());
    result.put(Keywords.AT_CLIENT_AT_SERVER_NO_CONTEXT_UP.getRu(), Keywords.AT_CLIENT_AT_SERVER_NO_CONTEXT.getRu());
    result.put(Keywords.AT_CLIENT_AT_SERVER_NO_CONTEXT_UP.getEn(), Keywords.AT_CLIENT_AT_SERVER_NO_CONTEXT.getEn());
    result.put(Keywords.AT_CLIENT_AT_SERVER_UP.getRu(), Keywords.AT_CLIENT_AT_SERVER.getRu());
    result.put(Keywords.AT_CLIENT_AT_SERVER_UP.getEn(), Keywords.AT_CLIENT_AT_SERVER.getEn());
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
