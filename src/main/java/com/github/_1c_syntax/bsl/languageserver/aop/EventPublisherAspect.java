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
package com.github._1c_syntax.bsl.languageserver.aop;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.configuration.events.LanguageServerConfigurationChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextContentChangedEvent;
import com.github._1c_syntax.bsl.languageserver.events.LanguageServerInitializeRequestReceivedEvent;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.services.LanguageServer;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;

import javax.annotation.PreDestroy;

/**
 * Аспект подсистемы событий.
 * <p>
 * Каждый advice перехватывает какой-либо метод из недр продукта и генерирует соответствующее событие
 * с помощью Spring Events.
 */
@Aspect
@Slf4j
@NoArgsConstructor
public class EventPublisherAspect implements ApplicationEventPublisherAware {

  private boolean active;
  private ApplicationEventPublisher applicationEventPublisher;

  @PreDestroy
  public void destroy() {
    active = false;
    applicationEventPublisher = null;
  }

  public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
    active = true;
    this.applicationEventPublisher = applicationEventPublisher;
  }

  @AfterReturning("Pointcuts.isLanguageServerConfiguration() && (Pointcuts.isResetCall() || Pointcuts.isUpdateCall())")
  public void languageServerConfigurationUpdated(JoinPoint joinPoint) {
    publishEvent(new LanguageServerConfigurationChangedEvent((LanguageServerConfiguration) joinPoint.getThis()));
  }

  @AfterReturning("Pointcuts.isDocumentContext() && Pointcuts.isRebuildCall()")
  public void documentContextRebuild(JoinPoint joinPoint) {
    publishEvent(new DocumentContextContentChangedEvent((DocumentContext) joinPoint.getThis()));
  }

  @AfterReturning("Pointcuts.isLanguageServer() && Pointcuts.isInitializeCall() && args(initializeParams)")
  public void languageServerInitialize(JoinPoint joinPoint, InitializeParams initializeParams) {
    var event = new LanguageServerInitializeRequestReceivedEvent(
      (LanguageServer) joinPoint.getThis(),
      initializeParams
    );
    publishEvent(event);
  }

  private void publishEvent(ApplicationEvent event) {
    if (!active) {
      LOGGER.warn("Trying to send event in not active event publisher.");
      return;
    }
    applicationEventPublisher.publishEvent(event);
  }
}
