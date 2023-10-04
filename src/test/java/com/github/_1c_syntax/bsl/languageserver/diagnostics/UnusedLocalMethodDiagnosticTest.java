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
import com.github._1c_syntax.bsl.types.ModuleType;
import com.github._1c_syntax.mdclasses.mdo.MDCommonModule;
import com.github._1c_syntax.utils.Absolute;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class UnusedLocalMethodDiagnosticTest extends AbstractDiagnosticTest<UnusedLocalMethodDiagnostic> {
  private static final String PATH_TO_METADATA = "src/test/resources/metadata/designer";
  private static final String PATH_TO_MODULE_FILE = PATH_TO_METADATA + "/CommonModules/ПервыйОбщийМодуль/Ext/Module.bsl";
  private static final String PATH_TO_MODULE_CONTENT = "src/test/resources/diagnostics/UnusedLocalMethodDiagnostic.bsl";

  private MDCommonModule module;
  private DocumentContext documentContext;
  UnusedLocalMethodDiagnosticTest() {
    super(UnusedLocalMethodDiagnostic.class);
  }

  @Test
  void test() {
    List<Diagnostic> diagnostics = getDiagnostics();
    checkByDefault(diagnostics);
  }

  private static void checkByDefault(List<Diagnostic> diagnostics) {
    assertThat(diagnostics).hasSize(2);
    assertThat(diagnostics, true)
      .hasRange(1, 10, 24)
      .hasRange(70, 10, 41)
    ;
  }

  @Test
  void testObjectModuleByDefault() {
    getObjectModuleDocumentContext();

    List<Diagnostic> diagnostics = getDiagnostics(documentContext);
    assertThat(diagnostics).isEmpty();
  }

  @Test
  void testConfigure() {
    // given
    Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("attachableMethodPrefixes", "ПодключаемаяМоя_");
    diagnosticInstance.configure(configuration);

    // when
    List<Diagnostic> diagnostics = getDiagnostics();

    // then
    assertThat(diagnostics).hasSize(3);
    assertThat(diagnostics, true)
      .hasRange(1, 10, 24)
      .hasRange(60, 10, 40)
      .hasRange(63, 10, 39)
    ;
  }

  @Test
  void testObjectModuleWithEnabledConfiguration() {
    // given
    getObjectModuleDocumentContext();

    Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("checkObjectModule", true);
    diagnosticInstance.configure(configuration);

    // when
    List<Diagnostic> diagnostics = getDiagnostics(documentContext);

    // then
    checkByDefault(diagnostics);
  }

  private void getObjectModuleDocumentContext() {
    Path testFile = Paths.get(PATH_TO_MODULE_CONTENT).toAbsolutePath();
    getDocumentContextFromFile(testFile);
    when(documentContext.getModuleType()).thenReturn(ModuleType.ObjectModule);
    when(documentContext.getMdObject()).thenReturn(Optional.of(module));
  }

  @SneakyThrows
  void getDocumentContextFromFile(Path testFile) {

    Path path = Absolute.path(PATH_TO_METADATA);
    Path moduleFile = Paths.get(PATH_TO_MODULE_FILE).toAbsolutePath();

    initServerContext(path);
    var configuration = context.getConfiguration();
    documentContext = spy(TestUtils.getDocumentContext(
      testFile.toUri(),
      FileUtils.readFileToString(testFile.toFile(), StandardCharsets.UTF_8),
      context
    ));

    module = spy((MDCommonModule) configuration.getModulesByObject().get(moduleFile.toUri()));
  }
}
