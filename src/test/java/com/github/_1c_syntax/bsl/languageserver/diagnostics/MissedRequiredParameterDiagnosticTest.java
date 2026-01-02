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

import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

@CleanupContextBeforeClassAndAfterEachTestMethod
class MissedRequiredParameterDiagnosticTest extends AbstractDiagnosticTest<MissedRequiredParameterDiagnostic> {
  private static final String PATH_TO_METADATA = "src/test/resources/metadata/designer";

  MissedRequiredParameterDiagnosticTest() {
    super(MissedRequiredParameterDiagnostic.class);
  }

  @Test
  void testLocalMethod() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(5);
    assertThat(diagnostics, true)
      .hasRange(2, 16, 2, 29)
      .hasRange(8, 16, 8, 27)
      .hasRange(14, 16, 14, 26)
      .hasRange(17, 13, 17, 24)
      .hasRange(18, 13, 18, 35)
    ;
  }

  @Test
  void testSideMethod() {

    initServerContext(Absolute.path(PATH_TO_METADATA));

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(9);
    assertThat(diagnostics, true)
      .hasRange(2, 16, 2, 29)
      .hasRange(8, 16, 8, 27)
      .hasRange(14, 16, 14, 26)
      .hasRange(17, 13, 17, 24)
      .hasRange(18, 13, 18, 35)
      .hasRange(24, 22, 24, 49)
      .hasRange(25, 22, 25, 50)
      .hasRange(26, 22, 26, 48)
      .hasRange(27, 31, 27, 57)
    ;
  }
}
