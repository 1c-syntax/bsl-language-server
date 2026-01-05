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
package com.github._1c_syntax.bsl.languageserver.reporters;

import tools.jackson.databind.ObjectMapper;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.reporters.data.AnalysisInfo;
import com.github._1c_syntax.bsl.languageserver.reporters.data.FileInfo;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class GenericReporterTest {

  @Autowired
  private GenericIssueReporter reporter;

  @Autowired
  private Map<String, DiagnosticInfo> diagnosticInfos;

  private final File file = new File("./bsl-generic-json.json");

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
    var iterator = diagnosticInfos.entrySet().iterator();
    var firstInfo = iterator.next().getValue();
    var secondInfo = iterator.next().getValue();
    diagnostics.add(new Diagnostic(
      Ranges.create(0, 1, 2, 3),
      "message",
      DiagnosticSeverity.Error,
      "test-source",
      firstInfo.getCode().getStringValue()
    ));

    diagnostics.add(new Diagnostic(
      Ranges.create(0, 1, 2, 4),
      "message4",
      DiagnosticSeverity.Error,
      "test-source2",
      firstInfo.getCode().getStringValue()
    ));

    diagnostics.add(new Diagnostic(
      Ranges.create(3, 1, 4, 4),
      "message4",
      DiagnosticSeverity.Error,
      "test-source2",
      secondInfo.getCode().getStringValue()
    ));

    var documentContext = TestUtils.getDocumentContext("");
    Location location = new Location("file:///fake-uri2.bsl", Ranges.create(0, 2, 2, 3));
    diagnostics.get(0).setRelatedInformation(Collections.singletonList(new DiagnosticRelatedInformation(location, "message")));

    String sourceDir = ".";
    FileInfo fileInfo = new FileInfo(sourceDir, documentContext, diagnostics);
    AnalysisInfo analysisInfo = new AnalysisInfo(LocalDateTime.now(), Collections.singletonList(fileInfo), sourceDir);

    // when
    reporter.report(analysisInfo, Path.of(sourceDir));

    // then
    ObjectMapper mapper = new ObjectMapper();
    GenericIssueReport report = mapper.readValue(file, GenericIssueReport.class);
    assertThat(report).isNotNull();
    assertThat(report.getIssues()).isNotNull();
    assertThat(report.getIssues().size()).isEqualTo(3);
    assertThat(report.getIssues().get(0).getPrimaryLocation()).isNotNull();
    assertThat(report.getIssues().get(0).getSecondaryLocations()).isNotNull();
    assertThat(report.getIssues().get(0).getSecondaryLocations().size()).isEqualTo(1);
    assertThat(report.getIssues().get(2).getRuleId()).isEqualTo(secondInfo.getCode().getStringValue());
    assertThat(report.getIssues().get(1).getSeverity()).isEqualTo(firstInfo.getSeverity().name());
  }

}
