/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
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
package org.github._1c_syntax.bsl.languageserver.diagnostics.reporter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Location;
import org.github._1c_syntax.bsl.languageserver.diagnostics.FileInfo;
import org.github._1c_syntax.bsl.languageserver.utils.RangeHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class GenericReporterTest {

  private File file = new File("./bsl-generic-json.json");

  @BeforeEach
  void setUp() {
    FileUtils.deleteQuietly(file);
  }

  @AfterEach
  void tearDown() {
    FileUtils.deleteQuietly(file);
  }

  @Test
  void report() throws IOException {

    // given
    Diagnostic diagnostic = new Diagnostic(
      RangeHelper.newRange(0, 1, 2, 3),
      "message",
      DiagnosticSeverity.Error,
      "test-source",
      "test"
    );
    Location location = new Location("file:///fake-uri", RangeHelper.newRange(0, 2, 2, 3));
    diagnostic.setRelatedInformation(Collections.singletonList(new DiagnosticRelatedInformation(location, "message")));

    FileInfo fileInfo = new FileInfo(new File("").toPath(), Collections.singletonList(diagnostic));
    AnalysisInfo analysisInfo = new AnalysisInfo(LocalDateTime.now(), Collections.singletonList(fileInfo));

    AbstractDiagnosticReporter reporter = new GenericIssueReporter();

    // when
    reporter.report(analysisInfo);

    // then
    ObjectMapper mapper = new ObjectMapper();
    GenericIssueReport report = mapper.readValue(file, GenericIssueReport.class);
    assertThat(report).isNotNull();

  }

}
