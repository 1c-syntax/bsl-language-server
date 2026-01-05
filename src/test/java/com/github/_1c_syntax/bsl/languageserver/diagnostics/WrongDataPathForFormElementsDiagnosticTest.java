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

import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.mdclasses.CF;
import com.github._1c_syntax.bsl.mdo.Form;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;
import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@DirtiesContext
class WrongDataPathForFormElementsDiagnosticTest extends AbstractDiagnosticTest<WrongDataPathForFormElementsDiagnostic> {

  public static final String PATH_TO_ELEMENT_MODULE_FILE = "/Catalogs/Справочник1/Forms/ФормаЭлемента/Ext/Form/Module.bsl";
  private static final String DYNAMIC_LIST_MESSAGE = "Не указан путь к данным у реквизита формы \"НесуществующийРеквизитСписка\". Форма \"Справочник.Справочник1.Форма.ФормаВыбора\".";
  private static final String ELEMENT_MESSAGE = "Не указан путь к данным у реквизита формы \"НесуществующийРеквизит\". Форма \"Справочник.Справочник1.Форма.ФормаЭлемента\".";

  WrongDataPathForFormElementsDiagnosticTest() {
    super(WrongDataPathForFormElementsDiagnostic.class);
  }

  @BeforeEach
  void setUp() {
    initServerContext(Absolute.path(PATH_TO_METADATA));
  }

  @Test
  void testNoFormModule() {

    final var pathToManagedApplicationModuleFile = "/Ext/ManagedApplicationModule.bsl";

    context = spy(context);
    final var configuration = spy(context.getConfiguration());
    fillConfigChildrenByFormsWithoutModule(configuration);
    when(context.getConfiguration()).thenReturn(configuration);

    List<Diagnostic> diagnostics = getDiagnosticListForMockedFile(pathToManagedApplicationModuleFile);

    assertThat(diagnostics, true)
      .hasMessageOnRange(ELEMENT_MESSAGE, 0, 0, 7)
      .hasMessageOnRange(DYNAMIC_LIST_MESSAGE, 0, 0, 7)
      .hasSize(2);

  }

  @Test
  void testFormModule() {

    List<Diagnostic> diagnostics = getDiagnosticListForMockedFile(PATH_TO_ELEMENT_MODULE_FILE);

    assertThat(diagnostics, true)
      .hasMessageOnRange(ELEMENT_MESSAGE, 0, 0, 7)
      .hasSize(1);

  }

  @Test
  void testDynamicListFormModule() {

    final var pathToDynamicListModuleFile = "/Catalogs/Справочник1/Forms/ФормаВыбора/Ext/Form/Module.bsl";
    List<Diagnostic> diagnostics = getDiagnosticListForMockedFile(pathToDynamicListModuleFile);

    assertThat(diagnostics, true)
      .hasMessageOnRange(DYNAMIC_LIST_MESSAGE, 0, 0, 7)
      .hasSize(1);

  }

  private void fillConfigChildrenByFormsWithoutModule(CF configuration) {
    var plainChildren = configuration.getPlainChildren().stream()
      .filter(md -> md instanceof Form)
      .map(md -> {
        var mockMD = spy(md);
        when(((Form) mockMD).getModules()).thenReturn(Collections.emptyList());
        return mockMD;
      })
      .toList();

    when(configuration.getPlainChildren()).thenReturn(plainChildren);
  }

  private List<Diagnostic> getDiagnosticListForMockedFile(String pathToDynamicListModuleFile) {
    var testFile = Path.of(PATH_TO_METADATA + pathToDynamicListModuleFile).toAbsolutePath();

    var documentContext = spy(TestUtils.getDocumentContext(testFile.toUri(), getText()));
    when(documentContext.getServerContext()).thenReturn(context);

    return getDiagnostics(documentContext);
  }
}
