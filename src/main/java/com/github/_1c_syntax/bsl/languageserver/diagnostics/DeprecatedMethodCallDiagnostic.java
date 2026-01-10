/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2026
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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.context.symbol.Describable;
import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceIndex;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.parser.description.SourceDefinedSymbolDescription;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.SymbolKind;

import java.util.Optional;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MINOR,
  minutesToFix = 3,
  tags = {
    DiagnosticTag.DEPRECATED,
    DiagnosticTag.DESIGN
  }
)
@RequiredArgsConstructor
public class DeprecatedMethodCallDiagnostic extends AbstractDiagnostic {
  private final ReferenceIndex referenceIndex;

  @Override
  public void check() {
    var uri = documentContext.getUri();

    referenceIndex.getReferencesFrom(uri, SymbolKind.Method).stream()
      .filter(reference -> reference.symbol().isDeprecated())
      .filter(reference -> !reference.from().isDeprecated())
      .forEach((Reference reference) -> {
        var deprecatedSymbol = reference.symbol();
        var deprecationInfo = getDeprecationInfo(deprecatedSymbol);
        var message = info.getMessage(deprecatedSymbol.getName(), deprecationInfo);
        diagnosticStorage.addDiagnostic(reference.selectionRange(), message);
      });
  }

  private static String getDeprecationInfo(Symbol deprecatedSymbol) {
    return Optional.of(deprecatedSymbol)
      .filter(Describable.class::isInstance)
      .map(Describable.class::cast)
      .flatMap(Describable::getDescription)
      .map(SourceDefinedSymbolDescription::getDeprecationInfo)
      .orElse("");
  }
}
