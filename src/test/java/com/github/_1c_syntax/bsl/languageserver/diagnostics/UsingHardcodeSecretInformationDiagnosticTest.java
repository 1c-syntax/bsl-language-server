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


class UsingHardcodeSecretInformationDiagnosticTest extends AbstractDiagnosticTest<UsingHardcodeSecretInformationDiagnostic> {
  UsingHardcodeSecretInformationDiagnosticTest() {
    super(UsingHardcodeSecretInformationDiagnostic.class);
  }

  @Test
  void test() {

    // when
    List<Diagnostic> diagnostics = getDiagnostics();

    // then
    assertThat(diagnostics).hasSize(12);

    assertThat(diagnostics, true)
      .hasRange(8, 4, 8, 49)
      .hasRange(12, 4, 12, 80)
      .hasRange(16, 4, 16, 23)
      .hasRange(17, 4, 17, 23)
      .hasRange(27, 4, 27, 35)
      .hasRange(32, 4, 32, 27)
      .hasRange(33, 4, 33, 31)
      .hasRange(44, 4, 44, 82)
      .hasRange(45, 4, 45, 79)
      .hasRange(48, 4, 48, 22)
      .hasRange(49, 4, 49, 21)
      .hasRange(50, 4, 50, 21)
    ;

  }

  @Test
  void testConfigure() {

    List<Diagnostic> diagnostics;
    Map<String, Object> configuration;

    // без изменения параметра
    // when
    configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    diagnosticInstance.configure(configuration);
    diagnostics = getDiagnostics();

    // then
    assertThat(diagnostics).hasSize(12);

    // с изменением параметра searchWords
    // when
    configuration.put("searchWords", "Password");
    diagnosticInstance.configure(configuration);
    diagnostics = getDiagnostics();

    // then
    assertThat(diagnostics).hasSize(4);

  }

}
