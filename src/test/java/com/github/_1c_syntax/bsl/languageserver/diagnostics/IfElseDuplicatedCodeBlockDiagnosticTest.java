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
import org.eclipse.lsp4j.DiagnosticRelatedInformation;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IfElseDuplicatedCodeBlockDiagnosticTest extends AbstractDiagnosticTest<IfElseDuplicatedCodeBlockDiagnostic> {

  IfElseDuplicatedCodeBlockDiagnosticTest() {
    super(IfElseDuplicatedCodeBlockDiagnostic.class);
  }

  @Test
  void test() {
    List<Diagnostic> diagnostics = getDiagnostics();
    assertThat(diagnostics).hasSize(5);

    checkDiagnosticContent(
      diagnostics.get(0),
      Ranges.create(9, 1, 10, 9),
      Ranges.create(12, 1, 13, 9)
    );

    checkDiagnosticContent(
      diagnostics.get(1),
      Ranges.create(26, 1, 27, 9),
      Ranges.create(29, 1, 30, 9)
    );

    checkDiagnosticContent(
      diagnostics.get(2),
      Ranges.create(39, 1, 47, 11),
      Ranges.create(52, 1, 60, 11)
    );

    checkDiagnosticContent(
      diagnostics.get(3),
      Ranges.create(40, 2, 41, 10),
      Ranges.create(43, 2, 44, 10)
    );

    checkDiagnosticContent(
      diagnostics.get(4),
      Ranges.create(53, 2, 54, 10),
      Ranges.create(56, 2, 57, 10)
    );

  }

  private void checkDiagnosticContent(
    Diagnostic diagnostic,
    Range diagnosticRange,
    Range relatedLocationRange
  ) {
    assertThat(diagnostic.getRange()).isEqualTo(diagnosticRange);
    List<DiagnosticRelatedInformation> relatedInformationList = diagnostic.getRelatedInformation();
    assertThat(relatedInformationList).hasSize(2);

    String relatedMessage = diagnosticInstance.getInfo().getResourceString("identicalCodeBlockRelatedMessage");

    DiagnosticRelatedInformation relatedInformation = relatedInformationList.get(0);
    assertThat(relatedInformation.getMessage()).isEqualTo(relatedMessage);
    assertThat(relatedInformation.getLocation().getRange()).isEqualTo(diagnosticRange);

    relatedInformation = relatedInformationList.get(1);
    assertThat(relatedInformation.getMessage()).isEqualTo(relatedMessage);
    assertThat(relatedInformation.getLocation().getRange()).isEqualTo(relatedLocationRange);
  }
}
