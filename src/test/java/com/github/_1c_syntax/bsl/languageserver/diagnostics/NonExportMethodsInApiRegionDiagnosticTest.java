/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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

import static org.assertj.core.api.Assertions.assertThat;

class NonExportMethodsInApiRegionDiagnosticTest extends AbstractDiagnosticTest<NonExportMethodsInApiRegionDiagnostic> {

  NonExportMethodsInApiRegionDiagnosticTest() {
    super(NonExportMethodsInApiRegionDiagnostic.class);
  }

  @Test
  void test() {

    // when
    List<Diagnostic> diagnostics = getDiagnostics();

    // then
    assertThat(diagnostics).hasSize(3);
    assertThat(diagnostics)
      // на +
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(8, 10, 8, 16)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(20, 10, 20, 13)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(Ranges.create(25, 14, 25, 27)))
    ;
  }
}
