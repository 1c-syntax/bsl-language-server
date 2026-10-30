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
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.types.ModuleType;
import lombok.SneakyThrows;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class FileDbIndexKeyLengthExceededDiagnosticTest extends AbstractDiagnosticTest<FileDbIndexKeyLengthExceededDiagnostic> {

  FileDbIndexKeyLengthExceededDiagnosticTest() {
    super(FileDbIndexKeyLengthExceededDiagnostic.class);
  }

  @Test
  void testFileMode() {
    diagnosticInstance.configure(Map.of("checkMode", "FILE"));
    List<Diagnostic> diagnostics = runDiagnostic();
    assertThat(diagnostics).hasSize(1);
  }

  @Test
  void testMssqlMode() {
    diagnosticInstance.configure(Map.of("checkMode", "MSSQL"));
    List<Diagnostic> diagnostics = runDiagnostic();
    assertThat(diagnostics).hasSize(2);
  }

  @Test
  void testAllMode() {
    diagnosticInstance.configure(Map.of("checkMode", "ALL"));
    List<Diagnostic> diagnostics = runDiagnostic();
    assertThat(diagnostics).hasSize(3);
  }

  private List<Diagnostic> runDiagnostic() {
    DocumentContext documentContext = createDocumentContext();
    DocumentContext spyContext = spy(documentContext);
    when(spyContext.getModuleType()).thenReturn(ModuleType.SessionModule);

    return diagnosticInstance.getDiagnostics(spyContext);
  }

  @SneakyThrows
  private DocumentContext createDocumentContext() {
    Path metadataPath = Path.of("src", "test", "resources", "metadata", "FileDbIndexKeyLengthExceeded");
    initServerContext(metadataPath);

    String dummyBslFilePath = "src/test/resources/diagnostics/FileDbIndexKeyLengthExceededDiagnostic.bsl";
    return TestUtils.getDocumentContextFromFile(dummyBslFilePath, context);
  }
}