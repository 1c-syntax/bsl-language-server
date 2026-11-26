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
package com.github._1c_syntax.bsl.languageserver.lsp;

import com.github._1c_syntax.bsl.languageserver.events.LanguageServerInitializedEvent;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ParentProcessWatcherTest {

  private static final Duration EXIT_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration QUIET_PERIOD = Duration.ofSeconds(1);

  @InjectMocks
  private ParentProcessWatcher parentProcessWatcher;

  @Mock
  private LanguageServer languageServer;

  private final List<Process> startedProcesses = new ArrayList<>();

  @AfterEach
  void tearDown() {
    startedProcesses.forEach(Process::destroyForcibly);
  }

  @Test
  void testExitWhenParentProcessTerminates() throws Exception {
    // given
    var parentProcess = startParentProcess();
    initialize(parentProcess.pid());

    // when
    parentProcess.destroy();

    // then
    await()
      .atMost(EXIT_TIMEOUT)
      .untilAsserted(() -> verify(languageServer).exit());
  }

  @Test
  void testExitWhenParentProcessIsAlreadyGone() throws Exception {
    // given
    var parentProcess = startParentProcess();
    parentProcess.destroyForcibly().waitFor();

    // when
    initialize(parentProcess.pid());

    // then
    await()
      .atMost(EXIT_TIMEOUT)
      .untilAsserted(() -> verify(languageServer).exit());
  }

  @Test
  void testNoExitWhileParentProcessIsAlive() throws Exception {
    // given
    var parentProcess = startParentProcess();

    // when
    initialize(parentProcess.pid());

    // then
    await()
      .during(QUIET_PERIOD)
      .atMost(QUIET_PERIOD.plusSeconds(1))
      .untilAsserted(() -> verify(languageServer, never()).exit());
  }

  @Test
  void testNoExitWhenParentProcessIsCurrentProcess() {
    // when
    initialize(ProcessHandle.current().pid());

    // then
    await()
      .during(QUIET_PERIOD)
      .atMost(QUIET_PERIOD.plusSeconds(1))
      .untilAsserted(() -> verify(languageServer, never()).exit());
  }

  @Test
  void testNoExitWithoutParentProcessId() {
    // when
    initialize(null);

    // then
    await()
      .during(QUIET_PERIOD)
      .atMost(QUIET_PERIOD.plusSeconds(1))
      .untilAsserted(() -> verify(languageServer, never()).exit());
  }

  private void initialize(@Nullable Long processId) {
    var params = new InitializeParams();
    params.setProcessId(processId == null ? null : Math.toIntExact(processId));
    parentProcessWatcher.handleEvent(new LanguageServerInitializedEvent(languageServer, params));
  }

  /**
   * Запускает «родительский» процесс — отдельную JVM, которая просто спит.
   * Кроссплатформенно, в отличие от утилит ОС вроде {@code sleep}/{@code timeout}.
   */
  private Process startParentProcess() throws Exception {
    var java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    var classPath = Path.of(SleepingProcess.class.getProtectionDomain().getCodeSource().getLocation().toURI())
      .toString();
    var process = new ProcessBuilder(java, "-cp", classPath, SleepingProcess.class.getName())
      .redirectErrorStream(true)
      .redirectOutput(ProcessBuilder.Redirect.DISCARD)
      .start();
    startedProcesses.add(process);
    return process;
  }

  static class SleepingProcess {
    public static void main(String[] args) throws InterruptedException {
      Thread.sleep(Duration.ofMinutes(1));
    }
  }

}
