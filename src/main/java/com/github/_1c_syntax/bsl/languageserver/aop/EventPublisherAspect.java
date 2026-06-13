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
import com.github._1c_syntax.bsl.languageserver.context.events.ConfigurationTypesRegisteredEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextContentChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentAddedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentClearedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentClosedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentRemovedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextPopulatedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.WorkspaceAddedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.WorkspaceRemovedEvent;
import com.github._1c_syntax.bsl.languageserver.events.LanguageServerInitializeRequestReceivedEvent;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndexedEvent;
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
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

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
   * Используется для рассылки событий <em>во все</em> живые контексты — каждый контекст
   * получает событие в своих собственных listener'ах и обновляет свои workspace-scoped бины.
   * Контексты, в которых соответствующий workspace не зарегистрирован, просто обработают
   * событие в своих singleton-листенерах, но workspace-scoped бины при этом не затронут
   * чужое состояние (каждый контекст имеет собственную область видимости).
   * <p>
   * Синхронизация чтения/записи — через {@code synchronized}-методы register/unregister
   * и волатильную пере-публикацию ссылки на массив-снимок в {@link #snapshot}.
   */
  private final Set<ApplicationContext> registeredContexts = new LinkedHashSet<>();

  /**
   * Иммутабельный снимок зарегистрированных контекстов для безлоковой рассылки событий.
   * Перестраивается при каждом register/unregister.
   */
  private final AtomicReference<ApplicationContext[]> snapshot =
    new AtomicReference<>(new ApplicationContext[0]);

  /**
   * Регистрирует Spring-контекст как источник событий.
   * Вызывается из {@link EventPublisherAspectRegistration} при инициализации каждого контекста.
   *
   * @param ctx регистрируемый контекст
   */
  synchronized void register(ApplicationContext ctx) {
    registeredContexts.add(ctx);
    snapshot.set(registeredContexts.toArray(new ApplicationContext[0]));
  }

  /**
   * Отменяет регистрацию Spring-контекста.
   * Вызывается из {@link EventPublisherAspectRegistration} при уничтожении каждого контекста.
   *
   * @param ctx контекст, регистрацию которого необходимо отменить
   */
  synchronized void unregister(ApplicationContext ctx) {
    registeredContexts.remove(ctx);
    snapshot.set(registeredContexts.toArray(new ApplicationContext[0]));
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

  @AfterReturning(
    pointcut = "Pointcuts.isConfigurationTypesProvider() && Pointcuts.isTryRegisterCall()",
    returning = "serverContext"
  )
  public void configurationTypesRegistered(JoinPoint joinPoint, @Nullable ServerContext serverContext) {
    if (serverContext != null) {
      // tryRegister отдаёт non-null только при реальной регистрации, отсев no-op
      publishEvent(new ConfigurationTypesRegisteredEvent(serverContext));
    }
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

  @AfterReturning(
    pointcut = "Pointcuts.isServerContext() && Pointcuts.isTryClearDocumentCall() && args(documentContext)",
    returning = "cleared"
  )
  public void serverContextTryClearDocument(JoinPoint joinPoint, DocumentContext documentContext, boolean cleared) {
    // Публикуем только при реальной очистке: на открытом документе tryClearDocument
    // делает no-op (cleared == false) и сбрасывать кэши не нужно.
    if (cleared) {
      publishEvent(new ServerContextDocumentClearedEvent((ServerContext) joinPoint.getThis(), documentContext));
    }
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

  @AfterReturning(
    pointcut = "Pointcuts.isOScriptLibraryIndex() && Pointcuts.isReindexCall() && args(serverContext)",
    returning = "configs"
  )
  public void oscriptLibraryIndexed(JoinPoint joinPoint, ServerContext serverContext, List<Path> configs) {
    publishEvent(new OScriptLibraryIndexedEvent(
      (OScriptLibraryIndex) joinPoint.getThis(),
      serverContext,
      configs
    ));
  }

  private void publishEvent(Object event) {
    var contexts = snapshot.get();
    if (contexts.length == 0) {
      LOGGER.warn("Trying to send event in not active event publisher.");
      return;
    }
    // Если установлен workspace-контекст, событие принадлежит конкретному
    // workspace и должно идти только в Spring-контекст-владельца этого workspace.
    // В тестах это критично: несколько Spring-контекстов с разными @SpringBootTest-
    // конфигурациями висят в TestContext-кэше и зарегистрированы в JVM-singleton
    // аспекте. Если рассылать во ВСЕ, listener'ы non-owning контекстов создают
    // workspace-scoped beans под текущий WSCH-URI и затрагивают чужое состояние —
    // в боевом сценарии это маловероятно (один LS = один контекст), но в тестах
    // ломает соседние тест-классы. Когда WSCH не установлен (глобальные события
    // вроде {@link GlobalLanguageServerConfigurationChangedEvent} или Initialize),
    // рассылаем во все — у них нет workspace-привязки.
    var owner = findOwningContext(contexts, event);
    if (owner != null) {
      try {
        owner.publishEvent(event);
      } catch (RuntimeException e) {
        LOGGER.warn("Failed to publish event {} to owning context {}: {}", event, owner, e.toString());
      }
      return;
    }
    for (var ctx : contexts) {
      try {
        ctx.publishEvent(event);
      } catch (RuntimeException e) {
        LOGGER.warn("Failed to publish event {} to context {}: {}", event, ctx, e.toString());
      }
    }
  }

  /**
   * Найти Spring-контекст, чей {@link ServerContextProvider} владеет источником
   * события. Сначала пробуем матч по identity {@link ServerContext} (источник
   * для большинства workspace-событий) — это надёжнее, чем по URI, потому что
   * разные Spring-контексты могут зарегистрировать workspace с одним и тем же
   * URI. Фолбэк — по URI из WorkspaceContextHolder. Возвращает {@code null},
   * если владелец не определяется (тогда вызывающий рассылает во все контексты).
   */
  private static @Nullable ApplicationContext findOwningContext(
    ApplicationContext[] contexts, Object event
  ) {
    var serverContext = extractServerContext(event);
    if (serverContext != null) {
      for (var ctx : contexts) {
        try {
          var provider = ctx.getBean(ServerContextProvider.class);
          if (provider.getAllContexts().containsValue(serverContext)) {
            return ctx;
          }
        } catch (RuntimeException ignored) {
          // Бин может быть недоступен (контекст закрывается) — пропускаем.
        }
      }
      // Если identity-матч не сработал (workspace уже удалён, контекст в процессе закрытия),
      // не делаем фолбэка по URI: лучше отправить во все контексты (выше по стеку), чем
      // случайно попасть в чужой контекст с тем же URI.
      return null;
    }
    var workspaceUri = WorkspaceContextHolder.get();
    if (workspaceUri == null) {
      return null;
    }
    for (var ctx : contexts) {
      try {
        var provider = ctx.getBean(ServerContextProvider.class);
        if (provider.getAllContexts().containsKey(workspaceUri)) {
          return ctx;
        }
      } catch (RuntimeException ignored) {
        // Бин может быть недоступен.
      }
    }
    return null;
  }

  /**
   * Достать {@link ServerContext} из источника события, чтобы прокидывать
   * routing по identity. {@code null} — у события нет привязки к конкретному
   * ServerContext (глобальные события вроде Initialize, LSC).
   */
  private static @Nullable ServerContext extractServerContext(Object event) {
    if (event instanceof ConfigurationTypesRegisteredEvent ctr) {
      return ctr.serverContext();
    }
    if (!(event instanceof ApplicationEvent appEvent)) {
      return null;
    }
    // События с source = ServerContext (DocumentAdded/Removed/Closed, Populated):
    // ловим через instanceof ниже.
    var src = appEvent.getSource();
    if (src instanceof DocumentContext documentContext) {
      return documentContext.getServerContext();
    }
    if (src instanceof ServerContext serverContext) {
      return serverContext;
    }
    // События workspace-жизненного цикла: source = ServerContextProvider, но они
    // несут ServerContext в отдельном поле.
    if (event instanceof WorkspaceAddedEvent addedWs) {
      return addedWs.getServerContext();
    }
    if (event instanceof BeforeWorkspaceRemovedEvent beforeRemoved) {
      return beforeRemoved.getServerContext();
    }
    if (event instanceof OScriptLibraryIndexedEvent indexed) {
      return indexed.getServerContext();
    }
    return null;
  }
}
