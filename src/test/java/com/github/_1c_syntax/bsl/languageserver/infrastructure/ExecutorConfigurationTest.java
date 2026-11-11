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

import com.github._1c_syntax.utils.Absolute;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutorConfigurationTest {

  private static final URI WORKSPACE_URI = Absolute.uri(URI.create("file:///workspace"));
  private static final String WORKSPACE_NAME = "workspace";

  /**
   * Число воркеров, которые тест заставляет одновременно работать. Не зависит от числа ядер:
   * пул создаётся напрямую, а не через бины с {@code getCommonPoolParallelism()}.
   */
  private static final int PARALLELISM = 4;
  private static final int TIMEOUT_SECONDS = 30;

  @AfterEach
  void tearDown() {
    WorkspaceContextHolder.clear();
  }

  @Test
  void namedFactory_givesEachWorkerItsOwnName() throws Exception {
    var factory = new ExecutorConfiguration.NamedForkJoinWorkerThreadFactory("compute-configuration-");

    var names = namesOfConcurrentWorkers(factory);

    assertThat(names).hasSize(PARALLELISM);
    assertThat(names.values()).doesNotHaveDuplicates();
    assertThat(names.values()).allMatch(name -> name.matches("compute-configuration-\\d+"));
  }

  @Test
  void workspaceAwareFactory_givesEachWorkerItsOwnName() throws Exception {
    var factory = new ExecutorConfiguration.WorkspaceAwareFJWTFactory(
      WORKSPACE_URI, WORKSPACE_NAME, "populate-context-"
    );

    var names = namesOfConcurrentWorkers(factory);

    assertThat(names).hasSize(PARALLELISM);
    assertThat(names.values()).doesNotHaveDuplicates();
    assertThat(names.values()).allMatch(name -> name.matches("populate-context-workspace-\\d+"));
  }

  @Test
  void workspaceAwareFactory_setsWorkspaceContextInWorker() throws Exception {
    var factory = new ExecutorConfiguration.WorkspaceAwareFJWTFactory(
      WORKSPACE_URI, WORKSPACE_NAME, "populate-context-"
    );
    var pool = new ForkJoinPool(PARALLELISM, factory, null, true);

    try {
      var workspaceUri = pool.submit(WorkspaceContextHolder::get).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

      assertThat(workspaceUri).isEqualTo(WORKSPACE_URI);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void populateContextExecutor_namesWorkersAfterWorkspace() throws Exception {
    WorkspaceContextHolder.set(WORKSPACE_URI, WORKSPACE_NAME);
    var executor = new ExecutorConfiguration().populateContextExecutor();

    try {
      var name = currentThreadName(executor);

      assertThat(name).matches("populate-context-workspace-\\d+");
    } finally {
      executor.shutdown();
    }
  }

  @Test
  void computeConfigurationExecutor_namesWorkersAfterPrefix() throws Exception {
    var executor = new ExecutorConfiguration().computeConfigurationExecutor();

    try {
      var name = currentThreadName(executor);

      assertThat(name).matches("compute-configuration-\\d+");
    } finally {
      executor.shutdown();
    }
  }

  private static String currentThreadName(ExecutorService executor) throws Exception {
    return executor.submit(() -> Thread.currentThread().getName())
      .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
  }

  /**
   * Поднимает в пуле ровно {@link #PARALLELISM} воркеров и возвращает их имена.
   * <p>
   * Задачи расходятся только после того, как в них зайдут все воркеры, — значит имена
   * собраны с потоков, живущих одновременно, и обязаны различаться.
   */
  private static Map<Thread, String> namesOfConcurrentWorkers(
    ForkJoinPool.ForkJoinWorkerThreadFactory factory
  ) throws Exception {
    var pool = new ForkJoinPool(PARALLELISM, factory, null, true);
    var names = new ConcurrentHashMap<Thread, String>();
    var allWorkersStarted = new CountDownLatch(PARALLELISM);

    List<Callable<Void>> tasks = IntStream.range(0, PARALLELISM)
      .<Callable<Void>>mapToObj(i -> () -> {
        allWorkersStarted.countDown();
        assertThat(allWorkersStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        var thread = Thread.currentThread();
        names.put(thread, thread.getName());
        return null;
      })
      .toList();

    try {
      for (var future : pool.invokeAll(tasks)) {
        future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
      }
    } finally {
      pool.shutdownNow();
    }

    return names;
  }
}
