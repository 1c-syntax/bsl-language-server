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
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;

/**
 * Конфигурация исполнителей для обработки асинхронных задач.
 * <p>
 * Исполнители, в которых запускаются {@code parallelStream()}, оборачиваются в {@link ForkJoinPool},
 * чтобы параллельные потоки работали в выделенном пуле, а не в {@code ForkJoinPool.commonPool()}.
 * Внешняя задача оборачивается через {@link ContextPropagatingExecutorService}
 * для прокидывания Sentry/MDC контекста из вызывающего потока.
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
    ContextPropagatingTaskDecorator contextPropagatingDecorator) {
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
  // Wrapped in ContextPropagatingExecutorService for Sentry/MDC propagation
  // into the outer submitted task. Inner parallelStream forks inherit the FJP
  // but not the ThreadLocal context.

  @Bean(destroyMethod = "shutdown")
  public ExecutorService populateContextExecutor() {
    return createForkJoinExecutorService("populate-context-");
  }

  @Bean(destroyMethod = "shutdown")
  public ExecutorService computeConfigurationExecutor() {
    return createForkJoinExecutorService("compute-configuration-");
  }

  @Bean(destroyMethod = "shutdown")
  public ExecutorService diagnosticComputerExecutor() {
    return createForkJoinExecutorService("diagnostic-computer-");
  }

  @Bean(destroyMethod = "shutdown")
  public ExecutorService analyzeOnStartExecutor() {
    return createForkJoinExecutorService("analyze-on-start-");
  }

  @Bean(destroyMethod = "shutdown")
  public ExecutorService semanticTokensExecutor() {
    return createForkJoinExecutorService("semantic-tokens-");
  }

  private ThreadPoolTaskExecutor createThreadPoolExecutor(
    TaskDecorator taskDecorator, String threadNamePrefix) {
    var executor = new ThreadPoolTaskExecutor();
    executor.setQueueCapacity(0);
    executor.setThreadNamePrefix(threadNamePrefix);
    executor.setTaskDecorator(taskDecorator);
    executor.initialize();
    return executor;
  }

  private ExecutorService createForkJoinExecutorService(String threadNamePrefix) {
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
}
