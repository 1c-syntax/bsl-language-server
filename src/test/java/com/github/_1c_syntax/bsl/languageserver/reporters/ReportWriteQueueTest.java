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

import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportWriteQueueTest {

  /**
   * Ключевое свойство: задачи исполняет ровно один поток. На нём держится весь контракт —
   * репортёры пишут в свои генераторы без синхронизации.
   */
  @Test
  void executesAllTasksOnSingleThread() {
    Set<String> threads = ConcurrentHashMap.newKeySet();
    var executed = new AtomicInteger();

    try (var queue = new ReportWriteQueue("test")) {
      IntStream.range(0, 200).parallel().forEach(i -> queue.submit(() -> {
        threads.add(Thread.currentThread().getName());
        executed.incrementAndGet();
      }));
    }

    assertThat(threads).hasSize(1);
    assertThat(executed).hasValue(200);
  }

  @Test
  void closeWaitsForQueuedTasks() {
    var executed = new AtomicInteger();

    try (var queue = new ReportWriteQueue("test")) {
      for (var i = 0; i < 50; i++) {
        queue.submit(executed::incrementAndGet);
      }
    }

    assertThat(executed).hasValue(50);
  }

  /**
   * Анализ не должен ждать записи: submit возвращает управление, не дожидаясь задачи.
   */
  @Test
  void submitDoesNotWaitForTaskToRun() throws Exception {
    var release = new CountDownLatch(1);
    var taskStarted = new CountDownLatch(1);
    var submitReturned = new CountDownLatch(1);

    try (var queue = new ReportWriteQueue("test")) {
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
   * Задача видит workspace-контекст того потока, который её поставил, — иначе workspace-scoped
   * бины (например, {@code DiagnosticInfos}) в потоке записи не резолвятся. Контекст снимается
   * на каждую задачу отдельно: поток очереди общий, а постановщики могут быть из разных workspace.
   */
  @Test
  void propagatesWorkspaceContextOfSubmitterToTask() throws InterruptedException {
    var firstWorkspace = URI.create("file:///report-write-queue/first/");
    var secondWorkspace = URI.create("file:///report-write-queue/second/");
    List<@Nullable URI> observed = Collections.synchronizedList(new ArrayList<>());
    Runnable recordWorkspace = () -> observed.add(WorkspaceContextHolder.get());

    WorkspaceContextHolder.registerWorkspace(firstWorkspace, "first");
    WorkspaceContextHolder.registerWorkspace(secondWorkspace, "second");
    try (var queue = new ReportWriteQueue("test")) {
      // свежий поток: контекст потока теста мог остаться от предыдущих тестов этого форка
      var producer = new Thread(() -> {
        WorkspaceContextHolder.run(firstWorkspace, () -> queue.submit(recordWorkspace));
        WorkspaceContextHolder.run(secondWorkspace, () -> queue.submit(recordWorkspace));
        queue.submit(recordWorkspace);
      });
      producer.start();
      producer.join();
    } finally {
      WorkspaceContextHolder.unregisterWorkspace(firstWorkspace);
      WorkspaceContextHolder.unregisterWorkspace(secondWorkspace);
    }

    assertThat(observed).containsExactly(firstWorkspace, secondWorkspace, null);
  }

  @Test
  void closeRethrowsFirstTaskFailure() {
    var queue = new ReportWriteQueue("test");
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

    // очередь однопоточная и FIFO: первая задача успевает упасть до разбора второй
    var queue = new ReportWriteQueue("test");
    queue.submit(() -> {
      throw new IllegalStateException("запись сломалась");
    });
    queue.submit(afterFailure::incrementAndGet);

    assertThatThrownBy(queue::close).isInstanceOf(IllegalStateException.class);
    assertThat(afterFailure).hasValue(0);
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
