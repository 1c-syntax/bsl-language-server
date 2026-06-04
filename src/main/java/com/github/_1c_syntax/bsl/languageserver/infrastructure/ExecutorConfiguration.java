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
package com.github._1c_syntax.bsl.languageserver.infrastructure;

import io.sentry.spring7.SentryTaskDecorator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;

/**
 * Конфигурация исполнителей для обработки асинхронных задач.
 * <p>
 * ForkJoinPool-исполнители (в которых запускаются {@code parallelStream()}) создаются
 * per-workspace — каждый воркспейс получает свой набор пулов. Worker threads
 * устанавливают workspace URI в ThreadLocal при старте ({@code onStart()}),
 * что гарантирует корректную работу workspace-scoped proxy в fork-задачах.
 * <p>
 * Исключение: {@code computeConfigurationExecutor} — singleton, т.к. вызывает
 * внешнюю библиотеку MDClasses, не использующую ThreadLocal из BSL LS.
 * <p>
 * Остальные исполнители реализуются через {@link ThreadPoolTaskExecutor} (cached thread pool)
 * с {@link TaskDecorator} для прокидывания контекста.
 */
@Configuration
public class ExecutorConfiguration {

  @Bean
  public SentryTaskDecorator sentryDecorator() {
    return new SentryTaskDecorator();
  }

  @Bean
  public ContextPropagatingTaskDecorator contextPropagatingDecorator() {
    return new ContextPropagatingTaskDecorator();
  }

  @Bean
  public TaskDecorator compositeTaskDecorator(
    SentryTaskDecorator sentryDecorator,
    TaskDecorator contextPropagatingDecorator) {
    return runnable -> sentryDecorator.decorate(contextPropagatingDecorator.decorate(runnable));
  }

  // --- ThreadPoolTaskExecutor beans (no parallelStream inside) ---

  @Bean(destroyMethod = "shutdown")
  public ThreadPoolTaskExecutor textDocumentServiceExecutor(TaskDecorator compositeTaskDecorator) {
    return createThreadPoolExecutor(compositeTaskDecorator, "text-document-service-");
  }

  @Bean(destroyMethod = "shutdown")
  public ThreadPoolTaskExecutor workspaceServiceExecutor(TaskDecorator compositeTaskDecorator) {
    return createThreadPoolExecutor(compositeTaskDecorator, "workspace-service-");
  }

  @Bean(destroyMethod = "shutdown")
  public ThreadPoolTaskExecutor sentryExecutor(TaskDecorator compositeTaskDecorator) {
    return createThreadPoolExecutor(compositeTaskDecorator, "sentry-");
  }

  // --- ForkJoinPool beans (parallelStream runs inside) ---
  // Per-workspace: worker threads set workspace URI in onStart(),
  // so parallelStream forks always have correct ThreadLocal context.

  @Bean(destroyMethod = "shutdown")
  @WorkspaceScope(proxyMode = ScopedProxyMode.INTERFACES)
  public ExecutorService populateContextExecutor() {
    return createWorkspaceForkJoinPool("populate-context-");
  }

  // computeConfigurationExecutor — singleton, вызывает MDClasses (не использует ThreadLocal BSL LS)
  @Bean(destroyMethod = "shutdown")
  public ExecutorService computeConfigurationExecutor() {
    return createSharedForkJoinExecutorService("compute-configuration-");
  }

  @Bean(destroyMethod = "shutdown")
  @WorkspaceScope(proxyMode = ScopedProxyMode.INTERFACES)
  public ExecutorService diagnosticComputerExecutor() {
    return createWorkspaceForkJoinPool("diagnostic-computer-");
  }

  @Bean(destroyMethod = "shutdown")
  @WorkspaceScope(proxyMode = ScopedProxyMode.INTERFACES)
  public ExecutorService analyzeOnStartExecutor() {
    return createWorkspaceForkJoinPool("analyze-on-start-");
  }

  @Bean(destroyMethod = "shutdown")
  @WorkspaceScope(proxyMode = ScopedProxyMode.INTERFACES)
  public ExecutorService semanticTokensExecutor() {
    return createWorkspaceForkJoinPool("semantic-tokens-");
  }

  @Bean(destroyMethod = "shutdown")
  @WorkspaceScope(proxyMode = ScopedProxyMode.INTERFACES)
  public ExecutorService cliExecutor() {
    return createWorkspaceForkJoinPool("cli-");
  }

  private static ThreadPoolTaskExecutor createThreadPoolExecutor(
    TaskDecorator taskDecorator, String threadNamePrefix) {
    var executor = new ThreadPoolTaskExecutor();
    executor.setQueueCapacity(0);
    executor.setThreadNamePrefix(threadNamePrefix);
    executor.setTaskDecorator(taskDecorator);
    executor.initialize();
    return executor;
  }

  private static ExecutorService createWorkspaceForkJoinPool(String prefix) {
    var workspaceUri = WorkspaceContextHolder.get();
    if (workspaceUri == null) {
      throw new IllegalStateException("Workspace context is not set when creating ForkJoinPool");
    }
    var workspaceName = Optional.ofNullable(WorkspaceContextHolder.getName())
      .orElse("default");
    var factory = new WorkspaceAwareFJWTFactory(workspaceUri, workspaceName, prefix);
    var pool = new ForkJoinPool(ForkJoinPool.getCommonPoolParallelism(), factory, null, true);
    return new ContextPropagatingExecutorService(pool);
  }

  private static ExecutorService createSharedForkJoinExecutorService(String threadNamePrefix) {
    var factory = new NamedForkJoinWorkerThreadFactory(threadNamePrefix);
    var pool = new ForkJoinPool(ForkJoinPool.getCommonPoolParallelism(), factory, null, true);
    return new ContextPropagatingExecutorService(pool);
  }

  private record NamedForkJoinWorkerThreadFactory(String prefix) implements ForkJoinPool.ForkJoinWorkerThreadFactory {
    @Override
    public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
      var thread = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
      thread.setName(prefix + thread.getPoolIndex());
      return thread;
    }
  }

  private record WorkspaceAwareFJWTFactory(
    URI workspaceUri,
    String workspaceName,
    String prefix
  ) implements ForkJoinPool.ForkJoinWorkerThreadFactory {
    @Override
    public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
      var thread = new ForkJoinWorkerThread(pool) {
        @Override
        protected void onStart() {
          WorkspaceContextHolder.set(workspaceUri, workspaceName);
          super.onStart();
        }
      };
      thread.setName(prefix + workspaceName + "-" + thread.getPoolIndex());
      return thread;
    }
  }
}
