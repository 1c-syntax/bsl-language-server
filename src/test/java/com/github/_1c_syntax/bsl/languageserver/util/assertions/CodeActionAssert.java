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
package com.github._1c_syntax.bsl.languageserver.util.assertions;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.diagnostics.BSLDiagnostic;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;

import java.util.List;
import java.util.Objects;

import static java.lang.Integer.max;

public class CodeActionAssert extends AbstractAssert<CodeActionAssert, CodeAction> {

  private DocumentContext documentContext;
  private BSLDiagnostic bslDiagnostic;

  public CodeActionAssert(CodeAction actual) {
    super(actual, CodeActionAssert.class);
  }

  public static CodeActionAssert assertThat(CodeAction actual) {
    return new CodeActionAssert(actual);
  }

  public CodeActionAssert of(BSLDiagnostic diagnostic) {
    this.bslDiagnostic = diagnostic;
    return this;
  }

  public CodeActionAssert in(DocumentContext documentContext) {
    this.documentContext = documentContext;
    return this;
  }

  public CodeActionAssert fixes(Diagnostic diagnostic) {
    // check that actual value we want to make assertions on is not null.
    isNotNull();

    // saving original state
    var cachedContent = documentContext.getContent();
    var serverContext = documentContext.getServerContext();

    // apply edits from quick fix
    final List<TextEdit> textEdits = getTextEdits();

    String[] contentList = documentContext.getContentList();
    textEdits.forEach(textEdit -> {
      final String newText = textEdit.getNewText();
      final Range range = textEdit.getRange();

      final Position start = range.getStart();
      int startLine = 0;
      int startChar = 0;
      int endLine = start.getLine();
      int endChar = 0;
      if (start.getCharacter() > 0) {
        endChar = start.getCharacter() - 1;
      }
      Range startRange = Ranges.create(startLine, startChar, endLine, endChar);
      final String startText = documentContext.getText(startRange);

      final Position end = range.getEnd();
      startLine = end.getLine();
      startChar = end.getCharacter();
      endLine = contentList.length - 1;
      endChar = max(contentList[endLine].length() - 1, 0);

      Range endRange = Ranges.create(startLine, startChar, endLine, endChar);
      final String endText = documentContext.getText(endRange);

      // TODO: does not work for several textedits changing content length (missed semicolon ie.)
      String content = startText + newText + endText;
      serverContext.rebuildDocument(documentContext, content, documentContext.getVersion() + 1);
    });

    // get diagnostics from fixed document
    final List<Diagnostic> diagnostics = bslDiagnostic.getDiagnostics(documentContext);

    // check if expected diagnostic is not present in new diagnostic list
    Assertions.assertThat(diagnostics).doesNotContain(diagnostic)
    ;

    // returning to original state
    serverContext.rebuildDocument(documentContext, cachedContent, documentContext.getVersion() + 1);

    return this;
  }

  public CodeActionAssert hasNewText(String expected) {
    isNotNull();

    final List<TextEdit> textEdits = getTextEdits();
    Assertions.assertThat(textEdits).extracting(TextEdit::getNewText).anyMatch(expected::equals);

    return this;
  }

  public CodeActionAssert containsNewText(String expected) {
    isNotNull();

    final List<TextEdit> textEdits = getTextEdits();
    Assertions.assertThat(textEdits).extracting(TextEdit::getNewText).anyMatch(expected::contains);

    return this;
  }

  public CodeActionAssert hasChanges(int size) {
    isNotNull();

    final List<TextEdit> textEdits = getTextEdits();
    Assertions.assertThat(textEdits).hasSize(size);

    return this;
  }

  private List<TextEdit> getTextEdits() {
    final List<TextEdit> textEdits = actual.getEdit().getChanges().get(documentContext.getUri().toString());
    Objects.requireNonNull(textEdits);
    return textEdits;
  }
}
