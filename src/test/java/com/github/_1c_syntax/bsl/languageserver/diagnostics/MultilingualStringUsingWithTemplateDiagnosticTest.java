/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2023
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


class MultilingualStringUsingWithTemplateDiagnosticTest
  extends AbstractDiagnosticTest<MultilingualStringUsingWithTemplateDiagnostic> {

  MultilingualStringUsingWithTemplateDiagnosticTest() {
    super(MultilingualStringUsingWithTemplateDiagnostic.class);
  }

  @Test
  void testOnlyRU() {
    List<Diagnostic> diagnostics = getDiagnostics();
    assertThat(diagnostics).hasSize(2);
    assertThat(diagnostics, true)
      .hasRange(19, 38, 19, 89)
      .hasRange(24, 31, 24, 82);
  }

  @Test
  void testRuAndEn() {
    Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("declaredLanguages", "ru,en");
    diagnosticInstance.configure(configuration);

    List<Diagnostic> diagnostics = getDiagnostics();
    assertThat(diagnostics).hasSize(4);
    assertThat(diagnostics, true)
      .hasRange(18, 38, 18, 89)
      .hasRange(19, 38, 19, 89)
      .hasRange(21, 28, 21, 79)
      .hasRange(24, 31, 24, 82);
  }
}