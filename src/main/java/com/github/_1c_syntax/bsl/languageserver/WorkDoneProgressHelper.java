/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2022
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

import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.ProgressParams;
import org.eclipse.lsp4j.WindowClientCapabilities;
import org.eclipse.lsp4j.WorkDoneProgressBegin;
import org.eclipse.lsp4j.WorkDoneProgressCreateParams;
import org.eclipse.lsp4j.WorkDoneProgressEnd;
import org.eclipse.lsp4j.WorkDoneProgressReport;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
public class WorkDoneProgressHelper {

  private final LanguageClientHolder languageClientHolder;
  private final ClientCapabilitiesHolder clientCapabilitiesHolder;

  private boolean isWorkDoneProgressSupported;

  public WorkDoneProgressReporter createProgress(int size, String messagePostfix) {
    isWorkDoneProgressSupported = clientCapabilitiesHolder.getCapabilities()
      .map(ClientCapabilities::getWindow)
      .map(WindowClientCapabilities::getWorkDoneProgress)
      .orElse(false);

    if (!isWorkDoneProgressSupported) {
      return new WorkDoneProgressReporter("", 0, "");
    }

    var token = UUID.randomUUID().toString();
    var createProgressParams = new WorkDoneProgressCreateParams(Either.forLeft(token));

    languageClientHolder.execIfConnected(languageClient ->
      languageClient.createProgress(createProgressParams)
    );

    return new WorkDoneProgressReporter(token, size, messagePostfix);
  }


  @AllArgsConstructor
  public class WorkDoneProgressReporter {
    private final String token;
    @Setter
    private int size;
    private final String messagePostfix;

    private final AtomicInteger counter = new AtomicInteger();

    public void beginProgress(String title) {
      if (!isWorkDoneProgressSupported) {
        return;
      }

      languageClientHolder.execIfConnected((LanguageClient languageClient) -> {
        var value = new WorkDoneProgressBegin();
        value.setTitle(title);

        var params = new ProgressParams(Either.forLeft(token), Either.forLeft(value));
        languageClient.notifyProgress(params);
      });
    }

    public void tick(String message, int percentage) {
      if (!isWorkDoneProgressSupported) {
        return;
      }

      languageClientHolder.execIfConnected((LanguageClient languageClient) -> {
        var value = new WorkDoneProgressReport();
        value.setMessage(message);
        value.setCancellable(false);
        value.setPercentage(percentage);

        var params = new ProgressParams(Either.forLeft(token), Either.forLeft(value));
        languageClient.notifyProgress(params);
      });
    }

    public void tick() {
      if (!isWorkDoneProgressSupported) {
        return;
      }

      var currentCounter = counter.incrementAndGet();
      var message = String.format("%d/%d%s", currentCounter, size, messagePostfix);
      var percentage = (double) currentCounter / size * 100;

      tick(message, (int) percentage);
    }

    public void endProgress(String message) {
      if (!isWorkDoneProgressSupported) {
        return;
      }

      languageClientHolder.execIfConnected((LanguageClient languageClient) -> {
        var value = new WorkDoneProgressEnd();
        value.setMessage(message);

        var params = new ProgressParams(Either.forLeft(token), Either.forLeft(value));
        languageClient.notifyProgress(params);
      });
    }
  }
}
