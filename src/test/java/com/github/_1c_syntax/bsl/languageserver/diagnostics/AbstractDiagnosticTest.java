/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
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

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.utils.Absolute;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

abstract class AbstractDiagnosticTest<T extends BSLDiagnostic> {

  protected final T diagnosticInstance;
  @Nullable
  protected ServerContext context;

  @SuppressWarnings("unchecked")
  AbstractDiagnosticTest(Class<T> diagnosticClass) {
    DiagnosticSupplier diagnosticSupplier = new DiagnosticSupplier(LanguageServerConfiguration.create());
    diagnosticInstance = (T) diagnosticSupplier.getDiagnosticInstance(diagnosticClass);
  }

  protected void initServerContext(String path) {
    var configurationRoot = Absolute.path(path);
    context = new ServerContext(configurationRoot);
    context.populateContext();
  }

  protected List<Diagnostic> getDiagnostics(DocumentContext documentContext) {
    return diagnosticInstance.getDiagnostics(documentContext);
  }

  protected List<Diagnostic> getDiagnostics() {
    DocumentContext documentContext = getDocumentContext();
    return getDiagnostics(documentContext);
  }

  protected List<Diagnostic> getDiagnostics(String simpleFileName) {
    DocumentContext documentContext = getDocumentContext(simpleFileName);
    return getDiagnostics(documentContext);
  }

  protected List<CodeAction> getQuickFixes(Diagnostic diagnostic) {
    return getQuickFixes(diagnostic, getDocumentContext());
  }

  protected List<CodeAction> getQuickFixes(Diagnostic diagnostic, DocumentContext documentContext) {
    return getQuickFixes(documentContext, Collections.singletonList(diagnostic), diagnostic.getRange());
  }

  protected List<CodeAction> getQuickFixes(Diagnostic diagnostic, Range range) {
    DocumentContext documentContext = getDocumentContext();
    return getQuickFixes(documentContext, Collections.singletonList(diagnostic), range);
  }

  protected List<CodeAction> getQuickFixes(Range range) {
    DocumentContext documentContext = getDocumentContext();
    List<Diagnostic> diagnostics = this.diagnosticInstance.getDiagnostics(documentContext);

    return getQuickFixes(documentContext, diagnostics, range);
  }

  private List<CodeAction> getQuickFixes(DocumentContext documentContext, List<Diagnostic> diagnostics, Range range) {
    TextDocumentIdentifier textDocument = new TextDocumentIdentifier(documentContext.getUri().toString());

    CodeActionContext codeActionContext = new CodeActionContext();
    codeActionContext.setDiagnostics(diagnostics);
    codeActionContext.setOnly(Collections.singletonList(CodeActionKind.QuickFix));

    CodeActionParams params = new CodeActionParams();
    params.setTextDocument(textDocument);
    params.setRange(range);
    params.setContext(codeActionContext);

    return ((QuickFixProvider) this.diagnosticInstance).getQuickFixes(diagnostics, params, documentContext);

  }

  protected DocumentContext getDocumentContext() {
    return getDocumentContext(diagnosticInstance.getClass().getSimpleName());
  }

  @SneakyThrows
  protected DocumentContext getDocumentContext(String SimpleFileName) {
    String textDocumentContent = getText(SimpleFileName);

    return TestUtils.getDocumentContext(textDocumentContent, context);
  }

  protected String getText() {
    return getText(diagnosticInstance.getClass().getSimpleName());
  }

  @SneakyThrows
  protected String getText(String SimpleFileName) {
    String filePath = "diagnostics/" + SimpleFileName + ".bsl";
    return IOUtils.resourceToString(
      filePath,
      StandardCharsets.UTF_8,
      this.getClass().getClassLoader()
    );
  }

}
