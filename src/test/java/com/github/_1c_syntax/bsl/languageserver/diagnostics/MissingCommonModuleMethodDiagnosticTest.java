/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2022
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

import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class MissingCommonModuleMethodDiagnosticTest extends AbstractDiagnosticTest<MissingCommonModuleMethodDiagnostic> {

  private static final String PATH_TO_METADATA = "src/test/resources/metadata/designer";

  MissingCommonModuleMethodDiagnosticTest() {
    super(MissingCommonModuleMethodDiagnostic.class);
  }

  @Test
  void test() {
    initServerContext(Absolute.path(PATH_TO_METADATA));

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics);
    assertThat(diagnostics, true)
      .hasRange(1, 4, 41)
      .hasRange(2, 8, 51)
      .hasRange(3, 4, 44)
      .hasRange(4, 4, 48)
      .hasRange(5, 8, 54)

      .hasRange(11, 4, 56)
      .hasRange(12, 8, 30)
      .hasRange(13, 4, 26)
      .hasRange(14, 4, 26)
      .hasRange(15, 8, 30)
      .hasSize(10);
  }

  @Test
  void testWithoutMetadata() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(0);
  }
}
