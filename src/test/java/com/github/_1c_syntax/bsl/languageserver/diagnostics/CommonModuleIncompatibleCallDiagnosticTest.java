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
import com.github._1c_syntax.bsl.types.ModuleType;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class CommonModuleIncompatibleCallDiagnosticTest extends AbstractDiagnosticTest<CommonModuleIncompatibleCallDiagnostic> {

  private static final String PATH_TO_METADATA = "src/test/resources/metadata/commonModuleCalls";

  CommonModuleIncompatibleCallDiagnosticTest() {
    super(CommonModuleIncompatibleCallDiagnostic.class);
  }

  @Test
  void testFormModule() {
    initServerContext(PATH_TO_METADATA);
    var documentContext = spy(getDocumentContext());
    when(documentContext.getModuleType()).thenReturn(ModuleType.FormModule);

    List<Diagnostic> diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics).hasSize(4);
    assertThat(diagnostics, true)
      .hasMessageOnRange("Общий модуль \"СерверныйМодуль\" недоступен в контексте вызова", 2, 20, 2, 24)
      .hasRange(11, 21, 11, 25)
      .hasMessageOnRange("Общий модуль \"ГлобальныйКлиентскийМодуль\" недоступен в контексте вызова", 13, 4, 13, 18)
      .hasRange(20, 20, 20, 24);
  }

  @Test
  void testServerCommonModule() {
    List<Diagnostic> diagnostics = getDiagnostics(commonModule("СерверныйМодуль"));

    assertThat(diagnostics).hasSize(2);
    assertThat(diagnostics, true)
      .hasRange(4, 21, 4, 25)
      .hasRange(9, 4, 9, 18);
  }

  @Test
  void testClientCommonModule() {
    List<Diagnostic> diagnostics = getDiagnostics(commonModule("КлиентскийМодуль"));

    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true)
      .hasRange(4, 20, 4, 24);
  }

  @Test
  void testClientServerCommonModule() {
    List<Diagnostic> diagnostics = getDiagnostics(commonModule("КлиентСерверныйМодуль"));

    assertThat(diagnostics).hasSize(4);
    assertThat(diagnostics, true)
      .hasMessageOnRange(
        "Метод \"ЦельЕслиСервер\" общего модуля \"КлиентСерверныйМодуль\" недоступен в контексте вызова",
        14, 4, 14, 18)
      .hasRange(15, 26, 15, 40)
      .hasRange(16, 20, 16, 24)
      .hasRange(20, 21, 20, 25);
  }

  @Test
  void testWithoutMetadata() {
    var documentContext = spy(getDocumentContext());
    when(documentContext.getModuleType()).thenReturn(ModuleType.FormModule);

    List<Diagnostic> diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics).isEmpty();
  }

  @Test
  void testOtherModule() {
    initServerContext(PATH_TO_METADATA);
    var documentContext = spy(getDocumentContext());
    when(documentContext.getModuleType()).thenReturn(ModuleType.SessionModule);

    List<Diagnostic> diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics).isEmpty();
  }

  private DocumentContext commonModule(String name) {
    initServerContext(PATH_TO_METADATA);
    var documentContext = context.getDocument("CommonModule." + name, ModuleType.CommonModule).orElseThrow();
    context.rebuildDocument(documentContext);
    return documentContext;
  }
}
