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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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
 * пробрасывается из {@link #close()}. Задачи, поставленные после закрытия, тоже не исполняются.
 * Исполнитель очередь не останавливает: он ей не принадлежит.
 */
final class ReportWriteQueue implements AutoCloseable {

  private final ExecutorService executor;
  private final AtomicReference<RuntimeException> failure = new AtomicReference<>();

  // выставляется барьером закрытия в потоке исполнителя: всё, что стоит в очереди после него,
  // поставлено уже после закрытия
  private volatile boolean drained;

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
   * Дождаться исполнения поставленных задач. Прерывание не обрывает ожидание идущей записи:
   * флаг прерывания восстанавливается после неё, а закрытие завершается исключением.
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
    // после закрытия отчёты уже завершены, а после первого сбоя состояние отчёта испорчено —
    // в обоих случаях писать некуда
    if (drained || failure.get() != null) {
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
    // завершённый барьер не проверяет прерывание: учитываем его заранее, чтобы исход закрытия
    // не зависел от того, успел ли барьер исполниться
    var interrupted = Thread.interrupted();

    // исполнитель однопоточный и FIFO: барьер исполнится после всех задач, поставленных до него.
    // join() не прерывается: идущая запись не должна пересечься с завершением отчёта.
    // Прерывание во время ожидания он сохраняет во флаге потока
    try {
      CompletableFuture.runAsync(() -> drained = true, executor).join();
    } catch (CompletionException e) {
      failure.compareAndSet(null, new IllegalStateException("Report write barrier failed", e));
    }

    if (interrupted || Thread.interrupted()) {
      Thread.currentThread().interrupt();
      failure.compareAndSet(null, new IllegalStateException("Interrupted while waiting for report writes"));
    }
  }
}
