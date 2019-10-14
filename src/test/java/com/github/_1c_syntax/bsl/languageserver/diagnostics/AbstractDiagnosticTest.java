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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import org.apache.commons.io.IOUtils;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

abstract class AbstractDiagnosticTest<T extends BSLDiagnostic> {

  private final T diagnostic;

  AbstractDiagnosticTest(Class<T> diagnosticClass) {
    try {
      diagnostic = diagnosticClass.getDeclaredConstructor().newInstance();
    } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
      e.printStackTrace();
      throw new RuntimeException("Diagnostic instantiate error", e);
    }
  }

  T getDiagnosticInstance() {
    return diagnostic;
  }

  List<Diagnostic> getDiagnostics() {
    DocumentContext documentContext = getDocumentContext();
    return diagnostic.getDiagnostics(documentContext);
  }

  List<Diagnostic> getDiagnostics(String SimpleFileName) {
    DocumentContext documentContext = getDocumentContext(SimpleFileName);
    return diagnostic.getDiagnostics(documentContext);
  }

  List<CodeAction> getQuickFixes(Diagnostic diagnostic, Range range) {
    DocumentContext documentContext = getDocumentContext();
    return getQuickFixes(documentContext, Collections.singletonList(diagnostic), range);
  }

  List<CodeAction> getQuickFixes(Range range) {
    DocumentContext documentContext = getDocumentContext();
    List<Diagnostic> diagnostics = this.diagnostic.getDiagnostics(documentContext);

    return getQuickFixes(documentContext, diagnostics, range);
  }

  private List<CodeAction> getQuickFixes(DocumentContext documentContext, List<Diagnostic> diagnostics, Range range) {
    TextDocumentIdentifier textDocument = new TextDocumentIdentifier(documentContext.getUri());

    CodeActionContext codeActionContext = new CodeActionContext();
    codeActionContext.setDiagnostics(diagnostics);
    codeActionContext.setOnly(Collections.singletonList(CodeActionKind.QuickFix));

    CodeActionParams params = new CodeActionParams();
    params.setTextDocument(textDocument);
    params.setRange(range);
    params.setContext(codeActionContext);

    return ((QuickFixProvider) this.diagnostic).getQuickFixes(diagnostics, params, documentContext);

  }

  private DocumentContext getDocumentContext() {
    return getDocumentContext(diagnostic.getClass().getSimpleName());
  }

  private DocumentContext getDocumentContext(String SimpleFileName) {
    String textDocumentContent;
    try {
      textDocumentContent = IOUtils.resourceToString(
        "diagnostics/" + SimpleFileName + ".bsl",
        StandardCharsets.UTF_8,
        this.getClass().getClassLoader()
      );
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    return new DocumentContext("file:///fake-uri.bsl", textDocumentContent);
  }

}
