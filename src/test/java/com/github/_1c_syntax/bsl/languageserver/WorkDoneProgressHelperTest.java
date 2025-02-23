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
package com.github._1c_syntax.bsl.languageserver;

import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.ProgressParams;
import org.eclipse.lsp4j.WindowClientCapabilities;
import org.eclipse.lsp4j.WorkDoneProgressBegin;
import org.eclipse.lsp4j.WorkDoneProgressEnd;
import org.eclipse.lsp4j.WorkDoneProgressReport;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class WorkDoneProgressHelperTest {

  @Test
  void noOpProgressWithoutWorkDoneSupport() {
    // given
    var languageClientHolder = getLanguageClientHolder();
    var languageClient = languageClientHolder.getClient().orElseThrow();

    var clientCapabilitiesHolder = getClientCapabilitiesHolder(Boolean.FALSE);
    var workDoneProgressHelper = new WorkDoneProgressHelper(languageClientHolder, clientCapabilitiesHolder);

    // when
    var progress = workDoneProgressHelper.createProgress(1, "");
    progress.beginProgress("test");
    progress.tick();
    progress.endProgress("");

    // then
    verify(languageClient, never()).createProgress(any());
    verify(languageClient, never()).notifyProgress(any());
  }

  @Test
  void createProgressWithWorkDoneSupport() {
    // given
    var languageClientHolder = getLanguageClientHolder();
    var languageClient = languageClientHolder.getClient().orElseThrow();

    var clientCapabilitiesHolder = getClientCapabilitiesHolder(Boolean.TRUE);
    var workDoneProgressHelper = new WorkDoneProgressHelper(languageClientHolder, clientCapabilitiesHolder);

    // when
    workDoneProgressHelper.createProgress(1, "");

    // then
    verify(languageClient, times(1)).createProgress(any());
  }

  @Test
  void beginProgress() {
    // given
    var languageClientHolder = getLanguageClientHolder();
    var languageClient = languageClientHolder.getClient().orElseThrow();

    var clientCapabilitiesHolder = getClientCapabilitiesHolder(Boolean.TRUE);
    var workDoneProgressHelper = new WorkDoneProgressHelper(languageClientHolder, clientCapabilitiesHolder);

    var progress = workDoneProgressHelper.createProgress(1, "");

    // when
    progress.beginProgress("test");

    // then
    var begin = new WorkDoneProgressBegin();
    begin.setTitle("test");
    var progressParams = new ProgressParams(Either.forLeft("token"), Either.forLeft(begin));

    verify(languageClient, times(1))
      .notifyProgress(
        refEq(
          progressParams,
          "token"
        )
      );
  }

  @Test
  void tick() {
    // given
    var languageClientHolder = getLanguageClientHolder();
    var languageClient = languageClientHolder.getClient().orElseThrow();

    var clientCapabilitiesHolder = getClientCapabilitiesHolder(Boolean.TRUE);
    var workDoneProgressHelper = new WorkDoneProgressHelper(languageClientHolder, clientCapabilitiesHolder);

    var progress = workDoneProgressHelper.createProgress(2, " files");
    progress.beginProgress("test");

    // when
    progress.tick();

    // then
    var report = new WorkDoneProgressReport();
    report.setMessage("1/2 files");
    report.setCancellable(false);
    report.setPercentage(50);

    var progressParams = new ProgressParams(Either.forLeft("token"), Either.forLeft(report));

    verify(languageClient, times(1))
      .notifyProgress(
        refEq(
          progressParams,
          "token"
        )
      );

    // when
    // one more tick
    progress.tick();

    // then
    report.setMessage("2/2 files");
    report.setPercentage(100);

    progressParams = new ProgressParams(Either.forLeft("token"), Either.forLeft(report));

    verify(languageClient, times(1))
      .notifyProgress(
        refEq(
          progressParams,
          "token"
        )
      );
  }

  @Test
  void endProgress() {
    // given
    var languageClientHolder = getLanguageClientHolder();
    var languageClient = languageClientHolder.getClient().orElseThrow();

    var clientCapabilitiesHolder = getClientCapabilitiesHolder(Boolean.TRUE);
    var workDoneProgressHelper = new WorkDoneProgressHelper(languageClientHolder, clientCapabilitiesHolder);

    var progress = workDoneProgressHelper.createProgress(1, "");

    progress.beginProgress("test");

    // when
    progress.endProgress("end");

    // then
    var end = new WorkDoneProgressEnd();
    end.setMessage("end");
    var progressParams = new ProgressParams(Either.forLeft("token"), Either.forLeft(end));

    verify(languageClient, times(1))
      .notifyProgress(
        refEq(
          progressParams,
          "token"
        )
      );
  }

  private LanguageClientHolder getLanguageClientHolder() {
    var languageClient = mock(LanguageClient.class);
    var languageClientHolder = new LanguageClientHolder();
    languageClientHolder.connect(languageClient);

    return languageClientHolder;
  }

  private ClientCapabilitiesHolder getClientCapabilitiesHolder(Boolean workDoneProgress) {
    var windowClientCapabilities = new WindowClientCapabilities();
    windowClientCapabilities.setWorkDoneProgress(workDoneProgress);

    var clientCapabilities = new ClientCapabilities();
    clientCapabilities.setWindow(windowClientCapabilities);

    var clientCapabilitiesHolder = new ClientCapabilitiesHolder();
    clientCapabilitiesHolder.setCapabilities(clientCapabilities);

    return clientCapabilitiesHolder;
  }
}
