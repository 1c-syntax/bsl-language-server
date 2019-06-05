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
import org.github._1c_syntax.bsl.languageserver.providers.DiagnosticProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;


public class NestedFunctionsInStructureDeclarationDiagnosticTest
  extends AbstractDiagnosticTest<NestedFunctionsInStructureDeclarationDiagnostic> {

  private final String relatedMessage = getDiagnosticInstance().getResourceString("nestedFunctionRelatedMessage");

  NestedFunctionsInStructureDeclarationDiagnosticTest() {
    super(NestedFunctionsInStructureDeclarationDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();
    assertThat(diagnostics).hasSize(16);
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

  @Test
  void testConfigure() {

    Map<String, Object> configuration = DiagnosticProvider.getDefaultDiagnosticConfiguration(getDiagnosticInstance());
    configuration.put("maxValuesCount", 3);
    getDiagnosticInstance().configure(configuration);

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(4);
  }

}
