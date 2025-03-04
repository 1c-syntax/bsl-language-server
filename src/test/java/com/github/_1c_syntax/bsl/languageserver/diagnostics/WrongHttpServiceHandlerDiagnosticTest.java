/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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

class WrongHttpServiceHandlerDiagnosticTest extends AbstractDiagnosticTest<WrongHttpServiceHandlerDiagnostic> {
  WrongHttpServiceHandlerDiagnosticTest() {
    super(WrongHttpServiceHandlerDiagnostic.class);
  }

  private static final String PATH_TO_METADATA = "src/test/resources/metadata/designer";
  private static final String PATH_TO_MODULE_FILE = PATH_TO_METADATA + "/HTTPServices/HTTPСервис1/Ext/Module.bsl";

  @SneakyThrows
  @Test
  void test() {

    final var path = Absolute.path(PATH_TO_METADATA);

    initServerContext(path);
    Path testFile = Paths.get(PATH_TO_MODULE_FILE).toAbsolutePath();
    var documentContext = TestUtils.getDocumentContext(
      testFile.toUri(),
      FileUtils.readFileToString(testFile.toFile(), StandardCharsets.UTF_8),
      context
    );

    List<Diagnostic> diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics, true)
      .hasMessageOnRange("Создайте функцию-обработчик \"URLTemplate1GET\" или исправьте некорректный обработчик http-сервиса \"HTTPService.HTTPСервис1.URLTemplate.URLTemplate1.Method.GET\"",
        0, 0, 1)
      .hasMessageOnRange("Задайте обработчик http-сервиса \"HTTPService.HTTPСервис1.URLTemplate.URLTemplate_НеверныеОбработчики.Method.GET\"",
        0, 0, 1)
      .hasMessageOnRange("Задайте всего один параметр у обработчика \"URLTemplate_НеверныеОбработчики_POST\" для http-сервиса \"HTTPService.HTTPСервис1.URLTemplate.URLTemplate_НеверныеОбработчики.Method.POST\"",
        16, 8, 44)
      .hasSize(3)

    ;
  }
}
