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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Однопоточная очередь записи отчётов.
 * <p>
 * Все переданные задачи исполняет один и тот же поток, поэтому код записи может работать с
 * состоянием (генератором, буфером) без синхронизации: конкурентных вызовов не бывает.
 * <p>
 * {@link #submit} не ждёт ни выполнения задачи, ни освобождения места: анализ только ставит
 * запись в очередь и продолжается. Плата за это — очередь не ограничена. Пока запись успевает
 * за разбором (обычный случай: сериализация результата на порядок дешевле разбора модуля),
 * в очереди почти пусто. Если же запись устойчиво медленнее разбора — много активных форматов
 * на машине с большим числом ядер, — очередь будет расти, и выигрыш по памяти сойдёт на нет.
 * <p>
 * Первый сбой задачи запоминается, последующие задачи не исполняются, а исключение
 * пробрасывается из {@link #close()}.
 */
final class ReportWriteQueue implements AutoCloseable {

  private static final Runnable POISON = () -> {
    // маркер завершения: до исполнения не доходит
  };

  private final BlockingQueue<Runnable> queue;
  private final Thread worker;
  private final AtomicReference<Throwable> failure = new AtomicReference<>();

  private volatile boolean stoppedNormally;

  private boolean closed;

  /**
   * @param name имя очереди, попадает в имя потока
   */
  ReportWriteQueue(String name) {
    this.queue = new LinkedBlockingQueue<>();
    this.worker = new Thread(this::processQueue, "report-write-" + name);
    this.worker.setDaemon(true);
    this.worker.start();
  }

  /**
   * Поставить задачу записи в очередь и сразу вернуть управление.
   *
   * @param task задача; исполняется в потоке очереди
   */
  void submit(Runnable task) {
    put(task);
  }

  /**
   * Дождаться исполнения поставленных задач и остановить поток очереди.
   *
   * @throws RuntimeException первое исключение, с которым упала задача записи
   */
  @Override
  public void close() {
    if (!closed) {
      closed = true;
      put(POISON);
      try {
        worker.join();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    var thrown = failure.get();
    if (thrown instanceof RuntimeException runtimeException) {
      throw runtimeException;
    }

    // поток мог умереть на Error (например, OutOfMemoryError): сам он ничего не записал,
    // и молча зафиксировать отчёт как полный нельзя
    if (!stoppedNormally) {
      throw new IllegalStateException("Report write thread terminated unexpectedly");
    }
  }

  private void processQueue() {
    while (true) {
      Runnable task;
      try {
        task = queue.take();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }

      if (task == POISON) {
        stoppedNormally = true;
        return;
      }

      // после первого сбоя состояние отчёта уже испорчено — дописывать в него нечего
      if (failure.get() == null) {
        runTask(task);
      }
    }
  }

  private void runTask(Runnable task) {
    try {
      task.run();
    } catch (RuntimeException e) {
      failure.compareAndSet(null, e);
    }
  }

  private void put(Runnable task) {
    try {
      queue.put(task);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while queueing report write", e);
    }
  }
}
