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

import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.description.TypeDescription;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;

import java.util.stream.Collectors;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 5,
  tags = {
    DiagnosticTag.STANDARD,
    DiagnosticTag.BADPRACTICE
  }

)
public class MissingReturnedValueDescriptionDiagnostic extends AbstractDiagnostic {

  /**
   * Анализируется только методы, имеющие описание
   * Для удобства кидается несколько разных замечаний
   */
  @Override
  protected void check() {
    documentContext.getSymbolTree().getMethods().stream()
      .filter((MethodSymbol methodSymbol) -> methodSymbol.getDescription().isPresent()
        && !methodSymbol.getDescription().get().getDescription().isEmpty())
      .forEach((MethodSymbol methodSymbol) -> {
        var returnedValueDescription = methodSymbol.getDescription().get().getReturnedValue();

        // процедура и описания возвращаемого значения нет, все нормально
        if (!methodSymbol.isFunction() && returnedValueDescription.isEmpty()) {
          return;
        }

        // процедура не должна иметь описания
        if (!methodSymbol.isFunction()) {
          diagnosticStorage.addDiagnostic(methodSymbol.getSubNameRange(), info.getResourceString("isProcedure"));
          return;
        }

        // функция без описания - ошибка
        if (returnedValueDescription.isEmpty()) {
          diagnosticStorage.addDiagnostic(methodSymbol.getSubNameRange(), info.getMessage());
          return;
        }

        // тип возвращаемого значения должен иметь описание
        var typesWithoutDescription = returnedValueDescription.stream()
          .filter((TypeDescription typeDescription) -> typeDescription.getDescription().isEmpty())
          .map(TypeDescription::getName)
          .collect(Collectors.joining(", "));
        if (!typesWithoutDescription.isEmpty()) {
          diagnosticStorage.addDiagnostic(methodSymbol.getSubNameRange(),
            info.getResourceString("typesWithoutDescription", typesWithoutDescription));
        }
      });
  }
}
