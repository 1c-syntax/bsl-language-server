/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2023
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
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.mdclasses.mdo.MDCommonModule;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@DirtiesContext
class ExecuteExternalCodeInCommonModuleDiagnosticTest extends AbstractDiagnosticTest<ExecuteExternalCodeInCommonModuleDiagnostic> {
  ExecuteExternalCodeInCommonModuleDiagnosticTest() {
    super(ExecuteExternalCodeInCommonModuleDiagnostic.class);
  }

  private static final String PATH_TO_METADATA = "src/test/resources/metadata/designer";
  private static final String PATH_TO_MODULE_FILE = "src/test/resources/metadata/designer/CommonModules/ПервыйОбщийМодуль/Ext/Module.bsl";

  private MDCommonModule module;
  private DocumentContext documentContext;

  @Test
  void testIsServer() {

    getDocumentContextFromFile();
    when(module.isServer()).thenReturn(Boolean.TRUE);
    when(documentContext.getMdObject()).thenReturn(Optional.of(module));

    List<Diagnostic> diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics).hasSize(2);
    assertThat(diagnostics, true)
      .hasRange(2, 4, 21)
      .hasRange(6, 12, 29)
    ;
  }

  @Test
  void testIsExternal() {

    getDocumentContextFromFile();
    when(module.isServer()).thenReturn(Boolean.FALSE);
    when(module.isClientOrdinaryApplication()).thenReturn(Boolean.FALSE);
    when(module.isExternalConnection()).thenReturn(Boolean.TRUE);
    when(documentContext.getMdObject()).thenReturn(Optional.of(module));

    List<Diagnostic> diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics).hasSize(2);
    assertThat(diagnostics, true)
      .hasRange(2, 4, 21)
      .hasRange(6, 12, 29)
    ;
  }

  @Test
  void testIsOrdinary() {

    getDocumentContextFromFile();
    when(module.isServer()).thenReturn(Boolean.FALSE);
    when(module.isClientOrdinaryApplication()).thenReturn(Boolean.TRUE);
    when(module.isExternalConnection()).thenReturn(Boolean.FALSE);
    when(module.isClientManagedApplication()).thenReturn(Boolean.FALSE);
    when(documentContext.getMdObject()).thenReturn(Optional.of(module));

    List<Diagnostic> diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics).hasSize(2);
    assertThat(diagnostics, true)
      .hasRange(2, 4, 21)
      .hasRange(6, 12, 29)
    ;
  }

  @Test
  void testIsNonServer() {

    getDocumentContextFromFile();
    when(module.isServer()).thenReturn(Boolean.FALSE);
    when(module.isClientOrdinaryApplication()).thenReturn(Boolean.FALSE);
    when(module.isExternalConnection()).thenReturn(Boolean.FALSE);
    when(documentContext.getMdObject()).thenReturn(Optional.of(module));

    List<Diagnostic> diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics).isEmpty();
  }

  private void getDocumentContextFromFile() {
    var path = Absolute.path(PATH_TO_METADATA);
    var testFile = Paths.get(PATH_TO_MODULE_FILE).toAbsolutePath();

    initServerContext(path);
    var configuration = spy(context.getConfiguration());

    documentContext = spy(TestUtils.getDocumentContext(
      testFile.toUri(),
      getText(),
      context
    ));

    module = spy((MDCommonModule) configuration.getModulesByObject().get(documentContext.getUri()));

  }
}
