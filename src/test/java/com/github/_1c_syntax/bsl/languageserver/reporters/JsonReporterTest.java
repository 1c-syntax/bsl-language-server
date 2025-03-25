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
package com.github._1c_syntax.bsl.languageserver.reporters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github._1c_syntax.bsl.languageserver.reporters.data.AnalysisInfo;
import com.github._1c_syntax.bsl.languageserver.reporters.data.FileInfo;
import com.github._1c_syntax.bsl.languageserver.reporters.databind.AnalysisInfoObjectMapper;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.apache.commons.io.FileUtils;
import org.assertj.core.api.Assertions;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collections;

@SpringBootTest
class JsonReporterTest {

  private final File file = new File("./bsl-json.json");

  @BeforeEach
  void setUp() {
    FileUtils.deleteQuietly(file);
  }

  @AfterEach
  void tearDown() {
    System.gc();
    FileUtils.deleteQuietly(file);
  }

  @Test
  void report() throws IOException {

    // given
    Diagnostic diagnostic = new Diagnostic(
      Ranges.create(0, 1, 2, 3),
      "message",
      DiagnosticSeverity.Error,
      "test-source",
      "test"
    );

    var documentContext = TestUtils.getDocumentContext("");
    String sourceDir = ".";
    FileInfo fileInfo = new FileInfo(sourceDir, documentContext, Collections.singletonList(diagnostic));
    AnalysisInfo analysisInfo = new AnalysisInfo(LocalDateTime.now(), Collections.singletonList(fileInfo), sourceDir);

    JsonReporter reporter = new JsonReporter();

    // when
    reporter.report(analysisInfo, Path.of(sourceDir));

    // then
    ObjectMapper mapper = new AnalysisInfoObjectMapper();

    mapper.findAndRegisterModules();
    AnalysisInfo report = mapper.readValue(file, AnalysisInfo.class);

    Assertions.assertThat(report.getFileinfos()).hasSize(1);

  }
}