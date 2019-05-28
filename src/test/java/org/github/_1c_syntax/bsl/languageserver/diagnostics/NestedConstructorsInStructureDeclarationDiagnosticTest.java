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

public class NestedConstructorsInStructureDeclarationDiagnosticTest
  extends AbstractDiagnosticTest<NestedConstructorsInStructureDeclarationDiagnostic>{

  NestedConstructorsInStructureDeclarationDiagnosticTest() {
    super(NestedConstructorsInStructureDeclarationDiagnostic.class);
  }

  private final String relatedMessage = getDiagnosticInstance().getResourceString("nestedConstructorRelatedMessage");

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();
    assertThat(diagnostics).hasSize(8);

    List<Range> relatedInformation = new ArrayList<>();
    relatedInformation.add(RangeHelper.newRange(10, 16, 12, 36));
    relatedInformation.add(RangeHelper.newRange(11, 33, 11, 69));

    checkDiagnosticContent(
      diagnostics.get(0),
      RangeHelper.newRange(10, 16, 12, 36),
      relatedInformation);

    relatedInformation.clear();

    relatedInformation.add(RangeHelper.newRange(14, 16, 23, 62));
    relatedInformation.add(RangeHelper.newRange(19, 32, 19, 93));
    relatedInformation.add(RangeHelper.newRange(20, 32, 20, 82));
    relatedInformation.add(RangeHelper.newRange(22, 32, 22, 91));

    checkDiagnosticContent(
      diagnostics.get(1),
      RangeHelper.newRange(14, 16, 23, 62),
      relatedInformation);

    relatedInformation.clear();

    relatedInformation.add(RangeHelper.newRange(25, 16, 27, 96));
    relatedInformation.add(RangeHelper.newRange(26, 32, 27, 95));

    checkDiagnosticContent(
      diagnostics.get(2),
      RangeHelper.newRange(25, 16, 27, 96),
      relatedInformation);

    relatedInformation.clear();

    relatedInformation.add(RangeHelper.newRange(26, 32, 27, 95));
    relatedInformation.add(RangeHelper.newRange(27, 48, 27, 94));

    checkDiagnosticContent(
      diagnostics.get(3),
      RangeHelper.newRange(26, 32, 27, 95),
      relatedInformation);

    relatedInformation.clear();

    relatedInformation = new ArrayList<>();
    relatedInformation.add(RangeHelper.newRange(38, 13, 40, 31));
    relatedInformation.add(RangeHelper.newRange(39, 28, 39, 55));

    checkDiagnosticContent(
      diagnostics.get(4),
      RangeHelper.newRange(38, 13, 40, 31),
      relatedInformation);

    relatedInformation.clear();

    relatedInformation.add(RangeHelper.newRange(42, 13, 51, 50));
    relatedInformation.add(RangeHelper.newRange(47, 28, 47, 63));
    relatedInformation.add(RangeHelper.newRange(48, 28, 48, 58));
    relatedInformation.add(RangeHelper.newRange(50, 28, 50, 62));

    checkDiagnosticContent(
      diagnostics.get(5),
      RangeHelper.newRange(42, 13, 51, 50),
      relatedInformation);

    relatedInformation.clear();

    relatedInformation.add(RangeHelper.newRange(53, 13, 55, 79));
    relatedInformation.add(RangeHelper.newRange(54, 28, 55, 78));

    checkDiagnosticContent(
      diagnostics.get(6),
      RangeHelper.newRange(53, 13, 55, 79),
      relatedInformation);

    relatedInformation.clear();

    relatedInformation.add(RangeHelper.newRange(54, 28, 55, 78));
    relatedInformation.add(RangeHelper.newRange(55, 44, 55, 77));

    checkDiagnosticContent(
      diagnostics.get(7),
      RangeHelper.newRange(54, 28, 55, 78),
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
