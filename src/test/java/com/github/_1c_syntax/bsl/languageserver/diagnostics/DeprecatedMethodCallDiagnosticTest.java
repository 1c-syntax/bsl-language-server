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

import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

@CleanupContextBeforeClassAndAfterClass
class DeprecatedMethodCallDiagnosticTest extends AbstractDiagnosticTest<DeprecatedMethodCallDiagnostic> {
  DeprecatedMethodCallDiagnosticTest() {
    super(DeprecatedMethodCallDiagnostic.class);
  }

  @Test
  void test() {

    // given
    initServerContext(TestUtils.PATH_TO_METADATA);

    // when
    List<Diagnostic> diagnostics = getDiagnostics();

    // then
    assertThat(diagnostics).hasSize(8);
    assertThat(diagnostics, true)
      .hasRange(1, 18, 1, 37)
      .hasRange(4, 18, 4, 35)
      .hasRange(7, 22, 7, 39)
      .hasRange(10, 23, 10, 40)
      .hasRange(16, 34, 16, 53)
      .hasRange(19, 38, 19, 55)
      .hasRange(22, 39, 22, 56)
      .hasRange(28, 0, 28, 19)
    ;

  }
}
