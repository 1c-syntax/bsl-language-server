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
package com.github._1c_syntax.bsl.languageserver.aop;

import com.github._1c_syntax.bsl.languageserver.LanguageClientHolder;
import com.github._1c_syntax.bsl.languageserver.utils.Resources;
import io.sentry.Sentry;
import io.sentry.protocol.SentryId;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CompletableFuture;

/**
 * Аспект перехвата исключений и регистрации их в Sentry.
 */
@Aspect
@NoArgsConstructor
public class SentryAspect {

  @Setter(onMethod = @__({@Autowired}))
  @Nullable
  private LanguageClientHolder languageClientHolder;

  @Setter(onMethod = @__({@Autowired}))
  private Resources resources;

  @Setter(onMethod = @__({@Autowired, @Qualifier("sentryExecutor")}))
  private ThreadPoolTaskExecutor executor;

  @AfterThrowing(value = "Pointcuts.isBSLDiagnostic() && Pointcuts.isGetDiagnosticsCall()", throwing = "ex")
  public void logThrowingBSLDiagnosticGetDiagnostics(Throwable ex) {
    logException(ex);
  }

  @AfterThrowing(value =
    "Pointcuts.isPublicMethodCall() && (Pointcuts.isLanguageServer() || Pointcuts.isTextDocumentService() || Pointcuts.isWorkspaceService())",
    throwing = "ex"
  )
  public void logThrowingLSPCall(Throwable ex) {
    logException(ex);
  }

  private void logException(Throwable ex) {
    CompletableFuture.runAsync(() -> {
        var sentryId = Sentry.captureException(ex);
        if (sentryId.equals(SentryId.EMPTY_ID)) {
          return;}

        if (languageClientHolder == null) {
          return;
        }
        var messageType = MessageType.Info;
        var message = resources.getResourceString(getClass(), "logMessage", sentryId);
        var messageParams = new MessageParams(messageType, message);

        languageClientHolder.execIfConnected(languageClient -> languageClient.showMessage(messageParams));
      },
      executor
    );
  }

}
