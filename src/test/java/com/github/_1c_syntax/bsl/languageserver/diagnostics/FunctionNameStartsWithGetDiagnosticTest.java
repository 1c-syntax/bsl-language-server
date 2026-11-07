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

import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class FunctionNameStartsWithGetDiagnosticTest extends AbstractDiagnosticTest<FunctionNameStartsWithGetDiagnostic> {
  FunctionNameStartsWithGetDiagnosticTest() {
    super(FunctionNameStartsWithGetDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true)
      .hasRange(0, 8, 0, 25);

  }

  @ParameterizedTest
  @CsvSource({
    "АЯ_, АЯ_ПолучитьВажныеДанные, 1",
    "ая_, АЯ_пОлУчИтЬВажныеДанные, 1",
    "'АЯ_, БСП_', БСП_ПолучитьДанные, 1",
    "' , АЯ_ , , БСП_ , ', АЯ_ПолучитьДанные, 1",
    "'А, АЯ_', АЯ_ПолучитьДанные, 1",
    "'АЯ_, АЯ_', АЯ_ПолучитьДанные, 1",
    "АЯ_, ПолучитьДанные, 1",
    "'', ПолучитьДанные, 1",
    "' , , ', ПолучитьДанные, 1",
    "'', АЯ_ПолучитьДанные, 0",
    "БСП_, АЯ_ПолучитьДанные, 0",
    "АЯ_, АЯ_Данные, 0",
    "АЯ_, ДанныеАЯ_Получить, 0",
    "АЯ_, АЯ_АЯ_ПолучитьДанные, 0",
    "АЯ_, АЯ_GetData, 0",
    "АЯ_, GetData, 0",
    "АЯ., АЯ_ПолучитьДанные, 0",
    "АЯ_|БСП_, АЯ_ПолучитьДанные, 0",
    "[, АЯ_ПолучитьДанные, 0"
  })
  void testPrefixes(String prefixes, String name, int expectedCount) {
    diagnosticInstance.configure(Map.of("prefixes", prefixes));
    var document = TestUtils.getDocumentContext("Функция " + name + "()\nКонецФункции", context);

    var diagnostics = getDiagnostics(document);

    assertThat(diagnostics).hasSize(expectedCount);
    if (expectedCount > 0) {
      assertThat(diagnostics, true).hasRange(0, 8, 0, 8 + name.length());
    }
  }

  @Test
  void testPrefixedProcedure() {
    diagnosticInstance.configure(Map.of("prefixes", "АЯ_"));
    var document = TestUtils.getDocumentContext("Процедура АЯ_ПолучитьДанные()\nКонецПроцедуры", context);

    assertThat(getDiagnostics(document)).isEmpty();
  }

  @Test
  void testResetPrefixes() {
    var document = TestUtils.getDocumentContext("Функция АЯ_ПолучитьДанные()\nКонецФункции", context);
    diagnosticInstance.configure(Map.of("prefixes", "АЯ_"));
    assertThat(getDiagnostics(document)).hasSize(1);

    diagnosticInstance.configure(Map.of());
    assertThat(getDiagnostics(document)).isEmpty();
  }
}
