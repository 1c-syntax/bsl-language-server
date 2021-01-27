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
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;

/**
 * Аспект подсистемы событий.
 * <p/>
 * Каждый advice перехватывает какой-либо метод из недр продукта и генерирует соответствующее событие
 * с помощью Spring Events.
 */
@Aspect
@NoArgsConstructor
public class EventPublisherAspect implements ApplicationEventPublisherAware {

  @Setter
  private ApplicationEventPublisher applicationEventPublisher;

  @AfterReturning("Pointcuts.isLanguageServerConfiguration() && (Pointcuts.isResetCall() || Pointcuts.isUpdateCall())")
  public void languageServerConfigurationUpdated(JoinPoint joinPoint) {
    var configuration = (LanguageServerConfiguration) joinPoint.getThis();
    applicationEventPublisher.publishEvent(new LanguageServerConfigurationChangedEvent(configuration));
  }

  @AfterReturning("Pointcuts.isDocumentContext() && Pointcuts.isRebuildCall()")
  public void documentContextRebuild(JoinPoint joinPoint) {
    var documentContext = (DocumentContext) joinPoint.getThis();
    applicationEventPublisher.publishEvent(new DocumentContextContentChangedEvent(documentContext));
  }

}
