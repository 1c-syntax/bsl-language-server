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

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.types.ModuleType;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class MethodCallUnavailableCommonModuleTest extends AbstractDiagnosticTest<MethodCallUnavailableDiagnostic> {

  private static final String PATH_TO_METADATA = "src/test/resources/metadata/commonModuleCalls";

  MethodCallUnavailableCommonModuleTest() {
    super(MethodCallUnavailableDiagnostic.class);
  }

  @Test
  void testFormModule() {
    initServerContext(PATH_TO_METADATA);
    var documentContext = spy(getDocumentContext("MethodCallUnavailableCommon"));
    when(documentContext.getModuleType()).thenReturn(ModuleType.FormModule);

    List<Diagnostic> diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics).hasSize(15);
    assertThat(diagnostics, true)
      .hasMessageOnRange("Общий модуль \"СерверныйМодуль\" недоступен в контексте вызова (Тонкий клиент)", 2, 4, 2, 19)
      .hasRange(11, 4, 11, 20)
      .hasRange(4, 26, 4, 40)
      .hasRange(12, 26, 12, 40)
      .hasMessageOnRange("Общий модуль \"ГлобальныйКлиентскийМодуль\" недоступен в контексте вызова (Сервер)", 13, 4, 13, 18)
      .hasRange(20, 4, 20, 19);
  }

  @Test
  void testServerCommonModule() {
    List<Diagnostic> diagnostics = getDiagnostics(commonModule("СерверныйМодуль"));

    assertThat(diagnostics).hasSize(6);
    assertThat(diagnostics, true)
      .hasRange(4, 4, 4, 20)
      .hasRange(8, 26, 8, 40)
      .hasRange(9, 4, 9, 18);
  }

  @Test
  void testClientCommonModule() {
    List<Diagnostic> diagnostics = getDiagnostics(commonModule("КлиентскийМодуль"));

    assertThat(diagnostics).hasSize(8);
    assertThat(diagnostics, true)
      .hasRange(4, 4, 4, 19)
      .hasRange(6, 26, 6, 40);
  }

  @Test
  void testClientServerCommonModule() {
    List<Diagnostic> diagnostics = getDiagnostics(commonModule("КлиентСерверныйМодуль"));

    assertThat(diagnostics).hasSize(14);
    assertThat(diagnostics, true)
      .hasMessageOnRange(
        "Метод \"ЦельЕслиСервер\" общего модуля \"КлиентСерверныйМодуль\" недоступен в контексте вызова (Тонкий клиент)",
        14, 4, 14, 18)
      .hasRange(15, 26, 15, 40)
      .hasRange(16, 4, 16, 19)
      .hasRange(20, 4, 20, 20);
  }

  @Test
  void testWithoutMetadata() {
    var documentContext = spy(getDocumentContext("MethodCallUnavailableCommon"));
    when(documentContext.getModuleType()).thenReturn(ModuleType.FormModule);

    List<Diagnostic> diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics).isEmpty();
  }

  @Test
  void testOtherModule() {
    initServerContext(PATH_TO_METADATA);
    var documentContext = spy(getDocumentContext("MethodCallUnavailableCommon"));
    when(documentContext.getModuleType()).thenReturn(ModuleType.SessionModule);

    List<Diagnostic> diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(Language.class)
  void localizesModuleAndMethodErrorsWithoutTranslatingUserNames(Language language) {
    initServerContext(PATH_TO_METADATA);
    var document = spy(getDocumentContext("MethodCallUnavailableCommon"));
    when(document.getModuleType()).thenReturn(ModuleType.FormModule);
    configuration.setLanguage(language);
    diagnosticInstance.getInfo().refresh();
    var diagnostics = getDiagnostics(document);

    var moduleMessage = language == Language.EN
      ? "Common module \"СерверныйМодуль\" is not available in the call context (Thin client)"
      : "Общий модуль \"СерверныйМодуль\" недоступен в контексте вызова (Тонкий клиент)";
    var methodMessage = language == Language.EN
      ? "Method \"ЦельЕслиСервер\" of common module \"КлиентСерверныйМодуль\" is not available in the call context (Thin client)"
      : "Метод \"ЦельЕслиСервер\" общего модуля \"КлиентСерверныйМодуль\" недоступен в контексте вызова (Тонкий клиент)";
    assertThat(diagnostics).allSatisfy(diagnostic -> assertThat(diagnostic.getRelatedInformation()).isNull());
    assertThat(diagnostics, true)
      .hasMessageOnRange(moduleMessage, 2, 4, 2, 19)
      .hasMessageOnRange(methodMessage, 4, 26, 4, 40);
  }

  private DocumentContext commonModule(String name) {
    initServerContext(PATH_TO_METADATA);
    var documentContext = context.getDocument("CommonModule." + name, ModuleType.CommonModule).orElseThrow();
    context.rebuildDocument(documentContext);
    return documentContext;
  }
}
