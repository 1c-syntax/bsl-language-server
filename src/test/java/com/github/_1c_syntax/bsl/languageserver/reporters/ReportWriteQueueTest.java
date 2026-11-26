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

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportWriteQueueTest {

  private final ExecutorService executor = Executors.newSingleThreadExecutor();

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
  }

  @Test
  void closeWaitsForQueuedTasks() {
    var executed = new AtomicInteger();

    try (var queue = new ReportWriteQueue(executor)) {
      IntStream.range(0, 200).parallel().forEach(i -> queue.submit(executed::incrementAndGet));
    }

    assertThat(executed).hasValue(200);
  }

  /**
   * Анализ не должен ждать записи: submit возвращает управление, не дожидаясь задачи.
   */
  @Test
  void submitDoesNotWaitForTaskToRun() throws Exception {
    var release = new CountDownLatch(1);
    var taskStarted = new CountDownLatch(1);
    var submitReturned = new CountDownLatch(1);

    try (var queue = new ReportWriteQueue(executor)) {
      var producer = new Thread(() -> {
        queue.submit(() -> {
          taskStarted.countDown();
          await(release);
        });
        submitReturned.countDown();
      });
      producer.start();

      assertThat(taskStarted.await(5, TimeUnit.SECONDS))
        .as("задача должна начать выполняться")
        .isTrue();

      // задача заведомо не завершена: release ещё не отпущен. Синхронный submit здесь бы завис
      assertThat(submitReturned.await(5, TimeUnit.SECONDS))
        .as("submit не должен ждать выполнения задачи")
        .isTrue();

      release.countDown();
      producer.join();
    }
  }

  /**
   * Прерванный анализ может прислать результат уже после закрытия очереди, когда отчёты
   * завершены или прерваны: такой результат писать некуда.
   */
  @Test
  void skipsTasksSubmittedAfterClose() throws Exception {
    var executed = new AtomicInteger();
    var queue = new ReportWriteQueue(executor);
    queue.close();

    queue.submit(executed::incrementAndGet);
    // исполнитель однопоточный и FIFO: к завершению пробы поздняя задача уже разобрана
    executor.submit(() -> {
      // проба
    }).get(5, TimeUnit.SECONDS);

    assertThat(executed).hasValue(0);
  }

  /**
   * Прерванное закрытие всё равно дожидается идущей записи: иначе отчёт завершится или
   * прервётся одновременно с записью в него.
   */
  @Test
  void interruptedCloseWaitsForRunningTask() throws Exception {
    var taskStarted = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var taskFinished = new AtomicBoolean();
    var finishedWhenClosed = new AtomicBoolean();
    var interruptRestored = new AtomicBoolean();

    var queue = new ReportWriteQueue(executor);
    queue.submit(() -> {
      taskStarted.countDown();
      await(release);
      taskFinished.set(true);
    });
    assertThat(taskStarted.await(5, TimeUnit.SECONDS)).isTrue();

    var closer = new Thread(() -> {
      Thread.currentThread().interrupt();
      assertThatThrownBy(queue::close).isInstanceOf(IllegalStateException.class);
      finishedWhenClosed.set(taskFinished.get());
      interruptRestored.set(Thread.currentThread().isInterrupted());
    });
    // зависший при регрессии поток не должен держать JVM тестов
    closer.setDaemon(true);
    closer.start();

    try {
      // запись ещё не отпущена: закрытие обязано ждать
      closer.join(500);
      assertThat(closer.isAlive()).as("close() должен дождаться идущей записи").isTrue();
    } finally {
      release.countDown();
    }

    closer.join(TimeUnit.SECONDS.toMillis(5));
    assertThat(closer.isAlive()).as("close() должен завершиться после записи").isFalse();
    assertThat(finishedWhenClosed).isTrue();
    assertThat(interruptRestored).isTrue();
  }

  /**
   * Прерывание, пришедшее уже во время ожидания идущей записи, тоже не обрывает ожидание
   * и завершает закрытие исключением.
   */
  @Test
  void interruptDuringCloseWaitIsRecorded() throws Exception {
    var taskStarted = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var taskFinished = new AtomicBoolean();
    var finishedWhenClosed = new AtomicBoolean();
    var interruptRestored = new AtomicBoolean();
    var closeFailed = new AtomicBoolean();

    var queue = new ReportWriteQueue(executor);
    queue.submit(() -> {
      taskStarted.countDown();
      await(release);
      taskFinished.set(true);
    });
    assertThat(taskStarted.await(5, TimeUnit.SECONDS)).isTrue();

    var closer = new Thread(() -> {
      try {
        queue.close();
      } catch (IllegalStateException e) {
        closeFailed.set(true);
      }
      finishedWhenClosed.set(taskFinished.get());
      interruptRestored.set(Thread.currentThread().isInterrupted());
    });
    closer.setDaemon(true);
    closer.start();

    try {
      awaitWaiting(closer);
      closer.interrupt();

      // запись ещё не отпущена: прерванное закрытие обязано ждать дальше
      closer.join(500);
      assertThat(closer.isAlive()).as("close() должен дождаться идущей записи").isTrue();
    } finally {
      release.countDown();
    }

    closer.join(TimeUnit.SECONDS.toMillis(5));
    assertThat(closer.isAlive()).as("close() должен завершиться после записи").isFalse();
    assertThat(closeFailed).isTrue();
    assertThat(finishedWhenClosed).isTrue();
    assertThat(interruptRestored).isTrue();
  }

  /**
   * Исход прерванного закрытия не зависит от того, успели ли записи завершиться к моменту
   * ожидания: закрытие всё равно завершается исключением и сохраняет флаг прерывания.
   */
  @Test
  void interruptedCloseFailsEvenWhenWritesAreAlreadyDone() {
    // задачи исполняются прямо при постановке: к ожиданию барьер уже завершён
    var queue = new ReportWriteQueue(new CallerThreadExecutor());

    Thread.currentThread().interrupt();
    try {
      assertThatThrownBy(queue::close).isInstanceOf(IllegalStateException.class);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      // флаг прерывания не должен пережить тест
      Thread.interrupted();
    }
  }

  @Test
  void closeRethrowsFirstTaskFailure() {
    var queue = new ReportWriteQueue(executor);
    queue.submit(() -> {
      throw new IllegalStateException("запись сломалась");
    });

    assertThatThrownBy(queue::close)
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("запись сломалась");
  }

  @Test
  void skipsTasksQueuedAfterFailure() {
    var afterFailure = new AtomicInteger();

    // исполнитель однопоточный и FIFO: первая задача успевает упасть до разбора второй
    var queue = new ReportWriteQueue(executor);
    queue.submit(() -> {
      throw new IllegalStateException("запись сломалась");
    });
    queue.submit(afterFailure::incrementAndGet);

    assertThatThrownBy(queue::close).isInstanceOf(IllegalStateException.class);
    assertThat(afterFailure).hasValue(0);
  }

  /**
   * Исполнитель, выполняющий задачу в потоке, который её поставил.
   */
  private static final class CallerThreadExecutor extends AbstractExecutorService {

    private volatile boolean shutdown;

    @Override
    public void execute(Runnable command) {
      command.run();
    }

    @Override
    public void shutdown() {
      shutdown = true;
    }

    @Override
    public List<Runnable> shutdownNow() {
      shutdown = true;
      return List.of();
    }

    @Override
    public boolean isShutdown() {
      return shutdown;
    }

    @Override
    public boolean isTerminated() {
      // задачи исполняются сразу при постановке: после остановки исполнять нечего
      return isShutdown();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
      return true;
    }
  }

  /**
   * Дождаться, пока поток встанет на ожидание (закрытие ждёт барьер).
   */
  private static void awaitWaiting(Thread thread) {
    Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> thread.getState() == Thread.State.WAITING);
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
