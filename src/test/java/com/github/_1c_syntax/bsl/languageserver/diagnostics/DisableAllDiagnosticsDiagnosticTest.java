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

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;
import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.getDocumentContextFromFile;

class DisableAllDiagnosticsDiagnosticTest extends AbstractDiagnosticTest<DisableAllDiagnosticsDiagnostic> {
  DisableAllDiagnosticsDiagnosticTest() {
    super(DisableAllDiagnosticsDiagnostic.class);
  }

  @Test
  void test() {

    String filePath = "./src/test/resources/context/computer/DiagnosticIgnoranceComputerTest.bsl";
    final var documentContext = getDocumentContextFromFile(filePath);

    List<Diagnostic> diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics).hasSize(2);
    assertThat(diagnostics, true)
      .hasRange(0, 0, 12)
      .hasRange(33, 0, 13);
  }

  @Test
  void testOnAndOff() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true)
      .hasRange(9, 0, 13);

  }
}
