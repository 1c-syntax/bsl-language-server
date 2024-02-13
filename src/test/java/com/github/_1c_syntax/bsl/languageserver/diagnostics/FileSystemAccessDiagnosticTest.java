/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2024
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

class FileSystemAccessDiagnosticTest extends AbstractDiagnosticTest<FileSystemAccessDiagnostic> {
  FileSystemAccessDiagnosticTest() {
    super(FileSystemAccessDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics, true)
      .hasRange(1, 15, 35)
      .hasRange(2, 15, 41)
      .hasRange(3, 15, 31)
      .hasRange(4, 15, 31)
      .hasRange(5, 15, 38)
      .hasRange(6, 15, 38)
      .hasRange(7, 15, 33)
      .hasRange(8, 15, 44)
      .hasRange(9, 15, 44)
      .hasRange(10, 15, 41)
      .hasRange(11, 15, 41)
      .hasRange(12, 15, 45)
      .hasRange(13, 15, 41)
      .hasRange(14, 15, 56)
      .hasRange(19, 15, 41)
      .hasRange(24, 15, 26)

      .hasRange(29, 4, 17)
      .hasRange(30, 4, 18)
      .hasRange(34, 4, 19)
      .hasRange(36, 4, 19)
      .hasRange(37, 4, 17)
      .hasRange(38, 4, 18)
      .hasRange(39, 4, 16)
      .hasSize(23)
    ;
  }

  @Test
  void testConfigure() {

    Map<String, Object> configuration = diagnosticInstance.info.getDefaultConfiguration();
    configuration.put("globalMethods", "ЗначениеВФайл");
    configuration.put("newExpression", "File");
    diagnosticInstance.configure(configuration);

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics, true)
      .hasRange(1, 15, 35)
      .hasRange(29, 4, 17)
      .hasSize(2);
  }

}
