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
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import org.github._1c_syntax.bsl.languageserver.providers.CodeActionProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  minutesToFix = 1
)

public class SpaceAtStartCommentDiagnostic extends AbstractVisitorDiagnostic implements QuickFixProvider {

  private static final String DEFAULT_COMMENTS_ANNOTATION = "//@,//(c)";
  private static final Pattern goodCommentPattern = Pattern.compile(
    "(?://\\s.*)|(?://[/]*)$",
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
  );

  protected DiagnosticStorage diagnosticStorage = new DiagnosticStorage(this);

  @DiagnosticParameter(
    type = String.class,
    defaultValue = "" + DEFAULT_COMMENTS_ANNOTATION,
    description = "Пропускать комментарии-аннотации, начинающиеся с указанных подстрок."
      + " Список через запятую. Например: //@,//(c)"
  )
  private Pattern commentsAnnotation = createCommentsAnnotationPattern(DEFAULT_COMMENTS_ANNOTATION.split(","));

  @Override
  public void configure(Map<String, Object> configuration) {
    if (configuration == null) {
      return;
    }

    String commentsAnnotationString = (String) configuration.get("commentsAnnotation");
    this.commentsAnnotation = createCommentsAnnotationPattern(commentsAnnotationString.split(","));
  }

  @Override
  public List<Diagnostic> getDiagnostics(DocumentContext documentContext) {
    diagnosticStorage.clearDiagnostics();

    documentContext.getComments()
      .parallelStream()
      .filter((Token t) ->
        !goodCommentPattern.matcher(t.getText()).matches()
          && !commentsAnnotation.matcher(t.getText()).matches())
      .sequential()
      .forEach((Token t) ->
        diagnosticStorage.addDiagnostic(t));

    return diagnosticStorage.getDiagnostics();
  }

  private static Pattern createCommentsAnnotationPattern(String[] patternParts) {
    StringJoiner stringJoiner = new StringJoiner("|");
    for (String elem : patternParts) {
      String commentsAnnotationPatternString = "(?:^" + Pattern.quote(elem) + ".*)";
      stringJoiner.add(commentsAnnotationPatternString);
    }

    return Pattern.compile(
      stringJoiner.toString(),
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
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
      Range diagnosticRange = diagnostic.getRange();
      Position currentEnd = diagnosticRange.getEnd();
      String currentText = documentContext.getText(diagnosticRange);
      Position newEnd = new Position(currentEnd.getLine(), currentEnd.getCharacter() + 1);
      Range newRange = new Range(diagnosticRange.getStart(), newEnd);

      TextEdit textEdit = new TextEdit(newRange, currentText.substring(0, 2) + " " + currentText.substring(2));
      textEdits.add(textEdit);
    });

    return CodeActionProvider.createCodeActions(
      textEdits,
      getResourceString("quickFixMessage"),
      documentContext.getUri(),
      diagnostics
    );
  }
}
