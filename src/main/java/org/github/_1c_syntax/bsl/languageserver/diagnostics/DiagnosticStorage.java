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
import org.eclipse.lsp4j.DiagnosticRelatedInformation;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import org.github._1c_syntax.bsl.parser.BSLParserRuleContext;

import java.util.ArrayList;
import java.util.List;

public class DiagnosticStorage {

  private DocumentContext documentContext;
  private BSLDiagnostic diagnostic;
  private List<Diagnostic> diagnosticList = new ArrayList<>();

  DiagnosticStorage(BSLDiagnostic diagnostic) {
    this.diagnostic = diagnostic;
  }

  public DocumentContext getDocumentContext() {
    return documentContext;
  }

  public void setDocumentContext(DocumentContext documentContext) {
    this.documentContext = documentContext;
  }

  public List<Diagnostic> getDiagnostics() {
    return new ArrayList<>(diagnosticList);
  }

  public void clearDiagnostics() {
    diagnosticList.clear();
  }

  protected void addDiagnostic(BSLParserRuleContext node) {
    diagnosticList.add(BSLDiagnostic.createDiagnostic(diagnostic, node));
  }

  protected void addDiagnostic(BSLParserRuleContext node, String diagnosticMessage) {
    diagnosticList.add(BSLDiagnostic.createDiagnostic(diagnostic, diagnosticMessage, node));
  }

  protected void addDiagnostic(int startLine, int startChar, int endLine, int endChar) {
    diagnosticList.add(BSLDiagnostic.createDiagnostic(diagnostic, startLine, startChar, endLine, endChar));
  }

  protected void addDiagnostic(Token token) {
    diagnosticList.add(BSLDiagnostic.createDiagnostic(
      diagnostic,
      token.getLine() - 1,
      token.getCharPositionInLine(),
      token.getLine() - 1,
      token.getCharPositionInLine() + token.getText().length()
    ));
  }

  protected void addDiagnostic(BSLParserRuleContext node, List<DiagnosticRelatedInformation> relatedInformation) {
    diagnosticList.add(BSLDiagnostic.createDiagnostic(diagnostic, node, relatedInformation));
  }

  protected DiagnosticRelatedInformation createRelatedInformation(Range range, String message) {
    Location location = new Location(documentContext.getUri(), range);
    return new DiagnosticRelatedInformation(location, message);
  }

}


