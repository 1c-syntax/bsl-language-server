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
import org.eclipse.lsp4j.Range;
import org.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import org.github._1c_syntax.bsl.languageserver.utils.RangeHelper;
import org.github._1c_syntax.bsl.parser.BSLParserBaseVisitor;
import org.github._1c_syntax.bsl.parser.BSLParserRuleContext;

import java.util.List;

public abstract class AbstractVisitorDiagnostic extends BSLParserBaseVisitor<ParseTree> implements BSLDiagnostic {

  protected DiagnosticStorage diagnosticStorage = new DiagnosticStorage(this);
  protected DocumentContext documentContext;

  @Override
  public List<Diagnostic> getDiagnostics(DocumentContext documentContext) {
    this.documentContext = documentContext;
    diagnosticStorage.clearDiagnostics();
    this.visitFile(documentContext.getAst());
    return diagnosticStorage.getDiagnostics();
  }

  /**
   * @deprecated use diagnosticStorage.addDiagnostic()
   */
  @Deprecated
  protected void addDiagnostic(BSLParserRuleContext node) {
    diagnosticStorage.addDiagnostic(node);
  }

  /**
   * @deprecated use diagnosticStorage.addDiagnostic()
   */
  @Deprecated
  protected void addDiagnostic(BSLParserRuleContext node, String diagnosticMessage) {
    diagnosticStorage.addDiagnostic(node, diagnosticMessage);
  }

  /**
   * @deprecated use diagnosticStorage.addDiagnostic()
   */
  @Deprecated
  protected void addDiagnostic(int startLine, int startChar, int endLine, int endChar) {
    diagnosticStorage.addDiagnostic(startLine, startChar, endLine, endChar);
  }

  /**
   * @deprecated use diagnosticStorage.addDiagnostic()
   */
  @Deprecated
  protected void addDiagnostic(Token token) {
    diagnosticStorage.addDiagnostic(token);
  }

  /**
   * @deprecated use diagnosticStorage.addDiagnostic()
   */
  @Deprecated
  protected void addDiagnostic(BSLParserRuleContext node, List<DiagnosticRelatedInformation> relatedInformation) {
    diagnosticStorage.addDiagnostic(node, relatedInformation);
  }

  /**
   * @deprecated use RangeHelper.createRelatedInformation()
   */
  @Deprecated
  protected DiagnosticRelatedInformation createRelatedInformation(Range range, String message) {
    return RangeHelper.createRelatedInformation(documentContext.getUri(), range, message);
  }
}
