/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2021
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
import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.references.Reference;
import lombok.RequiredArgsConstructor;

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
  private final ReferencesStorage referencesStorage;

  @Override
  public void check() {
    var uri = documentContext.getUri();

    referencesStorage.getReferencesFrom(uri).stream()
      .filter(reference -> reference.getSymbol().isDeprecated())
      .filter(reference -> !reference.getFrom().isDeprecated())
      .forEach((Reference reference) -> {
        Symbol deprecatedSymbol = reference.getSymbol();
        String deprecationInfo = getDeprecationInfo(deprecatedSymbol);
        String message = info.getMessage(deprecatedSymbol.getName(), deprecationInfo);
        diagnosticStorage.addDiagnostic(reference.getSelectionRange(), message);
      });
  }

  // TODO: Подумать: новый интерфейс Description с базовыми методами описания всех Symbol/SourceDefinedSymbol, в т.ч.
  //  getDeprecationInfo и getPurpose. Добавить в Symbol/SourceDefinedSymbol поле description.
  //  Это позволит унифицировать работу с описаниями всех символов.
  private static String getDeprecationInfo(Symbol deprecatedSymbol) {
    return Optional.of(deprecatedSymbol)
      .filter(MethodSymbol.class::isInstance)
      .map(MethodSymbol.class::cast)
      .flatMap(MethodSymbol::getDescription)
      .map(MethodDescription::getDeprecationInfo)
      .orElse("");
  }
}
