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
import org.eclipse.lsp4j.Diagnostic;
import org.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import org.github._1c_syntax.bsl.languageserver.utils.RangeHelper;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  minutesToFix = 1
)

public class MissingSpaceDiagnostic extends AbstractVisitorDiagnostic {
  //TODO <> если вместе - подумать
  private static final String symbolsLR = "+-*/=%<>"; //символы, требующие пробелы слева и справа
  private static final String symbolsR = ",;";        //символы, требующие пробелы справа

  private static final Pattern PATTERN_LR = Pattern.compile(
//    "\\S[\\Q"+symbolsLR+"\\E]|[\\Q"+symbolsLR+"\\E]\\S",
    ".*Процедура.*",
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
  );
  private static final Pattern PATTERN_R = Pattern.compile(
    "[\\Q"+symbolsR+"\\E]\\S",
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
  );

  @Override
  public List<Diagnostic> getDiagnostics(DocumentContext documentContext) {
    //String f = "0";
    diagnosticStorage.clearDiagnostics();

    Stream<Token> ps = documentContext.getTokens().parallelStream();
    Stream<Token> f = ps.filter((Token t) -> PATTERN_LR.matcher(t.getText()).matches());
    f.forEach((Token t) -> diagnosticStorage.addDiagnostic(t));

    /*documentContext.getTokens()
      .parallelStream()
      .filter((Token t) ->
        PATTERN_LR.matcher(t.getText()).matches())
      .forEach((Token t) ->
        diagnosticStorage.addDiagnostic(t));*/

    return diagnosticStorage.getDiagnostics();
  }
}
