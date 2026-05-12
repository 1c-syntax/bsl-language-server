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
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
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
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.io.File;
import java.net.URI;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Аспект подсистемы событий.
 * <p>
 * Каждый advice перехватывает какой-либо метод из недр продукта и генерирует соответствующее событие
 * с помощью Spring Events.
 * <p>
 * Аспект является синглтоном AspectJ CTW — один экземпляр на всю JVM, независимо от количества
 * Spring-контекстов. Регистрация publisher-ов выполняется через {@link EventPublisherAspectRegistration},
 * по одному на каждый Spring-контекст. При закрытии контекста происходит отмена регистрации,
 * и аспект автоматически переключается на publisher следующего зарегистрированного контекста.
 */
@Aspect
@Slf4j
@NoArgsConstructor
public class EventPublisherAspect {

  /**
   * Упорядоченное множество зарегистрированных Spring-контекстов (порядок вставки = порядок регистрации).
   * <p>
   * При отмене регистрации одного контекста аспект переключается на первый оставшийся
   * (т.е. на дефолтный кешируемый контекст, зарегистрированный первым).
   * <p>
   * {@code LinkedHashSet} обеспечивает детерминированный fallback через {@code iterator().next()}.
   * Синхронизация выполняется явно методами {@code register}/{@code unregister}.
   */
  private final Set<ApplicationContext> registeredContexts = new LinkedHashSet<>();

  private boolean active;
  @SuppressWarnings("NullAway.Init")
  private ApplicationEventPublisher applicationEventPublisher;

  /**
   * Регистрирует Spring-контекст как источник событий.
   * Вызывается из {@link EventPublisherAspectRegistration} при инициализации каждого контекста.
   *
   * @param ctx регистрируемый контекст
   */
  synchronized void register(ApplicationContext ctx) {
    registeredContexts.add(ctx);
    applicationEventPublisher = ctx;
    active = true;
  }

  /**
   * Отменяет регистрацию Spring-контекста.
   * Вызывается из {@link EventPublisherAspectRegistration} при уничтожении каждого контекста.
   * <p>
   * Если после удаления остались другие контексты — аспект переключается на первый из них
   * (дефолтный кешируемый). Если контекстов не осталось — аспект переходит в неактивное состояние.
   *
   * @param ctx контекст, регистрацию которого необходимо отменить
   */
  synchronized void unregister(ApplicationContext ctx) {
    registeredContexts.remove(ctx);
    if (registeredContexts.isEmpty()) {
      active = false;
      applicationEventPublisher = null;
    } else {
      // Fallback на первый зарегистрированный оставшийся контекст.
      // В рамках одного JVM-форка тесты выполняются последовательно, поэтому одновременно
      // существуют не более двух контекстов: дефолтный (A) и @DirtiesContext (B).
      // При закрытии B первым в множестве всегда остаётся A — результат детерминирован.
      applicationEventPublisher = registeredContexts.iterator().next();
    }
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
    pointcut = "Pointcuts.isServerContextProvider() && Pointcuts.isAddWorkspaceCall() && args(workspaceUri, *)",
    returning = "serverContext"
  )
  public void workspaceAdded(JoinPoint joinPoint, URI workspaceUri, ServerContext serverContext) {
    // Устанавливаем workspace-контекст перед публикацией события, чтобы все слушатели
    // WorkspaceAddedEvent автоматически получили корректный WorkspaceContextHolder.
    // К моменту вызова этого advice try-with-resources внутри addWorkspace() уже закрыт
    // и ThreadLocal сброшен — поэтому явно оборачиваем здесь, а не полагаемся на вызывающий код.
    WorkspaceContextHolder.run(workspaceUri, () ->
      publishEvent(new WorkspaceAddedEvent(
        (ServerContextProvider) joinPoint.getThis(),
        workspaceUri,
        serverContext
      ))
    );
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
