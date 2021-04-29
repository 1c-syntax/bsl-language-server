/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2021
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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.providers.CodeActionProvider;
import com.github._1c_syntax.bsl.languageserver.recognizer.BSLFootprint;
import com.github._1c_syntax.bsl.languageserver.recognizer.CodeRecognizer;
import com.github._1c_syntax.bsl.languageserver.utils.DiagnosticHelper;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.STANDARD
  }
)

public class SpaceAtStartCommentDiagnostic extends AbstractDiagnostic implements QuickFixProvider {

  private static final String DEFAULT_COMMENTS_ANNOTATION = "//@,//(c),//©";
  private static final Pattern goodCommentPattern = CaseInsensitivePattern.compile(
    "(?://\\s.*)|(?://[/]*\\s*)$"
  );
  private static final int COMMENT_LENGTH = 2;

  private static final float COMMENTED_CODE_THRESHOLD = 0.9F;
  private final CodeRecognizer codeRecognizer;

  @DiagnosticParameter(
    type = String.class,
    defaultValue = "" + DEFAULT_COMMENTS_ANNOTATION
  )
  private Pattern commentsAnnotation = DiagnosticHelper.createPatternFromString(DEFAULT_COMMENTS_ANNOTATION);

  public SpaceAtStartCommentDiagnostic() {
    this.codeRecognizer = new CodeRecognizer(COMMENTED_CODE_THRESHOLD, new BSLFootprint());
  }

  @Override
  public void configure(Map<String, Object> configuration) {
    this.commentsAnnotation = DiagnosticHelper.createPatternFromString(
      (String) configuration.getOrDefault("commentsAnnotation", DEFAULT_COMMENTS_ANNOTATION));
  }

  @Override
  public void check() {
    documentContext.getComments()
      .parallelStream()
      .filter((Token t) ->
        !goodCommentPattern.matcher(t.getText()).matches()
          && !commentsAnnotation.matcher(t.getText()).matches()
          && !codeRecognizer.meetsCondition(t.getText()))
      .sequential()
      .forEach(diagnosticStorage::addDiagnostic);
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
      String currentText = documentContext.getText(range);

      TextEdit textEdit = new TextEdit(
        range,
        currentText.substring(0, COMMENT_LENGTH) + " " + currentText.substring(COMMENT_LENGTH)
      );
      textEdits.add(textEdit);
    });

    return CodeActionProvider.createCodeActions(
      textEdits,
      info.getResourceString("quickFixMessage"),
      documentContext.getUri(),
      diagnostics
    );
  }
}
