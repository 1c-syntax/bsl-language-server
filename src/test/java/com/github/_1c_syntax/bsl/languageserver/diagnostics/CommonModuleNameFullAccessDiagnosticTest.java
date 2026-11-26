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
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.mdo.CommonModule;
import com.github._1c_syntax.utils.Absolute;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@CleanupContextBeforeClassAndAfterEachTestMethod
class CommonModuleNameFullAccessDiagnosticTest extends AbstractDiagnosticTest<CommonModuleNameFullAccessDiagnostic> {
  private DocumentContext documentContext;
  private CommonModule module;

  CommonModuleNameFullAccessDiagnosticTest() {
    super(CommonModuleNameFullAccessDiagnostic.class);
  }


  private static final String PATH_TO_METADATA = "src/test/resources/metadata/designer";
  private static final String PATH_TO_MODULE_FILE = "src/test/resources/metadata/designer/CommonModules/ПервыйОбщийМодуль/Ext/Module.bsl";


  @Test
  void test() {

    getDocumentContextFromFile();

    // given
    when(module.getName()).thenReturn("ЧтоТо");
    when(module.isPrivileged()).thenReturn(Boolean.TRUE);

    when(documentContext.getMdObject()).thenReturn(Optional.of(module));

    // when
    List<Diagnostic> diagnostics = diagnosticInstance.getDiagnostics(documentContext);

    //then
    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true)
      .hasRange(1, 0, 13);

  }

  @Test
  void testNegative() {

    getDocumentContextFromFile();

    // given
    when(module.getName()).thenReturn("ЧтоТоFullAccess");
    when(module.isPrivileged()).thenReturn(Boolean.TRUE);

    when(documentContext.getMdObject()).thenReturn(Optional.of(module));

    // when
    List<Diagnostic> diagnostics = diagnosticInstance.getDiagnostics(documentContext);

    //then
    assertThat(diagnostics).isEmpty();

  }

  @Test
  void testFalse() {

    getDocumentContextFromFile();

    // given
    when(module.getName()).thenReturn("ЧтоТоКлиентСервер");
    when(module.isPrivileged()).thenReturn(Boolean.FALSE);

    when(documentContext.getMdObject()).thenReturn(Optional.of(module));

    // when
    List<Diagnostic> diagnostics = diagnosticInstance.getDiagnostics(documentContext);

    //then
    assertThat(diagnostics).isEmpty();

  }

  @SneakyThrows
  void getDocumentContextFromFile() {
    Path path = Absolute.path(PATH_TO_METADATA);
    Path testFile = Path.of(PATH_TO_MODULE_FILE).toAbsolutePath();

    initServerContext(path);
    var configuration = context.getConfiguration();
    documentContext = spy(TestUtils.getDocumentContext(
      testFile.toUri(),
      FileUtils.readFileToString(testFile.toFile(), StandardCharsets.UTF_8),
      context
    ));

    module = spy((CommonModule) configuration.findChild(documentContext.getUri()).get());
  }
}
