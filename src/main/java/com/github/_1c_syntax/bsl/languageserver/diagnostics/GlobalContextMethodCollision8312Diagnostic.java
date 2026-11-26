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

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticCompatibilityMode;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.utils.CaseInsensitivePattern;

import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.BLOCKER,
  minutesToFix = 10,
  tags = {
    DiagnosticTag.ERROR,
    DiagnosticTag.UNPREDICTABLE
  },
  compatibilityMode = DiagnosticCompatibilityMode.COMPATIBILITY_MODE_8_3_12

)
public class GlobalContextMethodCollision8312Diagnostic extends AbstractDiagnostic {

  private static final Pattern COLLISION_METHODS = CaseInsensitivePattern.compile("(ПроверитьБит|ПроверитьПоБитовойМаске|УстановитьБит|ПобитовоеИ|ПобитовоеИли|ПобитовоеНе|ПобитовоеИНе|" +
    "ПобитовоеИсключительноеИли|ПобитовыйСдвигВлево|ПобитовыйСдвигВправо|" +
    "CheckBit|CheckByBitMask|SetBit|BitwiseAnd|BitwiseOr|BitwiseNot|BitwiseAndNot|BitwiseXor|BitwiseShiftLeft|" +
    "BitwiseShiftRight)");

  @Override
  protected void check() {
    documentContext.getSymbolTree().getMethods().stream()
      .filter(method -> COLLISION_METHODS.matcher(method.getName()).matches())
      .forEach(method ->
        diagnosticStorage.addDiagnostic(
          method.getSubNameRange(),
          info.getMessage(method.getName())
        )
      );
  }
}
