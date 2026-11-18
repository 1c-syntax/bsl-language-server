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
package com.github._1c_syntax.bsl.languageserver.reporters;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Очередь записи отчётов поверх однопоточного исполнителя.
 * <p>
 * Исполнитель обязан исполнять задачи по одному и в порядке постановки: на этом держится запись
 * без синхронизации — код записи работает с состоянием (генератором, буфером), не опасаясь
 * конкурентных вызовов.
 * <p>
 * {@link #submit} не ждёт выполнения задачи: анализ только ставит запись в очередь и продолжается.
 * <p>
 * Первый сбой задачи запоминается, последующие задачи не исполняются, а исключение
 * пробрасывается из {@link #close()}. Исполнитель очередь не останавливает: он ей не принадлежит.
 */
final class ReportWriteQueue implements AutoCloseable {

  private final ExecutorService executor;
  private final AtomicReference<RuntimeException> failure = new AtomicReference<>();

  private boolean closed;

  /**
   * @param executor однопоточный исполнитель задач записи
   */
  ReportWriteQueue(ExecutorService executor) {
    this.executor = executor;
  }

  /**
   * Поставить задачу записи в очередь и сразу вернуть управление.
   *
   * @param task задача; исполняется в потоке исполнителя
   */
  void submit(Runnable task) {
    executor.execute(() -> runTask(task));
  }

  /**
   * Дождаться исполнения поставленных задач.
   *
   * @throws RuntimeException первое исключение, с которым упала задача записи
   */
  @Override
  public void close() {
    if (!closed) {
      closed = true;
      awaitQueuedTasks();
    }

    var thrown = failure.get();
    if (thrown != null) {
      throw thrown;
    }
  }

  private void runTask(Runnable task) {
    // после первого сбоя состояние отчёта уже испорчено — дописывать в него нечего
    if (failure.get() != null) {
      return;
    }

    var completed = false;
    try {
      task.run();
      completed = true;
    } catch (RuntimeException e) {
      failure.compareAndSet(null, e);
      completed = true;
    } finally {
      // задача оборвалась на Error (например, OutOfMemoryError): сама она ничего не записала,
      // и молча зафиксировать отчёт как полный нельзя
      if (!completed) {
        failure.compareAndSet(null, new IllegalStateException("Report write task terminated unexpectedly"));
      }
    }
  }

  private void awaitQueuedTasks() {
    // исполнитель однопоточный и FIFO: пустая задача завершится после всех поставленных ранее
    try {
      executor.submit(() -> {
        // барьер: ждём, пока исполнитель дойдёт до него
      }).get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      failure.compareAndSet(null, new IllegalStateException("Interrupted while waiting for report writes", e));
    } catch (ExecutionException e) {
      failure.compareAndSet(null, new IllegalStateException("Report write barrier failed", e.getCause()));
    }
  }
}
