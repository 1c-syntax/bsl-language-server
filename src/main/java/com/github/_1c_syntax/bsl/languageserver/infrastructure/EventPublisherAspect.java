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
package com.github._1c_syntax.bsl.languageserver.infrastructure;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.configuration.watcher.LanguageServerConfigurationChangeEvent;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContextContentChangedEvent;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

@Aspect
public class EventPublisherAspect {

  private ApplicationEventPublisher eventPublisher;

  @Pointcut("within(com.github._1c_syntax.bsl.languageserver.context.DocumentContext)")
  public void isDocumentContext() {
  }

  @Pointcut("within(com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration)")
  public void isLanguageServerConfiguration() {
  }

  @Pointcut("execution(* com.github._1c_syntax.bsl.languageserver..*.update(..))")
  public void isUpdateCall() {
  }

  @Pointcut("execution(* com.github._1c_syntax.bsl.languageserver..*.rebuild(..))")
  public void isRebuildCall() {
  }

  @Pointcut("execution(* com.github._1c_syntax.bsl.languageserver..*.reset(..))")
  public void isResetCall() {
  }

  @AfterReturning("isDocumentContext() && isRebuildCall()")
  public void documentContextRebuild(JoinPoint joinPoint) {
    var documentContext = (DocumentContext) joinPoint.getThis();
    eventPublisher.publishEvent(new DocumentContextContentChangedEvent(documentContext));
  }

  @AfterReturning("isLanguageServerConfiguration() && (isResetCall() || isUpdateCall())")
  public void languageServerConfigurationUpdated(JoinPoint joinPoint) {
    var configuration = (LanguageServerConfiguration) joinPoint.getThis();
    eventPublisher.publishEvent(new LanguageServerConfigurationChangeEvent(configuration));
  }

  @Autowired
  public void setEventPublisher(ApplicationEventPublisher eventPublisher) {
    this.eventPublisher = eventPublisher;
  }
}
