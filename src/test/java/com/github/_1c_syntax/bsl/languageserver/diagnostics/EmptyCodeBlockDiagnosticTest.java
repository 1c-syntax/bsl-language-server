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

import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;


class EmptyCodeBlockDiagnosticTest extends AbstractDiagnosticTest<EmptyCodeBlockDiagnostic> {

  EmptyCodeBlockDiagnosticTest() {
    super(EmptyCodeBlockDiagnostic.class);
  }

  @Test
  void test() {
    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(6);
    assertThat(diagnostics, true)
      .hasRange(5, 1, 5, 6)
      .hasRange(17, 2, 17, 18)
      .hasRange(24, 4, 24, 21)
      .hasRange(35, 0, 35, 16)
      .hasRange(37, 0, 37, 21)
      .hasRange(38, 4, 38, 9);
  }

  @Test
  void testFileEmptyCodeBlock() {

    List<Diagnostic> diagnostics = getDiagnostics("EmptyCodeBlockDiagnosticFileCodeBlock");

    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics.get(0).getRange()).isEqualTo(Ranges.create(4, 4, 4, 16));
  }

  @Test
  void testConfigure() {

    Map<String, Object> configuration = diagnosticInstance.info.getDefaultConfiguration();
    configuration.put("commentAsCode", true);
    diagnosticInstance.configure(configuration);

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(2);
    assertThat(diagnostics, true)
      .hasRange(24, 4, 24, 21)
      .hasRange(37, 0, 37, 21);
  }

  @Test
  void testConfigureFile() {

    Map<String, Object> configuration = diagnosticInstance.info.getDefaultConfiguration();
    configuration.put("commentAsCode", true);
    diagnosticInstance.configure(configuration);

    List<Diagnostic> diagnostics = getDiagnostics("EmptyCodeBlockDiagnosticFileCodeBlock");

    assertThat(diagnostics).isEmpty();
  }
}
