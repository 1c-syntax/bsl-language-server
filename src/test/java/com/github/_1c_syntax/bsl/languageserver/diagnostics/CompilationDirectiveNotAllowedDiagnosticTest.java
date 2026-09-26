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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.mdo.Form;
import com.github._1c_syntax.bsl.mdo.support.FormType;
import com.github._1c_syntax.bsl.types.ModuleType;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class CompilationDirectiveNotAllowedDiagnosticTest extends AbstractDiagnosticTest<CompilationDirectiveNotAllowedDiagnostic> {
  CompilationDirectiveNotAllowedDiagnosticTest() {
    super(CompilationDirectiveNotAllowedDiagnostic.class);
  }

  @Test
  void testFormModule() {
    List<Diagnostic> diagnostics = getDiagnostics(documentContextOf(ModuleType.FormModule));

    assertThat(diagnostics).hasSize(2);
    assertThat(diagnostics, true)
      .hasMessageOnRange("Директива компиляции \"&НаКлиентеНаСервере\" недоступна в модуле этого вида", 16, 0, 16, 19)
      .hasRange(20, 0, 20, 17);
  }

  @Test
  void testCommandModule() {
    List<Diagnostic> diagnostics = getDiagnostics(documentContextOf(ModuleType.CommandModule));

    assertThat(diagnostics).hasSize(3);
    assertThat(diagnostics, true)
      .hasRange(8, 0, 8, 22)
      .hasRange(12, 0, 12, 31)
      .hasRange(24, 0, 24, 18);
  }

  @Test
  void testOtherModule() {
    List<Diagnostic> diagnostics = getDiagnostics(documentContextOf(ModuleType.CommonModule));

    assertThat(diagnostics).isEmpty();
  }

  @Test
  void testOrdinaryFormModule() {
    final var PATH_TO_METADATA = "src/test/resources/metadata/designer";
    initServerContext(Absolute.path(PATH_TO_METADATA));
    var form = spy((Form) context.getConfiguration().getPlainChildren().stream()
      .filter(mdo -> mdo.getName().equalsIgnoreCase("ФормаЭлемента"))
      .findFirst()
      .get());

    var documentContext = documentContextOf(ModuleType.FormModule);
    when(form.getFormType()).thenReturn(FormType.ORDINARY);
    when(documentContext.getMdObject()).thenReturn(Optional.of(form));

    List<Diagnostic> diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics).isEmpty();
  }

  private DocumentContext documentContextOf(ModuleType moduleType) {
    var documentContext = spy(getDocumentContext());
    when(documentContext.getModuleType()).thenReturn(moduleType);
    return documentContext;
  }
}
