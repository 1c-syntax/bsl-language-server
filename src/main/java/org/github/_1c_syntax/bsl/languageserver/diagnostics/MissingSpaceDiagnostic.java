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

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  minutesToFix = 1
)

public class MissingSpaceDiagnostic extends AbstractVisitorDiagnostic {
  // TODO если "<>" "<=" ">=" рядом - то это верно - надо сделать
  private static final String symbols_LR = "+-*/=%<>"; // символы, требующие пробелы слева и справа
  private static final String symbols_R = ",;";        // символы, требующие пробелы только справа

  private static final Pattern PATTERN_LR = Pattern.compile(
    "[\\Q"+ symbols_LR +"\\E]",
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
  );
  private static final Pattern PATTERN_R = Pattern.compile(
    "[\\Q"+ symbols_R +"\\E]",
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
  );
  private static final Pattern PATTERN_SPACE = Pattern.compile(
    "\\S+",
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
  );

  @Override
  public List<Diagnostic> getDiagnostics(DocumentContext documentContext) {

    List<Token> tokens = documentContext.getTokens();

    // проверяем слева и справа
    Stream<Token> tokens1 = tokens.stream()
      .filter((Token t) ->
      PATTERN_LR.matcher( t.getText() ).matches());

    Stream<Token> tokens2 = tokens1.filter((Token t) ->
        noSpaceLeftAndRight(tokens, t)
    );
    tokens2.forEach((Token t) ->
      diagnosticStorage.addDiagnostic(t));

    // проверяем справа
    Stream<Token> tokens3 = tokens.stream()
      .filter((Token t) ->
        PATTERN_R.matcher( t.getText() ).matches());

    Stream<Token> tokens4 = tokens3.filter((Token t) ->
      NoSpaceRight(tokens, t)
    );
    tokens4.forEach((Token t) ->
      diagnosticStorage.addDiagnostic(t));



    /*documentContext.getTokens()
      .parallelStream()
      .filter((Token t) ->
        PATTERN_LR.matcher(t.getText()).matches())
      .forEach((Token t) ->
        diagnosticStorage.addDiagnostic(t));*/

    return diagnosticStorage.getDiagnostics();
  }

  private boolean noSpaceLeftAndRight(List<Token> tokens, Token t) {
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
  private boolean NoSpaceRight(List<Token> tokens, Token t) {
    int tokenIndex = t.getTokenIndex();

    Token nextToken = tokens.get(tokenIndex + 1);
    String nextTokenText = nextToken.getText();

    return PATTERN_SPACE.matcher(nextTokenText).matches();

    //return PATTERN_SPACE.matcher(tokens.get(t.getTokenIndex() + 1).getText()).matches();
  }
}
