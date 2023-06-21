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

import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import jakarta.annotation.PostConstruct;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NestedConstructorsInStructureDeclarationDiagnosticTest
  extends AbstractDiagnosticTest<NestedConstructorsInStructureDeclarationDiagnostic> {

  NestedConstructorsInStructureDeclarationDiagnosticTest() {
    super(NestedConstructorsInStructureDeclarationDiagnostic.class);
  }

  private String relatedMessage;

  @PostConstruct
  void before() {
    relatedMessage = diagnosticInstance.getInfo().getResourceString("nestedConstructorRelatedMessage");
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();
    assertThat(diagnostics).hasSize(8);

    List<Range> relatedInformation = new ArrayList<>();
    relatedInformation.add(Ranges.create(10, 16, 12, 36));
    relatedInformation.add(Ranges.create(11, 33, 11, 69));

    checkDiagnosticContent(
      diagnostics.get(0),
      Ranges.create(10, 16, 12, 36),
      relatedInformation);

    relatedInformation.clear();

    relatedInformation.add(Ranges.create(14, 16, 23, 62));
    relatedInformation.add(Ranges.create(19, 32, 19, 93));
    relatedInformation.add(Ranges.create(20, 32, 20, 82));
    relatedInformation.add(Ranges.create(22, 32, 22, 91));

    checkDiagnosticContent(
      diagnostics.get(1),
      Ranges.create(14, 16, 23, 62),
      relatedInformation);

    relatedInformation.clear();

    relatedInformation.add(Ranges.create(25, 16, 27, 96));
    relatedInformation.add(Ranges.create(26, 32, 27, 95));

    checkDiagnosticContent(
      diagnostics.get(2),
      Ranges.create(25, 16, 27, 96),
      relatedInformation);

    relatedInformation.clear();

    relatedInformation.add(Ranges.create(26, 32, 27, 95));
    relatedInformation.add(Ranges.create(27, 48, 27, 94));

    checkDiagnosticContent(
      diagnostics.get(3),
      Ranges.create(26, 32, 27, 95),
      relatedInformation);

    relatedInformation.clear();

    relatedInformation = new ArrayList<>();
    relatedInformation.add(Ranges.create(38, 13, 40, 31));
    relatedInformation.add(Ranges.create(39, 28, 39, 55));

    checkDiagnosticContent(
      diagnostics.get(4),
      Ranges.create(38, 13, 40, 31),
      relatedInformation);

    relatedInformation.clear();

    relatedInformation.add(Ranges.create(42, 13, 51, 50));
    relatedInformation.add(Ranges.create(47, 28, 47, 63));
    relatedInformation.add(Ranges.create(48, 28, 48, 58));
    relatedInformation.add(Ranges.create(50, 28, 50, 62));

    checkDiagnosticContent(
      diagnostics.get(5),
      Ranges.create(42, 13, 51, 50),
      relatedInformation);

    relatedInformation.clear();

    relatedInformation.add(Ranges.create(53, 13, 55, 79));
    relatedInformation.add(Ranges.create(54, 28, 55, 78));

    checkDiagnosticContent(
      diagnostics.get(6),
      Ranges.create(53, 13, 55, 79),
      relatedInformation);

    relatedInformation.clear();

    relatedInformation.add(Ranges.create(54, 28, 55, 78));
    relatedInformation.add(Ranges.create(55, 44, 55, 77));

    checkDiagnosticContent(
      diagnostics.get(7),
      Ranges.create(54, 28, 55, 78),
      relatedInformation);

  }

  private void checkDiagnosticContent(
    Diagnostic diagnostic,
    Range diagnosticRange,
    List<Range> diagnosticRelatedInformation) {

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
