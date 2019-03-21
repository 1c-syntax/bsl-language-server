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
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import org.github._1c_syntax.bsl.parser.BSLParserBaseVisitor;
import org.github._1c_syntax.bsl.parser.BSLParserRuleContext;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractVisitorDiagnostic extends BSLParserBaseVisitor<ParseTree> implements BSLDiagnostic {
  protected List<Diagnostic> diagnostics = new ArrayList<>();
  protected DocumentContext documentContext;

  @Override
  public List<Diagnostic> getDiagnostics(DocumentContext documentContext) {
    this.documentContext = documentContext;
    diagnostics.clear();
    this.visitFile(documentContext.getAst());
    return new ArrayList<>(diagnostics);
  }

  protected void addDiagnostic(BSLParserRuleContext node) {
    diagnostics.add(BSLDiagnostic.createDiagnostic(this, node));
  }

  protected void addDiagnostic(BSLParserRuleContext node, String diagnosticMessage) {
    diagnostics.add(BSLDiagnostic.createDiagnostic(this, diagnosticMessage, node));
  }

  protected void addDiagnostic(int startLine, int startChar, int endLine, int endChar) {
    diagnostics.add(BSLDiagnostic.createDiagnostic(this, startLine, startChar, endLine, endChar));
  }

  protected void addDiagnostic(Token token) {
    diagnostics.add(BSLDiagnostic.createDiagnostic(
      this,
      token.getLine() - 1,
      token.getCharPositionInLine(),
      token.getLine() - 1,
      token.getCharPositionInLine() + token.getText().length()
    ));
  }

  protected void addDiagnostic(BSLParserRuleContext node, List<DiagnosticRelatedInformation> relatedInformation) {
    diagnostics.add(BSLDiagnostic.createDiagnostic(this, node, relatedInformation));
  }

  protected DiagnosticRelatedInformation createRelatedInformation(Range range, String message) {
    Location location = new Location(documentContext.getUri(), range);
    return new DiagnosticRelatedInformation(location, message);
  }
}
