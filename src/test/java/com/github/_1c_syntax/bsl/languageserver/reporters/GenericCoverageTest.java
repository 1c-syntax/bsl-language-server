/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2021
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
package com.github._1c_syntax.bsl.languageserver.reporters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.reporters.data.AnalysisInfo;
import com.github._1c_syntax.bsl.languageserver.reporters.data.FileInfo;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class GenericCoverageTest {

  private final File file = new File("./genericCoverage.xml");

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
    String sourceDir = ".";
    String filePath = "./src/test/resources/context/DocumentContextLocForCoverTest.bsl";

    // when
    DocumentContext documentContext = TestUtils.getDocumentContextFromFile(filePath);
    FileInfo fileInfo = new FileInfo(sourceDir, documentContext, new ArrayList<>());
    AnalysisInfo analysisInfo = new AnalysisInfo(LocalDateTime.now(), Collections.singletonList(fileInfo), sourceDir);

    DiagnosticReporter reporter = new GenericCoverageReporter();
    reporter.report(analysisInfo, Path.of(sourceDir));

    // then
    ObjectMapper mapper = new XmlMapper();
    GenericCoverageReport report = mapper.readValue(file, GenericCoverageReport.class);

    assertThat(report).isNotNull();
    assertThat(report.getVersion()).isEqualTo("1");
    assertThat(report.getFile().size()).isEqualTo(1);

    GenericCoverageReport.GenericCoverageReportEntry fileEntry = report.getFile().get(0);

    assertThat(fileEntry.getPath()).isEqualTo(fileInfo.getPath().toString());

    checkLineToCoverEntry(fileEntry, 0, 6);
    checkLineToCoverEntry(fileEntry, 1, 10);
    checkLineToCoverEntry(fileEntry, 2, 11);
    checkLineToCoverEntry(fileEntry, 3, 13);
    checkLineToCoverEntry(fileEntry, 4, 18);
    checkLineToCoverEntry(fileEntry, 5, 28);
    checkLineToCoverEntry(fileEntry, 6, 31);
    checkLineToCoverEntry(fileEntry, 7, 32);
    checkLineToCoverEntry(fileEntry, 8, 35);
    checkLineToCoverEntry(fileEntry, 9, 37);
    checkLineToCoverEntry(fileEntry, 10, 44);
    checkLineToCoverEntry(fileEntry, 11, 45);
    checkLineToCoverEntry(fileEntry, 12, 49);
    checkLineToCoverEntry(fileEntry, 13, 50);
    checkLineToCoverEntry(fileEntry, 14, 54);
    checkLineToCoverEntry(fileEntry, 15, 58);
    checkLineToCoverEntry(fileEntry, 16, 60);

    assertThat(fileEntry.getLineToCover().size()).isEqualTo(17);
  }

  private static void checkLineToCoverEntry(GenericCoverageReport.GenericCoverageReportEntry fileEntry, int index, int expectedLine) {
    GenericCoverageReport.LineToCoverEntry lineToCover = fileEntry.getLineToCover().get(index);

    assertThat(lineToCover.getLineNumber()).isEqualTo(expectedLine);
    assertThat(lineToCover.isCovered()).isFalse();
  }
}
