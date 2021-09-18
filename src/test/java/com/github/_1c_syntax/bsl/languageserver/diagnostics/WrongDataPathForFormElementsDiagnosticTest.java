/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2021
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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.mdclasses.mdo.support.ModuleType;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class WrongDataPathForFormElementsDiagnosticTest extends AbstractDiagnosticTest<WrongDataPathForFormElementsDiagnostic> {

  public static final String DYNAMIC_LIST_FORM_MDO_REF = "Catalog.Справочник1.Form.ФормаВыбора";
  public static final String DYNAMIC_LIST_MESSAGE = "Не указан путь к данным у реквизита формы \"НесуществующийРеквизитСписка\". Форма \"Справочник.Справочник1.Форма.ФормаВыбора\".";
  public static final String ELEMENT_FORM_MDO_REF = "Catalog.Справочник1.Form.ФормаЭлемента";
  public static final String ELEMENT_MESSAGE = "Не указан путь к данным у реквизита формы \"НесуществующийРеквизит\". Форма \"Справочник.Справочник1.Форма.ФормаЭлемента\".";
  private static final String PATH_TO_METADATA = "src/test/resources/metadata";
  private DocumentContext documentContext;

  WrongDataPathForFormElementsDiagnosticTest() {
    super(WrongDataPathForFormElementsDiagnostic.class);
  }

  @BeforeEach
  void setUp() {
    initServerContext(Absolute.path(PATH_TO_METADATA));

    documentContext = spy(getDocumentContext());
  }

  @Test
  void test() {

    when(documentContext.getModuleType()).thenReturn(ModuleType.ManagedApplicationModule);

    var serverContext = spy(documentContext.getServerContext());
    when(documentContext.getServerContext()).thenReturn(serverContext);

    when(serverContext.getDocument(DYNAMIC_LIST_FORM_MDO_REF,
      ModuleType.FormModule)).thenReturn(Optional.of(documentContext));
    when(serverContext.getDocument(ELEMENT_FORM_MDO_REF,
      ModuleType.FormModule)).thenReturn(Optional.of(documentContext));

    List<Diagnostic> diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics, true)
      .hasMessageOnRange(ELEMENT_MESSAGE, 0, 8, 14)
      .hasMessageOnRange(DYNAMIC_LIST_MESSAGE, 0, 8, 14)
      .hasSize(2);

  }

  @Test
  void testFormModule() {

    when(documentContext.getModuleType()).thenReturn(ModuleType.FormModule);

    List<Diagnostic> diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics, true)
      .hasMessageOnRange(ELEMENT_MESSAGE, 1, 9, 27)
      .hasMessageOnRange(DYNAMIC_LIST_MESSAGE, 3, 10, 24)
      .hasSize(2);

  }
}
