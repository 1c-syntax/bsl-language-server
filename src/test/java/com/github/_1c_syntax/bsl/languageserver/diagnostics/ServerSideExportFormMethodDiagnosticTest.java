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

import com.github._1c_syntax.bsl.mdo.Form;
import com.github._1c_syntax.bsl.mdo.support.FormType;
import com.github._1c_syntax.bsl.types.ModuleType;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class ServerSideExportFormMethodDiagnosticTest extends AbstractDiagnosticTest<ServerSideExportFormMethodDiagnostic> {
  ServerSideExportFormMethodDiagnosticTest() {
    super(ServerSideExportFormMethodDiagnostic.class);
  }

  private static final String PATH_TO_METADATA = "src/test/resources/metadata/designer";
  private Form form;

  @BeforeEach
  void beforeTest() {
    initServerContext(Absolute.path(PATH_TO_METADATA));
    form = spy((Form) context.getConfiguration().getPlainChildren().stream()
      .filter(mdo -> mdo.getName().equalsIgnoreCase("ФормаЭлемента"))
      .findFirst()
      .get());
  }

  @Test
  void test() {

    var documentContext = spy(getDocumentContext());
    when(documentContext.getModuleType()).thenReturn(ModuleType.FormModule);
    when(documentContext.getMdObject()).thenReturn(Optional.of(form));
    when(form.getFormType()).thenReturn(FormType.MANAGED);

    List<Diagnostic> diagnostics = diagnosticInstance.getDiagnostics(documentContext);

    assertThat(diagnostics).hasSize(3);
    assertThat(diagnostics, true)
      .hasRange(0, 10, 22)
      .hasRange(8, 10, 19)
      .hasRange(12, 10, 31)
    ;
  }

  @Test
  void testWithoutMock() {
    List<Diagnostic> diagnostics = getDiagnostics();
    assertThat(diagnostics).isEmpty();
  }

  @Test
  void testOrdinaryForm() {
    var documentContext = spy(getDocumentContext());
    when(documentContext.getModuleType()).thenReturn(ModuleType.FormModule);
    when(documentContext.getMdObject()).thenReturn(Optional.of(form));
    when(form.getFormType()).thenReturn(FormType.ORDINARY);

    List<Diagnostic> diagnostics = diagnosticInstance.getDiagnostics(documentContext);
    assertThat(diagnostics).isEmpty();
  }

  @Test
  void testBroken() {
    var documentContext = spy(getDocumentContext());
    when(documentContext.getModuleType()).thenReturn(ModuleType.FormModule);
    when(documentContext.getMdObject()).thenReturn(Optional.empty());
    when(form.getFormType()).thenReturn(FormType.MANAGED);

    List<Diagnostic> diagnostics = diagnosticInstance.getDiagnostics(documentContext);
    assertThat(diagnostics).isEmpty();
  }
}
