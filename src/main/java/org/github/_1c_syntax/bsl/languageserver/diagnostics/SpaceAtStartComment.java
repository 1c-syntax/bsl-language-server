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
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import org.github._1c_syntax.bsl.languageserver.utils.RangeHelper;
import org.github._1c_syntax.bsl.parser.BSLParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  minutesToFix = 1
)

public class SpaceAtStartComment implements BSLDiagnostic {

  private static final String DEFAULT_COMMENTS_ANNOTATION = "//@,//(c)";
  private static final Pattern goodCommentPattern = Pattern.compile(
    "(?://\\s.*)|(?://[/]*)$",
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
  );

  protected DiagnosticStorage diagnosticStorage = new DiagnosticStorage(this);

  @DiagnosticParameter(
    type = String.class,
    defaultValue = "" + DEFAULT_COMMENTS_ANNOTATION,
    description = "Пропускать комментарии-аннотации, начинающиеся с указанных подстрок. Список через запятую. Например: //@,//(c)"
  )
  private List<String> commentsAnnotation = Arrays.asList(DEFAULT_COMMENTS_ANNOTATION.split(","));

  @Override
  public void configure(Map<String, Object> configuration) {
    if (configuration == null) {
      return;
    }

    String commentsAnnotationString = (String) configuration.get("commentsAnnotation");
    for (String s : commentsAnnotationString.split(",")) {
      this.commentsAnnotation.add(s.trim());
    }
  }

  @Override
  public List<Diagnostic> getDiagnostics(DocumentContext documentContext) {

    diagnosticStorage.clearDiagnostics();

    String commentsAnnotationPatternString = "";
    for (String elem : this.commentsAnnotation) {
      commentsAnnotationPatternString += "(?:^" + Pattern.quote(elem) + ".*)|";
    }

    if(!commentsAnnotationPatternString.isEmpty()) {
      commentsAnnotationPatternString = commentsAnnotationPatternString.substring(0, commentsAnnotationPatternString.length() - 1);
    }

    Pattern commentsAnnotationPattern = Pattern.compile(
      "" + commentsAnnotationPatternString,
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    List<Token> comments = documentContext.getComments()
      .parallelStream()
      .filter((Token t) ->
        !goodCommentPattern.matcher(t.getText()).matches()
          && !commentsAnnotationPattern.matcher(t.getText()).matches())
      .collect((Collectors.toList()));

    for (Token token : comments) {
      diagnosticStorage.addDiagnostic(
        token.getLine() - 1,
        token.getCharPositionInLine(),
        token.getLine() - 1,
        token.getCharPositionInLine() + token.getText().length());
    }

    return diagnosticStorage.getDiagnostics();
  }
}
