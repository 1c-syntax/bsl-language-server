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

import com.github._1c_syntax.bsl.languageserver.reporters.data.FileInfo;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportSessionTest {

  @Test
  void deliversEveryResultToEveryReporter() {
    var first = new RecordingReporter("first");
    var second = new RecordingReporter("second");

    try (var session = new ReportSession(List.of(first, second))) {
      session.accept(fileInfo("a.bsl"));
      session.accept(fileInfo("b.bsl"));
      session.commit();
    }

    assertThat(first.accepted).containsExactlyInAnyOrder("a.bsl", "b.bsl");
    assertThat(second.accepted).containsExactlyInAnyOrder("a.bsl", "b.bsl");
  }

  @Test
  void commitFinishesReports() {
    var reporter = new RecordingReporter("only");

    try (var session = new ReportSession(List.of(reporter))) {
      session.accept(fileInfo("a.bsl"));
      session.commit();
    }

    assertThat(reporter.ended).isTrue();
    assertThat(reporter.aborted).isFalse();
  }

  @Test
  void closeWithoutCommitAbortsReports() {
    var reporter = new RecordingReporter("only");

    try (var session = new ReportSession(List.of(reporter))) {
      session.accept(fileInfo("a.bsl"));
      // фиксации нет: анализ не дошёл до конца
    }

    assertThat(reporter.aborted).isTrue();
    assertThat(reporter.ended).isFalse();
  }

  @Test
  void writeFailureAbortsReportsAndPropagates() {
    var failing = new RecordingReporter("failing");
    failing.failOnAccept = true;
    var healthy = new RecordingReporter("healthy");

    var session = new ReportSession(List.of(failing, healthy));
    session.accept(fileInfo("a.bsl"));
    session.commit();

    assertThatThrownBy(session::close)
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("не смог записать");

    assertThat(failing.aborted).isTrue();
    assertThat(healthy.aborted).isTrue();
    assertThat(healthy.ended).isFalse();
  }

  @Test
  void failureToFinishOneReportDoesNotSkipOthers() {
    var failing = new RecordingReporter("failing");
    failing.failOnEnd = true;
    var healthy = new RecordingReporter("healthy");

    var session = new ReportSession(List.of(failing, healthy));
    session.commit();

    assertThatThrownBy(session::close).isInstanceOf(IllegalStateException.class);

    // упавший откатывается, остальные всё равно завершаются
    assertThat(failing.aborted).isTrue();
    assertThat(healthy.ended).isTrue();
  }

  private static FileInfo fileInfo(String name) {
    return new FileInfo(Path.of(name), "", List.of(), null);
  }

  private static final class RecordingReporter implements DiagnosticReporter {

    private final String key;
    private final List<String> accepted = new ArrayList<>();

    private boolean ended;
    private boolean aborted;
    private boolean failOnAccept;
    private boolean failOnEnd;

    private RecordingReporter(String key) {
      this.key = key;
    }

    @Override
    public String key() {
      return key;
    }

    @Override
    public void beginReport(ReportContext context, Path outputDir) {
      // нечего открывать
    }

    @Override
    public void accept(FileInfo fileInfo) {
      if (failOnAccept) {
        throw new IllegalStateException("не смог записать");
      }
      accepted.add(fileInfo.getPath().toString());
    }

    @Override
    public void endReport() {
      if (failOnEnd) {
        throw new IllegalStateException("не смог завершить");
      }
      ended = true;
    }

    @Override
    public void abortReport() {
      aborted = true;
    }
  }
}
