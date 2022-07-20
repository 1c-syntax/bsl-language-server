/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2022
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

import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.types.ModuleType;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

class CommonModuleMissingAPIDiagnosticTest extends AbstractDiagnosticTest<CommonModuleMissingAPIDiagnostic> {
  CommonModuleMissingAPIDiagnosticTest() {
    super(CommonModuleMissingAPIDiagnostic.class);
  }

  @Test
  void positiveTest() {
    var diagnostics = getDiagnostics("CommonModuleMissingAPIDiagnostic", ModuleType.CommonModule);
    assertThat(diagnostics).isEmpty();
  }

  @Test
  void emptyFileTest() {
    var diagnostics = getDiagnostics("CommonModuleMissingAPIDiagnosticEmptyFile", ModuleType.CommonModule);
    assertThat(diagnostics).isEmpty();
  }

  @Test
  void noExportSubsTest() {
    var diagnostics = getDiagnostics("CommonModuleMissingAPIDiagnosticNoExportSubs", ModuleType.CommonModule);
    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true)
      .hasRange(0, 0, 66);

  }

  @Test
  void noRegionsAPITest() {
    var diagnostics = getDiagnostics("CommonModuleMissingAPIDiagnosticNoRegionsAPI", ModuleType.CommonModule);
    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true)
      .hasRange(0, 0, 111);
  }

  @Test
  void noSubsTest() {
    var diagnostics = getDiagnostics("CommonModuleMissingAPIDiagnosticNoSubs", ModuleType.CommonModule);
    assertThat(diagnostics).isEmpty();
  }

  private List<Diagnostic> getDiagnostics(String fileName, ModuleType moduleType) {
    var documentContext = spy(TestUtils.getDocumentContext(getText(fileName)));
    doReturn(moduleType).when(documentContext).getModuleType();
    return getDiagnostics(documentContext);
  }

}
