/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
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

import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.mdclasses.metadata.additional.ModuleType;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

class CodeOutOfRegionDiagnosticTest extends AbstractDiagnosticTest<CodeOutOfRegionDiagnostic> {
  CodeOutOfRegionDiagnosticTest() {
    super(CodeOutOfRegionDiagnostic.class);
  }

  @Test
  void test() {
    var diagnostics = getDiagnostics("CodeOutOfRegionDiagnostic", ModuleType.ObjectModule);

    assertThat(diagnostics).hasSize(7);
    assertThat(diagnostics, true)
      .hasRange(4, 0, 8)
      // TODO так вообще то правильно, но пока следующая строка .hasRange(9, 0, 9)
      .hasRange(8, 0, 9, 9)
      .hasRange(17, 10, 13)
      .hasRange(24, 10, 12)
      .hasRange(46, 0, 13)
      .hasRange(57, 0, 7)
      .hasRange(59, 0, 69, 9)
    ;
  }

  @Test
  void emptyTest() {
    var diagnostics = getDiagnostics("CodeOutOfRegionDiagnosticEmpty", ModuleType.ObjectModule);
    assertThat(diagnostics).isEmpty();
  }

  @Test
  void testNoRegions() {
    var diagnostics = getDiagnostics("CodeOutOfRegionDiagnosticNoRegions", ModuleType.ObjectModule);

    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true)
      .hasRange(4, 0, 5, 8)
    ;
    assertThat(diagnostics.get(0).getRelatedInformation())
      .isNotNull()
      .isNotEmpty();
    assertThat(diagnostics.get(0).getRelatedInformation().size()).isEqualTo(4);
  }

  @Test
  void testNoRegionsUnknown() {
    var diagnostics = getDiagnostics("CodeOutOfRegionDiagnosticNoRegions", ModuleType.UNKNOWN);
    assertThat(diagnostics).isEmpty();
  }

  @Test
  void testCodeBlock() {
    var diagnostics = getDiagnostics("CodeOutOfRegionDiagnosticCodeBlock", ModuleType.ObjectModule);

    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true)
      .hasRange(0, 0, 0, 23);
  }


  @Test
  void testEmptyFile() {
    var diagnostics = getDiagnostics("CodeOutOfRegionDiagnosticEmptyFile", ModuleType.ObjectModule);
    assertThat(diagnostics).isEmpty();
  }

  @Test
  void testExecute() {
    var diagnostics = getDiagnostics("CodeOutOfRegionDiagnosticExecute", ModuleType.ObjectModule);

    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true)
      .hasRange(1, 10, 1, 19);
  }

  private List<Diagnostic> getDiagnostics(String fileName, ModuleType moduleType) {
    var documentContext = spy(TestUtils.getDocumentContext(getText(fileName)));
    doReturn(moduleType).when(documentContext).getModuleType();
    return getDiagnostics(documentContext);
  }
}
