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
package org.github._1c_syntax.bsl.languageserver.diagnostics;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;
import org.eclipse.lsp4j.Range;
import org.github._1c_syntax.bsl.languageserver.utils.RangeHelper;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

public class IfElseDuplicatedConditionDiagnosticTest extends AbstractDiagnosticTest<IfElseDuplicatedConditionDiagnostic> {

  IfElseDuplicatedConditionDiagnosticTest() {
    super(IfElseDuplicatedConditionDiagnostic.class);
  }

  private final String relatedMessage = getDiagnosticInstance().getResourceString("identicalConditionRelatedMessage");

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();
    assertThat(diagnostics).hasSize(4);

    List<Range> relatedInformation = new ArrayList<>();
    relatedInformation.add(RangeHelper.newRange(3, 10, 3, 15));
    relatedInformation.add(RangeHelper.newRange(5, 10, 5, 15));
    relatedInformation.add(RangeHelper.newRange(9, 10, 9, 21));

    checkDiagnosticContent(
      diagnostics.get(0),
      RangeHelper.newRange(3, 10, 3, 15),
      relatedInformation);

    relatedInformation.clear();
    relatedInformation.add(RangeHelper.newRange(17, 10, 17, 15));
    relatedInformation.add(RangeHelper.newRange(27, 10, 27, 15));

    checkDiagnosticContent(
      diagnostics.get(1),
      RangeHelper.newRange(17, 10, 17, 15),
      relatedInformation);

    relatedInformation.clear();
    relatedInformation.add(RangeHelper.newRange(20, 13, 20, 18));
    relatedInformation.add(RangeHelper.newRange(22, 13, 22, 18));

    checkDiagnosticContent(
      diagnostics.get(2),
      RangeHelper.newRange(20, 13, 20, 18),
      relatedInformation);

    relatedInformation.clear();
    relatedInformation.add(RangeHelper.newRange(41, 5, 41, 17));
    relatedInformation.add(RangeHelper.newRange(43, 10, 43, 22));
    relatedInformation.add(RangeHelper.newRange(45, 10, 45, 22));

    checkDiagnosticContent(
      diagnostics.get(3),
      RangeHelper.newRange(41, 5, 41, 17),
      relatedInformation);

  }

  private void checkDiagnosticContent(
    Diagnostic diagnostic,
    Range diagnosticRange,
    List<Range> diagnosticRelatedInformation) {

    assertThat(diagnostic.getRange()).isEqualTo(diagnosticRange);

    List<DiagnosticRelatedInformation> relatedInformationList = diagnostic.getRelatedInformation();
    assertThat(relatedInformationList).hasSize(diagnosticRelatedInformation.size());

    for (int i = 0; i<relatedInformationList.size(); i++) {
      DiagnosticRelatedInformation relatedInformation = relatedInformationList.get(i);
      assertThat(relatedInformation.getMessage()).isEqualTo(relatedMessage);
      Range range = relatedInformation.getLocation().getRange();
      assertThat(diagnosticRelatedInformation).contains(range);
      if(i==0)
        assertThat(range).isEqualTo(diagnosticRange);
    }

  }
}
