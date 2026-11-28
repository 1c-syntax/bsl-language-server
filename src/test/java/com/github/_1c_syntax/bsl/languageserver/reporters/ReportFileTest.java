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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ReportFileTest {

  @TempDir
  Path outputDir;

  @Test
  void commitKeepsFileWithWrittenContent() throws Exception {
    var reportFile = ReportFile.create(outputDir, "report.json");
    reportFile.stream().write("содержимое".getBytes(StandardCharsets.UTF_8));

    reportFile.commit("JSON");

    assertThat(outputDir.resolve("report.json"))
      .content(StandardCharsets.UTF_8)
      .isEqualTo("содержимое");
  }

  @Test
  void commitWithReportNameAnnouncesWhereReportIsSaved() {
    // given
    var reportFile = ReportFile.create(outputDir, "report.json");
    var reportFileLog = (Logger) LoggerFactory.getLogger(ReportFile.class);
    var logged = new ListAppender<ILoggingEvent>();
    logged.start();
    reportFileLog.addAppender(logged);

    // when
    try {
      reportFile.commit("JSON");
    } finally {
      reportFileLog.detachAppender(logged);
    }

    // then
    assertThat(outputDir.resolve("report.json")).exists();
    assertThat(logged.list).anySatisfy(event -> {
      assertThat(event.getLevel()).isEqualTo(Level.INFO);
      assertThat(event.getFormattedMessage())
        .isEqualTo("JSON report saved to " + outputDir.resolve("report.json").toAbsolutePath());
    });
  }

  @Test
  void discardDeletesOpenedReportAndToleratesUnstartedOne() throws Exception {
    // given
    var reportFile = ReportFile.create(outputDir, "report.json");
    reportFile.stream().write("частичные данные".getBytes(StandardCharsets.UTF_8));

    // when
    ReportFile.discard(reportFile);
    ReportFile.discard(null);

    // then
    assertThat(outputDir.resolve("report.json")).doesNotExist();
  }

  @Test
  void closeWithoutCommitDeletesFile() throws Exception {
    var reportFile = ReportFile.create(outputDir, "report.json");
    reportFile.stream().write("частичные данные".getBytes(StandardCharsets.UTF_8));

    reportFile.close();

    assertThat(outputDir.resolve("report.json")).doesNotExist();
  }

  @Test
  void closeAfterCommitKeepsFile() throws Exception {
    var reportFile = ReportFile.create(outputDir, "report.json");
    reportFile.stream().write("содержимое".getBytes(StandardCharsets.UTF_8));
    reportFile.commit("JSON");

    reportFile.close();

    assertThat(outputDir.resolve("report.json")).exists();
  }

  @Test
  void closeWithoutCommitRemovesPreviousReportInsteadOfLeavingItStale() throws Exception {
    Files.writeString(outputDir.resolve("report.json"), "отчёт прошлого прогона");

    var reportFile = ReportFile.create(outputDir, "report.json");
    reportFile.close();

    assertThat(outputDir.resolve("report.json")).doesNotExist();
  }

  @Test
  void createResolvesNestedOutputDirectory() throws Exception {
    var nested = outputDir.resolve("вложенный");

    var reportFile = ReportFile.create(nested, "report.json");
    reportFile.stream().write("x".getBytes(StandardCharsets.UTF_8));
    reportFile.commit("JSON");

    assertThat(nested.resolve("report.json")).exists();
  }

  @Test
  void createResolvesFileNameWithoutDotSegment() throws Exception {
    var reportFile = ReportFile.create(outputDir, "bsl-json.json");
    reportFile.stream().write("x".getBytes(StandardCharsets.UTF_8));
    reportFile.commit("JSON");

    assertThat(reportFile.path()).isEqualTo(outputDir.resolve("bsl-json.json"));
  }
}
