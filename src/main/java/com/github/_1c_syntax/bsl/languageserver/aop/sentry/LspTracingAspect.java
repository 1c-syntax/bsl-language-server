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
package com.github._1c_syntax.bsl.languageserver.aop.sentry;

import io.sentry.ISpan;
import io.sentry.Sentry;
import io.sentry.SpanStatus;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;

/**
 * Аспект для трассировки LSP-запросов в Sentry.
 * <p>
 * Создаёт span'ы для LSP-методов напрямую от корневой сессионной транзакции,
 * что обеспечивает корректную иерархию span'ов независимо от многопоточности.
 */
@Aspect
@NoArgsConstructor
@Order(1) // Выполнять раньше стандартного SentryTracingAspect
public class LspTracingAspect {

  @Setter(onMethod_ = @Autowired)
  private SentrySessionTransaction sentrySessionTransaction;

  /**
   * Pointcut для публичных методов BSLTextDocumentService.
   */
  @Pointcut("execution(public * com.github._1c_syntax.bsl.languageserver.BSLTextDocumentService.*(..))")
  public void textDocumentServiceMethods() {
    // no-op
  }

  /**
   * Pointcut для публичных методов BSLLanguageServer.
   */
  @Pointcut("execution(public * com.github._1c_syntax.bsl.languageserver.BSLLanguageServer.*(..))")
  public void languageServerMethods() {
    // no-op
  }

  /**
   * Pointcut для публичных методов BSLWorkspaceService.
   */
  @Pointcut("execution(public * com.github._1c_syntax.bsl.languageserver.BSLWorkspaceService.*(..))")
  public void workspaceServiceMethods() {
    // no-op
  }

  /**
   * Трассировка LSP-методов.
   */
  @Around("textDocumentServiceMethods() || languageServerMethods() || workspaceServiceMethods()")
  public Object traceLspMethod(ProceedingJoinPoint joinPoint) throws Throwable {
    if (sentrySessionTransaction == null) {
      return joinPoint.proceed();
    }

    var sessionTransaction = sentrySessionTransaction.getSessionTransaction();
    if (sessionTransaction == null) {
      return joinPoint.proceed();
    }

    var className = joinPoint.getSignature().getDeclaringType().getSimpleName();
    var methodName = joinPoint.getSignature().getName();
    var operation = className + "." + methodName;

    // Создаём span как child сессионной транзакции
    // Транзакция привязана к scope через setBindToScope(true),
    // поэтому Sentry.getSpan() будет возвращать её для вложенных вызовов
    ISpan span = sessionTransaction.startChild(operation);

    try {
      var result = joinPoint.proceed();
      span.setStatus(SpanStatus.OK);
      return result;
    } catch (Throwable e) {
      span.setStatus(SpanStatus.INTERNAL_ERROR);
      span.setThrowable(e);
      throw e;
    } finally {
      span.finish();
    }
  }
}

