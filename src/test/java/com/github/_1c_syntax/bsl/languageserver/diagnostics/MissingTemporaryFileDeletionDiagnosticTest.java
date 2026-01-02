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

import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class MissingTemporaryFileDeletionDiagnosticTest extends AbstractDiagnosticTest<MissingTemporaryFileDeletionDiagnostic> {
  MissingTemporaryFileDeletionDiagnosticTest() {
    super(MissingTemporaryFileDeletionDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics, true)
      .hasRange(6, 29, 62)
      .hasRange(19, 30, 63)
      .hasRange(25, 30, 63)
      .hasRange(45, 29, 62)
      .hasRange(49, 30, 63)
      .hasRange(64, 30, 58)
      .hasRange(71, 26, 54)
      .hasSize(7)
    ;

  }

  @Test
  void testConfig() {

    List<Diagnostic> diagnostics;
    Map<String, Object> configuration;

    configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    diagnosticInstance.configure(configuration);
    diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(7);

    configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put(
      "searchDeleteFileMethod",
      "УдалитьФайлы|DeleteFiles|НачатьУдалениеФайлов|BeginDeletingFiles|ПереместитьФайл|MoveFile"
        + "|РаботаСФайламиСлужебныйКлиент.УдалитьФайл|Справочники.ОбщийМодуль.УдалитьВсеФайлы");
    diagnosticInstance.configure(configuration);
    diagnostics = getDiagnostics();

    assertThat(diagnostics, true)
      .hasRange(6, 29, 62)
      .hasRange(25, 30, 63)
      .hasRange(49, 30, 63)
      .hasRange(64, 30, 58)
      .hasRange(71, 26, 54)
      .hasSize(5)
    ;
  }

  @Test
  void testIncorrectConfig() {

    var configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put(
      "searchDeleteFileMethod",
      "УдалитьФайл|DeleteFile|НачатьУдалениеФайловВсех"
        + "|ОбщийМодуль.УдалитьВсеФайлы");
    diagnosticInstance.configure(configuration);
    var diagnostics = getDiagnostics();

    assertThat(diagnostics, true)
      .hasRange(6, 29, 62)
      .hasRange(9, 30, 63)
      .hasRange(13, 30, 63)
      .hasRange(19, 30, 63)
      .hasRange(25, 30, 63)
      .hasRange(30, 30, 63)
      .hasRange(34, 16, 38)
      .hasRange(45, 29, 62)
      .hasRange(49, 30, 63)
      .hasRange(60, 29, 57)
      .hasRange(64, 30, 58)
      .hasRange(71, 26, 54)
      .hasSize(12)
    ;
  }
}
