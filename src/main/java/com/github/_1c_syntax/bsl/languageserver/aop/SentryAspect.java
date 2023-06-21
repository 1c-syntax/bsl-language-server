/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2023
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

import io.sentry.Sentry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.NoArgsConstructor;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Аспект перехвата исключений и регистрации их в Sentry.
 */
@Aspect
@NoArgsConstructor
public class SentryAspect {

  private ExecutorService executorService;

  @PostConstruct
  private void init() {
    executorService = Executors.newCachedThreadPool();
  }

  @PreDestroy
  private void onDestroy() {
    executorService.shutdown();
  }

  @AfterThrowing(value = "Pointcuts.isBSLDiagnostic() && Pointcuts.isGetDiagnosticsCall()", throwing = "ex")
  public void logThrowingBSLDiagnosticGetDiagnostics(Throwable ex) {
    CompletableFuture.runAsync(() -> Sentry.captureException(ex), executorService);
  }

  @AfterThrowing(value =
    "Pointcuts.isPublicMethodCall() && (Pointcuts.isLanguageServer() || Pointcuts.isTextDocumentService() || Pointcuts.isWorkspaceService())",
    throwing = "ex"
  )
  public void logThrowingLSPCall(Throwable ex) {
    CompletableFuture.runAsync(() -> Sentry.captureException(ex), executorService);
  }

}
