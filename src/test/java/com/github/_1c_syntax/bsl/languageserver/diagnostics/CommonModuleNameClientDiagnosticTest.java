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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.mdclasses.mdo.CommonModule;
import com.github._1c_syntax.utils.Absolute;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class CommonModuleNameClientDiagnosticTest extends AbstractDiagnosticTest<CommonModuleNameClientDiagnostic> {
  private DocumentContext documentContext;
  private CommonModule module;

  CommonModuleNameClientDiagnosticTest() {
    super(CommonModuleNameClientDiagnostic.class);
  }

  private static final String PATH_TO_METADATA = "src/test/resources/metadata";
  private static final String PATH_TO_MODULE_FILE = "src/test/resources/metadata/CommonModules/ПервыйОбщийМодуль/Ext/Module.bsl";


  @Test
  void test() {

    getDocumentContextFromFile();

    // given
    when(module.getName()).thenReturn("ЧтоТо");
    when(module.isServer()).thenReturn(Boolean.FALSE);
    when(module.isClientManagedApplication()).thenReturn(Boolean.TRUE);
    when(module.isClientOrdinaryApplication()).thenReturn(Boolean.TRUE);

    when(documentContext.getMdObject()).thenReturn(Optional.of(module));

    // when
    List<Diagnostic> diagnostics = diagnosticInstance.getDiagnostics(documentContext);

    //then
    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true)
      .hasRange(5, 0, 1);

  }

  @Test
  void testClient() {

    getDocumentContextFromFile();

    // given
    when(module.getName()).thenReturn("ЧтоТо");
    when(module.isClientManagedApplication()).thenReturn(Boolean.TRUE);
    when(module.isServer()).thenReturn(Boolean.FALSE);
    when(module.isExternalConnection()).thenReturn(Boolean.FALSE);
    when(module.isClientOrdinaryApplication()).thenReturn(Boolean.TRUE);
    when(module.isServerCall()).thenReturn(Boolean.FALSE);

    when(documentContext.getMdObject()).thenReturn(Optional.of(module));

    // when
    List<Diagnostic> diagnostics = diagnosticInstance.getDiagnostics(documentContext);

    //then
    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true)
      .hasRange(5, 0, 1);

  }

  @Test
  void testClientServer() {

    getDocumentContextFromFile();

    // given
    when(module.getName()).thenReturn("ЧтоТо");
    when(module.isServer()).thenReturn(Boolean.TRUE);
    when(module.isClientManagedApplication()).thenReturn(Boolean.TRUE);

    when(documentContext.getMdObject()).thenReturn(Optional.of(module));

    // when
    List<Diagnostic> diagnostics = diagnosticInstance.getDiagnostics(documentContext);

    //then
    assertThat(diagnostics).hasSize(0);

  }

  @Test
  void testNegative() {

    getDocumentContextFromFile();

    // given
    when(module.getName()).thenReturn("ЧтоТоclient");
    when(module.isServer()).thenReturn(Boolean.FALSE);
    when(module.isClientManagedApplication()).thenReturn(Boolean.TRUE);

    when(documentContext.getMdObject()).thenReturn(Optional.of(module));

    // when
    List<Diagnostic> diagnostics = diagnosticInstance.getDiagnostics(documentContext);

    //then
    assertThat(diagnostics).hasSize(0);

  }

  @Test
  void testGlobal() {

    getDocumentContextFromFile();

    // given
    when(module.getName()).thenReturn("ЧтоТоГлобальный");
    when(module.isServer()).thenReturn(Boolean.FALSE);
    when(module.isGlobal()).thenReturn(Boolean.TRUE);
    when(module.isClientManagedApplication()).thenReturn(Boolean.TRUE);

    when(documentContext.getMdObject()).thenReturn(Optional.of(module));

    // when
    List<Diagnostic> diagnostics = diagnosticInstance.getDiagnostics(documentContext);

    //then
    assertThat(diagnostics).hasSize(0);

  }


  @SneakyThrows
  void getDocumentContextFromFile() {

    Path path = Absolute.path(PATH_TO_METADATA);
    Path testFile = Paths.get(PATH_TO_MODULE_FILE).toAbsolutePath();

    ServerContext serverContext = new ServerContext(path);
    var configuration = serverContext.getConfiguration();
    documentContext = spy(new DocumentContext(
      testFile.toUri(),
      FileUtils.readFileToString(testFile.toFile(), StandardCharsets.UTF_8),
      serverContext
    ));


    module = spy((CommonModule) configuration.getModulesByObject().get(documentContext.getUri()));

  }

}
