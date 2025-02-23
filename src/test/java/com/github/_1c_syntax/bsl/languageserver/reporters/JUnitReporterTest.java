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
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.github._1c_syntax.bsl.languageserver.reporters.data.AnalysisInfo;
import com.github._1c_syntax.bsl.languageserver.reporters.data.FileInfo;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class JUnitReporterTest {

  private final File file = new File("./bsl-junit.xml");

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
    List<Diagnostic> diagnostics = new ArrayList<>();
    diagnostics.add(new Diagnostic(
      Ranges.create(0, 1, 2, 3),
      "message",
      DiagnosticSeverity.Error,
      "test-source",
      "test"
    ));

    diagnostics.add(new Diagnostic(
      Ranges.create(0, 1, 2, 4),
      "message4",
      DiagnosticSeverity.Error,
      "test-source2",
      "test3"
    ));

    diagnostics.add(new Diagnostic(
      Ranges.create(3, 1, 4, 4),
      "message4",
      DiagnosticSeverity.Error,
      "test-source2",
      "test3"
    ));

    var documentContext = TestUtils.getDocumentContext(
      Paths.get("./src/test/java/diagnostics/CanonicalSpellingKeywordsDiagnostic.bsl").toUri(),
      ""
    );
    String sourceDir = ".";
    FileInfo fileInfo = new FileInfo(sourceDir, documentContext, diagnostics);
    AnalysisInfo analysisInfo = new AnalysisInfo(LocalDateTime.now(), Collections.singletonList(fileInfo), sourceDir);

    DiagnosticReporter reporter = new JUnitReporter();

    // when
    reporter.report(analysisInfo, Path.of(sourceDir));

    // then
    ObjectMapper mapper = new XmlMapper();
    JUnitTestSuites report = mapper.readValue(file, JUnitTestSuites.class);

    assertThat(report).isNotNull();

  }
}
