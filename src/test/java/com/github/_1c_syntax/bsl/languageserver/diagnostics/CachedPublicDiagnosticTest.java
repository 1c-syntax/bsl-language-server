/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2021
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
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.mdclasses.mdo.CommonModule;
import com.github._1c_syntax.mdclasses.metadata.additional.ReturnValueReuse;
import com.github._1c_syntax.utils.Absolute;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@DirtiesContext
class CachedPublicDiagnosticTest extends AbstractDiagnosticTest<CachedPublicDiagnostic> {
  CachedPublicDiagnosticTest() {
    super(CachedPublicDiagnostic.class);
  }

  private CommonModule module;
  private DocumentContext documentContext;

  private static final String PATH_TO_METADATA = "src/test/resources/metadata";
  private static final String PATH_TO_MODULE_FILE = "src/test/resources/metadata/CommonModules/ПервыйОбщийМодуль/Ext/Module.bsl";
  private static final String PATH_TO_MODULE_CONTENT = "src/test/resources/diagnostics/CachedPublicDiagnostic.bsl";


  @Test
  void test() {

    getDocumentContextFromFile();

    // given
    when(module.getReturnValuesReuse()).thenReturn(ReturnValueReuse.DURING_REQUEST);

    when(documentContext.getMdObject()).thenReturn(Optional.of(module));

    // when
    List<Diagnostic> diagnostics = diagnosticInstance.getDiagnostics(documentContext);

    //then
    assertThat(diagnostics).hasSize(2);
    assertThat(diagnostics, true)
      .hasRange(0, 9, 29)
      .hasRange(16, 9, 15);

  }

  @Test
  void testSession() {

    getDocumentContextFromFile();

    // given
    when(module.getReturnValuesReuse()).thenReturn(ReturnValueReuse.DURING_SESSION);

    when(documentContext.getMdObject()).thenReturn(Optional.of(module));

    // when
    List<Diagnostic> diagnostics = diagnosticInstance.getDiagnostics(documentContext);

    //then
    assertThat(diagnostics).hasSize(2);
    assertThat(diagnostics, true)
      .hasRange(0, 9, 29)
      .hasRange(16, 9, 15);

  }


  @Test
  void testNegative() {

    getDocumentContextFromFile();

    // given
    when(module.getReturnValuesReuse()).thenReturn(ReturnValueReuse.DONT_USE);

    when(documentContext.getMdObject()).thenReturn(Optional.of(module));

    // when
    List<Diagnostic> diagnostics = diagnosticInstance.getDiagnostics(documentContext);

    //then
    assertThat(diagnostics).isEmpty();

  }


  @SneakyThrows
  void getDocumentContextFromFile() {

    Path path = Absolute.path(PATH_TO_METADATA);
    Path moduleFile = Paths.get(PATH_TO_MODULE_FILE).toAbsolutePath();
    Path testFile = Paths.get(PATH_TO_MODULE_CONTENT).toAbsolutePath();


    initServerContext(path);
    var configuration = context.getConfiguration();
    documentContext = spy(TestUtils.getDocumentContext(
      testFile.toUri(),
      FileUtils.readFileToString(testFile.toFile(), StandardCharsets.UTF_8),
      context
    ));


    module = spy((CommonModule) configuration.getModulesByObject().get(moduleFile.toUri()));

  }
}
