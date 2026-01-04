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

import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class PrivilegedModuleMethodCallDiagnosticTest extends AbstractDiagnosticTest<PrivilegedModuleMethodCallDiagnostic> {
  private static final String PATH_TO_METADATA = "src/test/resources/metadata/privilegedModules";
  private static final String PATH_TO_MODULE_FILE = PATH_TO_METADATA + "/CommonModules/ПривилегированныйМодуль1/Ext/Module.bsl";

  PrivilegedModuleMethodCallDiagnosticTest() {
    super(PrivilegedModuleMethodCallDiagnostic.class);
  }

  @Test
  void testWithoutMetadata() {
    var diagnostics = getDiagnostics();
    assertThat(diagnostics).isEmpty();
  }

  @Test
  void test() {
    initServerContext(PATH_TO_METADATA);

    var diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(2);
    assertThat(diagnostics, true)
      .hasMessageOnRange("Проверьте обращение к методу ПубличнаяФункция привилегированного модуля", 3, 40, 56)
      .hasMessageOnRange("Проверьте обращение к методу ПубличнаяПроцедура привилегированного модуля", 4, 29, 47);
  }

  @Test
  void getNestedCalls() {
    var diagnostics = getDiagnosticsAsCommonModule();
    assertThat(diagnostics).hasSize(2);
    assertThat(diagnostics, true)
      .hasMessageOnRange("Проверьте обращение к методу ПубличнаяФункция привилегированного модуля", 15, 15, 31)
      .hasMessageOnRange("Проверьте обращение к методу ПубличнаяПроцедура привилегированного модуля", 19, 4, 22);
  }

  @Test
  void testParameterValidateNestedCalls() {
    Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("validateNestedCalls", false);
    diagnosticInstance.configure(configuration);

    var diagnostics = getDiagnosticsAsCommonModule();
    assertThat(diagnostics).isEmpty();
  }

  private List<Diagnostic> getDiagnosticsAsCommonModule() {
    Path moduleFile = Paths.get(PATH_TO_MODULE_FILE).toAbsolutePath();

    initServerContext(PATH_TO_METADATA);

    var documentContext = spy(getDocumentContext(diagnosticInstance.getClass().getSimpleName()));
    when(documentContext.getUri()).thenReturn(moduleFile.toUri());

    return getDiagnostics(documentContext);
  }
}
