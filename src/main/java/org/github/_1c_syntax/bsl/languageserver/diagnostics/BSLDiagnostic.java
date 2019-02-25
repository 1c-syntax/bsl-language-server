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
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Range;
import org.github._1c_syntax.bsl.languageserver.utils.UTF8Control;
import org.github._1c_syntax.bsl.languageserver.providers.DiagnosticProvider;
import org.github._1c_syntax.bsl.languageserver.utils.RangeHelper;
import org.github._1c_syntax.bsl.parser.BSLParser;
import org.github._1c_syntax.bsl.parser.BSLParserRuleContext;

import java.util.List;
import java.util.ResourceBundle;

public interface BSLDiagnostic {

  default DiagnosticSeverity getSeverity() {
    return DiagnosticSeverity.Error;
  }

  default String getCode() {
    String simpleName = getClass().getSimpleName();
    if (simpleName.endsWith("Diagnostic")) {
      simpleName = simpleName.substring(0, simpleName.length() - "Diagnostic".length());
    }
    return simpleName;
  }

  List<Diagnostic> getDiagnostics(BSLParser.FileContext fileTree);

  default String getDiagnosticMessage() {
    return ResourceBundle.getBundle(getClass().getName(), new UTF8Control()).getString("diagnosticMessage");
  }

  default String getDiagnosticMessage(String key) {
    return ResourceBundle.getBundle(getClass().getName(), new UTF8Control()).getString(key);
  }

  static Diagnostic createDiagnostic(BSLDiagnostic bslDiagnostic, BSLParserRuleContext node) {
    return createDiagnostic(bslDiagnostic, RangeHelper.newRange(node), bslDiagnostic.getDiagnosticMessage());
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
    return new Diagnostic(
      range,
      diagnosticMessage,
      bslDiagnostic.getSeverity(),
      DiagnosticProvider.SOURCE,
      bslDiagnostic.getCode()
    );
  }
}
