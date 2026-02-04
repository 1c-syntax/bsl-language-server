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
package com.github._1c_syntax.bsl.languageserver.aop;

import com.github._1c_syntax.bsl.languageserver.configuration.GlobalLanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.configuration.events.GlobalLanguageServerConfigurationChangedEvent;
import com.github._1c_syntax.bsl.languageserver.configuration.events.LanguageServerConfigurationChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.context.events.BeforeWorkspaceRemovedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextContentChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentAddedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentClosedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentRemovedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextPopulatedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.WorkspaceAddedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.WorkspaceRemovedEvent;
import com.github._1c_syntax.bsl.languageserver.events.LanguageServerInitializeRequestReceivedEvent;
import jakarta.annotation.PreDestroy;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.services.LanguageServer;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;

import java.io.File;
import java.net.URI;
import java.util.Collection;

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
  @SuppressWarnings("NullAway.Init")
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

  @AfterReturning("Pointcuts.isGlobalLanguageServerConfiguration() && (Pointcuts.isResetCall() || Pointcuts.isUpdateCall())")
  public void globalLanguageServerConfigurationUpdated(JoinPoint joinPoint) {
    publishEvent(new GlobalLanguageServerConfigurationChangedEvent(
      (GlobalLanguageServerConfiguration) joinPoint.getThis()
    ));
  }

  @AfterReturning("Pointcuts.isDocumentContext() && Pointcuts.isRebuildCall()")
  public void documentContextRebuild(JoinPoint joinPoint) {
    publishEvent(new DocumentContextContentChangedEvent((DocumentContext) joinPoint.getThis()));
  }

  @AfterReturning("Pointcuts.isServerContext() && Pointcuts.isPopulateContextCall() && args(files)")
  public void serverContextPopulated(JoinPoint joinPoint, Collection<File> files) {
    publishEvent(new ServerContextPopulatedEvent((ServerContext) joinPoint.getThis()));
  }

  @AfterReturning("Pointcuts.isServerContext() && Pointcuts.isAddDocumentCall() && args(uri)")
  public void serverContextAddDocument(JoinPoint joinPoint, URI uri) {
    publishEvent(new ServerContextDocumentAddedEvent((ServerContext) joinPoint.getThis(), uri));
  }

  @AfterReturning("Pointcuts.isServerContext() && Pointcuts.isRemoveDocumentCall() && args(uri)")
  public void serverContextRemoveDocument(JoinPoint joinPoint, URI uri) {
    publishEvent(new ServerContextDocumentRemovedEvent((ServerContext) joinPoint.getThis(), uri));
  }

  @AfterReturning("Pointcuts.isServerContext() && Pointcuts.isCloseDocumentCall() && args(documentContext)")
  public void serverContextCloseDocument(JoinPoint joinPoint, DocumentContext documentContext) {
    publishEvent(new ServerContextDocumentClosedEvent((ServerContext) joinPoint.getThis(), documentContext));
  }

  @AfterReturning("Pointcuts.isLanguageServer() && Pointcuts.isInitializeCall() && args(initializeParams)")
  public void languageServerInitialize(JoinPoint joinPoint, InitializeParams initializeParams) {
    var event = new LanguageServerInitializeRequestReceivedEvent(
      (LanguageServer) joinPoint.getThis(),
      initializeParams
    );
    publishEvent(event);
  }

  @AfterReturning(
    pointcut = "Pointcuts.isServerContextProvider() && Pointcuts.isAddWorkspaceCall() && args(workspaceUri)",
    returning = "serverContext"
  )
  public void workspaceAdded(JoinPoint joinPoint, URI workspaceUri, ServerContext serverContext) {
    publishEvent(new WorkspaceAddedEvent(
      (ServerContextProvider) joinPoint.getThis(),
      workspaceUri,
      serverContext
    ));
  }

  @Before("Pointcuts.isServerContextProvider() && Pointcuts.isRemoveWorkspaceCall() && args(workspaceFolder)")
  public void beforeWorkspaceRemoved(JoinPoint joinPoint, WorkspaceFolder workspaceFolder) {
    var provider = (ServerContextProvider) joinPoint.getThis();
    var uri = URI.create(workspaceFolder.getUri());
    var serverContext = provider.getServerContextUnsafe(uri).orElse(null);
    if (serverContext != null) {
      publishEvent(new BeforeWorkspaceRemovedEvent(provider, uri, serverContext));
    }
  }

  @After("Pointcuts.isServerContextProvider() && Pointcuts.isRemoveWorkspaceCall() && args(workspaceFolder)")
  public void workspaceRemoved(JoinPoint joinPoint, WorkspaceFolder workspaceFolder) {
    var uri = URI.create(workspaceFolder.getUri());
    publishEvent(new WorkspaceRemovedEvent(
      (ServerContextProvider) joinPoint.getThis(),
      uri
    ));
  }

  private void publishEvent(ApplicationEvent event) {
    if (!active || applicationEventPublisher == null) {
      LOGGER.warn("Trying to send event in not active event publisher.");
      return;
    }
    applicationEventPublisher.publishEvent(event);
  }
}
