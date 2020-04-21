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
import com.github._1c_syntax.mdclasses.metadata.Configuration;
import com.github._1c_syntax.utils.Absolute;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class CommonModuleAssignDiagnosticTest extends AbstractDiagnosticTest<CommonModuleAssignDiagnostic> {
  CommonModuleAssignDiagnosticTest() {
    super(CommonModuleAssignDiagnostic.class);
  }

  private static final String PATH_TO_METADATA = "src/test/resources/metadata";
  private static final String PATH_TO_MODULE_FILE = "src/test/resources/diagnostics/CommonModuleAssignDiagnostic.bsl";


  @SneakyThrows
  @Test
  void test() {

    Path path = Absolute.path(PATH_TO_METADATA);
    ServerContext serverContext = new ServerContext(path);
    Configuration configurationMetadata = serverContext.getConfiguration();


    Path testFile = Paths.get(PATH_TO_MODULE_FILE).toAbsolutePath();
    DocumentContext documentContext = new DocumentContext(
      testFile.toUri(),
      FileUtils.readFileToString(testFile.toFile(), StandardCharsets.UTF_8),
      serverContext
    );

    List<Diagnostic> diagnostics = diagnosticInstance.getDiagnostics(documentContext);


    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true)
      .hasRange(3, 0, 3, 17);

  }
}
