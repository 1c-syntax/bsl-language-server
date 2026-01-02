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

import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.description.MethodDescription;
import com.github._1c_syntax.bsl.languageserver.context.symbol.description.TypeDescription;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;

import java.util.Collections;
import java.util.List;
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
public class MissingReturnedValueDescriptionDiagnostic extends AbstractSymbolTreeDiagnostic {

  private static final boolean ALLOW_SHORT_DESCRIPTION_RETURN_VALUES = true;

  @DiagnosticParameter(
    type = Boolean.class,
    defaultValue = "" + ALLOW_SHORT_DESCRIPTION_RETURN_VALUES
  )
  private boolean allowShortDescriptionReturnValues = ALLOW_SHORT_DESCRIPTION_RETURN_VALUES;

  /**
   * Анализируется только методы, имеющие описание
   * Для удобства кидается несколько разных замечаний
   */
  @Override
  public void visitMethod(MethodSymbol methodSymbol) {

    var description = methodSymbol.getDescription();

    boolean hasDescription = description.isPresent();

    if (!hasDescription) {
      return;
    }

    List<TypeDescription> returnedValueDescription = description
      .map(MethodDescription::getReturnedValue)
      .orElse(Collections.emptyList());

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
      if (!description.get().getLink().isEmpty()) {
        // пока считаем ссылку наличием описания всего и вся
        return;
      }
      diagnosticStorage.addDiagnostic(methodSymbol.getSubNameRange(), info.getMessage());
      return;
    }

    // разрешено краткое описание параметра, поэтому больше не проверяем
    if (allowShortDescriptionReturnValues) {
      return;
    }

    // тип возвращаемого значения должен иметь описание или быть сложным
    var typesWithoutDescription = returnedValueDescription.stream()
      .filter((TypeDescription typeDescription) ->
        typeDescription.description().isEmpty() && typeDescription.parameters().isEmpty())
      .map(TypeDescription::name)
      .collect(Collectors.joining(", "));
    if (!typesWithoutDescription.isEmpty()) {
      diagnosticStorage.addDiagnostic(methodSymbol.getSubNameRange(),
        info.getResourceString("typesWithoutDescription", typesWithoutDescription));
    }
  }
}
