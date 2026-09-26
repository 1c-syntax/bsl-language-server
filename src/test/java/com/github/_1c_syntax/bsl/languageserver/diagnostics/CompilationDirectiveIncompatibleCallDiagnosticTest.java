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

class CompilationDirectiveIncompatibleCallDiagnosticTest
  extends AbstractDiagnosticTest<CompilationDirectiveIncompatibleCallDiagnostic> {

  CompilationDirectiveIncompatibleCallDiagnosticTest() {
    super(CompilationDirectiveIncompatibleCallDiagnostic.class);
  }

  @Test
  void testFormModule() {
    List<Diagnostic> diagnostics = getDiagnostics(documentContextOf(getDocumentContext(), ModuleType.FormModule));

    assertThat(diagnostics).hasSize(16);
    assertThat(diagnostics, true)
      .hasMessageOnRange("Метод \"Клиентская\" недоступен в контексте вызывающего метода", 40, 4, 40, 14)
      .hasRange(46, 9, 46, 23)
      .hasRange(48, 14, 48, 28)
      .hasRange(52, 4, 52, 14)
      .hasRange(57, 4, 57, 14)
      .hasRange(58, 4, 58, 13)
      .hasRange(59, 4, 59, 16)
      .hasRange(66, 4, 66, 14)
      .hasRange(67, 4, 67, 13)
      .hasRange(71, 4, 71, 14)
      .hasRange(83, 4, 83, 14)
      .hasRange(86, 4, 86, 14)
      .hasRange(89, 4, 89, 14)
      .hasRange(93, 4, 93, 14)
      .hasRange(110, 4, 110, 14)
      .hasRange(129, 4, 129, 13);
  }

  @Test
  void testCommandModule() {
    var documentContext = getDocumentContext("CompilationDirectiveIncompatibleCallDiagnosticCommand");
    List<Diagnostic> diagnostics = getDiagnostics(documentContextOf(documentContext, ModuleType.CommandModule));

    assertThat(diagnostics).hasSize(3);
    assertThat(diagnostics, true)
      .hasRange(13, 4, 13, 14)
      .hasRange(20, 4, 20, 14)
      .hasRange(26, 4, 26, 14);
  }

  @Test
  void testOtherModule() {
    List<Diagnostic> diagnostics = getDiagnostics(documentContextOf(getDocumentContext(), ModuleType.CommonModule));

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

    var documentContext = documentContextOf(getDocumentContext(), ModuleType.FormModule);
    when(form.getFormType()).thenReturn(FormType.ORDINARY);
    when(documentContext.getMdObject()).thenReturn(Optional.of(form));

    List<Diagnostic> diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics).isEmpty();
  }

  private static DocumentContext documentContextOf(DocumentContext documentContext, ModuleType moduleType) {
    var spied = spy(documentContext);
    when(spied.getModuleType()).thenReturn(moduleType);
    return spied;
  }
}
