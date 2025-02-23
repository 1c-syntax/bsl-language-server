/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IfElseDuplicatedConditionDiagnosticTest extends AbstractDiagnosticTest<IfElseDuplicatedConditionDiagnostic> {

  IfElseDuplicatedConditionDiagnosticTest() {
    super(IfElseDuplicatedConditionDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();
    assertThat(diagnostics).hasSize(4);

    List<Range> relatedInformation = new ArrayList<>();
    relatedInformation.add(Ranges.create(3, 10, 3, 15));
    relatedInformation.add(Ranges.create(5, 10, 5, 15));
    relatedInformation.add(Ranges.create(9, 10, 9, 21));

    checkDiagnosticContent(
      diagnostics.get(0),
      Ranges.create(3, 10, 3, 15),
      relatedInformation);

    relatedInformation.clear();
    relatedInformation.add(Ranges.create(17, 10, 17, 15));
    relatedInformation.add(Ranges.create(27, 10, 27, 15));

    checkDiagnosticContent(
      diagnostics.get(1),
      Ranges.create(17, 10, 17, 15),
      relatedInformation);

    relatedInformation.clear();
    relatedInformation.add(Ranges.create(20, 13, 20, 18));
    relatedInformation.add(Ranges.create(22, 13, 22, 18));

    checkDiagnosticContent(
      diagnostics.get(2),
      Ranges.create(20, 13, 20, 18),
      relatedInformation);

    relatedInformation.clear();
    relatedInformation.add(Ranges.create(41, 5, 41, 17));
    relatedInformation.add(Ranges.create(43, 10, 43, 22));
    relatedInformation.add(Ranges.create(45, 10, 45, 22));

    checkDiagnosticContent(
      diagnostics.get(3),
      Ranges.create(41, 5, 41, 17),
      relatedInformation);

  }

  private void checkDiagnosticContent(
    Diagnostic diagnostic,
    Range diagnosticRange,
    List<Range> diagnosticRelatedInformation) {

    String relatedMessage = diagnosticInstance.getInfo().getResourceString("identicalConditionRelatedMessage");

    assertThat(diagnostic.getRange()).isEqualTo(diagnosticRange);

    List<DiagnosticRelatedInformation> relatedInformationList = diagnostic.getRelatedInformation();
    assertThat(relatedInformationList).hasSize(diagnosticRelatedInformation.size());

    for (int i = 0; i < relatedInformationList.size(); i++) {
      DiagnosticRelatedInformation relatedInformation = relatedInformationList.get(i);
      assertThat(relatedInformation.getMessage()).isEqualTo(relatedMessage);
      Range range = relatedInformation.getLocation().getRange();
      assertThat(diagnosticRelatedInformation).contains(range);
      if (i == 0)
        assertThat(range).isEqualTo(diagnosticRange);
    }

  }
}
