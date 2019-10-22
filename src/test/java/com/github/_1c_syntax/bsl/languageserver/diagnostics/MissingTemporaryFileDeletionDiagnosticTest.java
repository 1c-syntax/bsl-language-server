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

import com.github._1c_syntax.bsl.languageserver.providers.DiagnosticProvider;
import com.github._1c_syntax.bsl.languageserver.utils.RangeHelper;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MissingTemporaryFileDeletionDiagnosticTest extends AbstractDiagnosticTest<MissingTemporaryFileDeletionDiagnostic> {
  MissingTemporaryFileDeletionDiagnosticTest() {
    super(MissingTemporaryFileDeletionDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(3);

    assertThat(diagnostics)
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(6, 29, 6, 62)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(17, 30, 17, 63)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(21, 30, 21, 63)))
    ;

  }

  @Test
  void testConfig() {

    List<Diagnostic> diagnostics;
    Map<String, Object> configuration;

    configuration = DiagnosticProvider.getDefaultDiagnosticConfiguration(getDiagnosticInstance());
    getDiagnosticInstance().configure(configuration);
    diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(3);

    configuration = DiagnosticProvider.getDefaultDiagnosticConfiguration(getDiagnosticInstance());
    configuration.put(
      "searchDeleteFileMethod",
      MissingTemporaryFileDeletionDiagnostic.REGEX_DELETION_FILE + "|РаботаСФайламиСлужебныйКлиент.УдалитьФайл");
    getDiagnosticInstance().configure(configuration);
    diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(2);

  }
}
