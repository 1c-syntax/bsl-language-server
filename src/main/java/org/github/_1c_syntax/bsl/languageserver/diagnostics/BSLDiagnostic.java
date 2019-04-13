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

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;
import org.eclipse.lsp4j.Range;
import org.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import org.github._1c_syntax.bsl.languageserver.providers.DiagnosticProvider;
import org.github._1c_syntax.bsl.languageserver.utils.RangeHelper;
import org.github._1c_syntax.bsl.languageserver.utils.UTF8Control;
import org.github._1c_syntax.bsl.parser.BSLParserRuleContext;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

public interface BSLDiagnostic {

  List<Diagnostic> getDiagnostics(DocumentContext documentContext);

  default String getDiagnosticMessage() {
    return getResourceString("diagnosticMessage");
  }

  default String getResourceString(String key) {
    return ResourceBundle.getBundle(getClass().getName(), new UTF8Control()).getString(key);
  }

  default void configure(Map<String, Object> configuration) {}

  static Diagnostic createDiagnostic(BSLDiagnostic bslDiagnostic, BSLParserRuleContext node) {
    return createDiagnostic(bslDiagnostic, RangeHelper.newRange(node), bslDiagnostic.getDiagnosticMessage());
  }

  static Diagnostic createDiagnostic(
    BSLDiagnostic bslDiagnostic,
    BSLParserRuleContext node,
    List<DiagnosticRelatedInformation> relatedInformation
  ) {
    return createDiagnostic(
      bslDiagnostic,
      RangeHelper.newRange(node),
      bslDiagnostic.getDiagnosticMessage(),
      relatedInformation
    );
  }

  static Diagnostic createDiagnostic(BSLDiagnostic bslDiagnostic, String diagnosticMessage, BSLParserRuleContext node) {
    return createDiagnostic(bslDiagnostic, RangeHelper.newRange(node), diagnosticMessage);
  }

  static Diagnostic createDiagnostic(
    BSLDiagnostic bslDiagnostic,
    int startLine,
    int startChar,
    int endLine,
    int endChar
  ) {
    return createDiagnostic(
      bslDiagnostic,
      RangeHelper.newRange(startLine, startChar, endLine, endChar),
      bslDiagnostic.getDiagnosticMessage()
    );
  }

  static Diagnostic createDiagnostic(BSLDiagnostic bslDiagnostic, Range range, String diagnosticMessage) {
    return createDiagnostic(
      bslDiagnostic,
      range,
      diagnosticMessage,
      null
    );

  }

  static Diagnostic createDiagnostic(
    BSLDiagnostic bslDiagnostic,
    Range range,
    String diagnosticMessage,
    @Nullable
    List<DiagnosticRelatedInformation> relatedInformation
  ) {
    Diagnostic diagnostic = new Diagnostic(
      range,
      diagnosticMessage,
      DiagnosticProvider.getLSPDiagnosticSeverity(bslDiagnostic),
      DiagnosticProvider.SOURCE,
      DiagnosticProvider.getDiagnosticCode(bslDiagnostic)
    );

    if (relatedInformation != null) {
      diagnostic.setRelatedInformation(relatedInformation);
    }
    return diagnostic;
  }
}
