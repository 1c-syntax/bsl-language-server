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

import com.github._1c_syntax.bsl.languageserver.context.references.ReferencesStorage;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodDescription;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTree;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.Range;

import java.util.Collection;
import java.util.Map;

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
  private final ReferencesStorage referencesStorage;

  @Override
  public void check() {
    var uri = documentContext.getUri();

    Map<MethodSymbol, Collection<Range>> calledSymbols = referencesStorage.getCalledMethodSymbolsFrom(uri);
    SymbolTree symbolTree = documentContext.getSymbolTree();

    calledSymbols.forEach((MethodSymbol methodSymbol, Collection<Range> ranges) -> {
      if (!methodSymbol.isDeprecated()) {
        return;
      }

      String deprecationInfo = methodSymbol.getDescription()
        .map(MethodDescription::getDeprecationInfo)
        .orElse("");

      String message = info.getMessage(methodSymbol.getName(), deprecationInfo);

      ranges.stream()
        .filter(range -> symbolTree.getMethodSymbol(range)
          .filter(MethodSymbol::isDeprecated)
          .isEmpty()
        )
        .forEach(range -> diagnosticStorage.addDiagnostic(range, message));
    });
  }
}
