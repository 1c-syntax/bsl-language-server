/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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

import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class BadWordsDiagnosticTest extends AbstractDiagnosticTest<BadWordsDiagnostic>{
  BadWordsDiagnosticTest() {
    super(BadWordsDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();
    assertThat(diagnostics).isEmpty(); // Проверка количества
  }

  @Test
  void testConfigure() {

    Map<String, Object> configuration = diagnosticInstance.info.getDefaultConfiguration();
    configuration.put("badWords", "лотус|шмотус");
    diagnosticInstance.configure(configuration);

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(6);
    assertThat(diagnostics, true)
      .hasMessageOnRange("В тексте модуля найдено запрещенное слово <лотус>.", 0, 42, 0, 47)
      .hasMessageOnRange("В тексте модуля найдено запрещенное слово <шмотус>.", 0, 48, 0, 54)
      .hasMessageOnRange("В тексте модуля найдено запрещенное слово <Лотус>.", 4, 4, 4, 9)
      .hasMessageOnRange("В тексте модуля найдено запрещенное слово <Лотус>.", 6, 24, 6, 29)
      .hasMessageOnRange("В тексте модуля найдено запрещенное слово <Лотус>.", 6, 34, 6, 39)
      .hasMessageOnRange("В тексте модуля найдено запрещенное слово <Шмотус>.", 8, 4, 8, 10);
  }

  @Test
  void testFindWithoutComments() {

    Map<String, Object> configuration = diagnosticInstance.info.getDefaultConfiguration();
    configuration.put("badWords", "лотус|шмотус");
    configuration.put("findInComments", false);
    diagnosticInstance.configure(configuration);

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(4);
    assertThat(diagnostics, true)
      .hasMessageOnRange("В тексте модуля найдено запрещенное слово <Лотус>.", 4, 4, 4, 9)
      .hasMessageOnRange("В тексте модуля найдено запрещенное слово <Лотус>.", 6, 24, 6, 29)
      .hasMessageOnRange("В тексте модуля найдено запрещенное слово <Лотус>.", 6, 34, 6, 39)
      .hasMessageOnRange("В тексте модуля найдено запрещенное слово <Шмотус>.", 8, 4, 8, 10);
  }
}
