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

import com.github._1c_syntax.bsl.languageserver.utils.NamedForkJoinWorkerThreadFactory;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Role;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Общие исполнители для LSP/CLI пайплайна.
 * <p>
 * Содержит два общих пула, переиспользуемых всеми компонентами:
 * <ul>
 *   <li>{@link #getCpuExecutor()} — CPU-bound задачи (диагностики, обходы AST,
 *       семантические токены). Размер ≈ числу ядер.</li>
 *   <li>{@link #getLspExecutor()} — обработка запросов LSP. Ограничен по числу
 *       потоков.</li>
 * </ul>
 */
@Slf4j
@Component
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
public class BslLsExecutors {

  /**
   * Верхняя граница очереди ожидающих LSP-задач. При переполнении применяется
   * {@link ThreadPoolExecutor.CallerRunsPolicy} — задача выполняется на потоке
   * вызывающего, что естественным образом создаёт backpressure и не даёт
   * памяти расти бесконтрольно при всплесках запросов.
   */
  private static final int LSP_QUEUE_CAPACITY = 1024;

  /** Минимальное число потоков CPU-пула (даже на одноядерных системах). */
  private static final int MIN_CPU_THREADS = 2;

  /** Минимальное число потоков пула LSP-запросов. */
  private static final int MIN_LSP_THREADS = 4;

  /** Время простоя потоков пула до выгрузки. */
  private static final long IDLE_KEEP_ALIVE_SECONDS = 60L;

  /**
   * CPU-bound пул для параллельных вычислений анализа.
   *
   * @return общий CPU-пул
   */
  @Getter
  private final ForkJoinPool cpuExecutor;

  /**
   * Ограниченный по числу потоков пул для обработки запросов LSP.
   *
   * @return пул LSP-запросов
   */
  @Getter
  private final ExecutorService lspExecutor;

  public BslLsExecutors() {
    var cores = Math.max(MIN_CPU_THREADS, Runtime.getRuntime().availableProcessors());

    this.cpuExecutor = new ForkJoinPool(
      cores,
      new NamedForkJoinWorkerThreadFactory("bsl-cpu-"),
      null,
      true
    );

    var lspThreads = Math.max(MIN_LSP_THREADS, cores);
    var pool = new ThreadPoolExecutor(
      lspThreads,
      lspThreads,
      IDLE_KEEP_ALIVE_SECONDS,
      TimeUnit.SECONDS,
      new LinkedBlockingQueue<>(LSP_QUEUE_CAPACITY),
      new CustomizableThreadFactory("bsl-lsp-"),
      new ThreadPoolExecutor.CallerRunsPolicy()
    );
    pool.allowCoreThreadTimeOut(true);
    this.lspExecutor = pool;

    LOGGER.debug("BslLsExecutors initialized: cpu={} lsp={}", cores, lspThreads);
  }

  /**
   * Проверка, что текущий поток принадлежит CPU-пулу.
   * <p>
   * Позволяет вложенным вызовам выполнять работу прямо на текущем потоке без
   * дополнительного {@code submit/join}.
   *
   * @return {@code true}, если поток входит в CPU-пул
   */
  public boolean isInCpuPool() {
    return Thread.currentThread() instanceof ForkJoinWorkerThread fj
      && fj.getPool() == cpuExecutor;
  }

  /**
   * Корректное завершение пулов при остановке Spring-контекста.
   * Вызывается фреймворком через {@link PreDestroy}.
   */
  @PreDestroy
  void shutdown() {
    cpuExecutor.shutdown();
    lspExecutor.shutdown();
  }
}
