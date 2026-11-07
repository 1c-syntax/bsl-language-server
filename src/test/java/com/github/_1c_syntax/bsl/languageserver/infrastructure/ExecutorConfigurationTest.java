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
import io.micrometer.context.ContextRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ExecutorConfigurationTest {

  private static final URI WORKSPACE_URI = Absolute.uri(URI.create("file:///workspace"));
  private static final String WORKSPACE_NAME = "workspace";

  /**
   * Число воркеров, которые тест заставляет одновременно работать. Не зависит от числа ядер:
   * пул создаётся напрямую, а не через бины с {@code getCommonPoolParallelism()}.
   */
  private static final int PARALLELISM = 4;
  private static final int TIMEOUT_SECONDS = 30;

  @BeforeEach
  void registerWorkspaceForContextPropagation() {
    ContextRegistry.getInstance().loadThreadLocalAccessors();
    WorkspaceContextHolder.registerWorkspace(WORKSPACE_URI, WORKSPACE_NAME);
  }

  @AfterEach
  void tearDown() {
    WorkspaceContextHolder.unregisterWorkspace(WORKSPACE_URI);
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
  void diagnosticExecutorIsBoundedAndPropagatesWorkspace() throws Exception {
    WorkspaceContextHolder.set(WORKSPACE_URI, WORKSPACE_NAME);
    var executor = new ExecutorConfiguration().diagnosticComputerExecutor();
    var pool = (ThreadPoolExecutor) ReflectionTestUtils.getField(executor, "delegate");
    try {
      assertThat(pool.getMaximumPoolSize()).isEqualTo(pool.getCorePoolSize());
      var expected = System.getProperty("java.util.concurrent.ForkJoinPool.common.parallelism") == null
        ? Runtime.getRuntime().availableProcessors() + 1 : ForkJoinPool.getCommonPoolParallelism();
      assertThat(pool.getCorePoolSize()).isEqualTo(expected);
      assertThat(executor.submit(WorkspaceContextHolder::get).get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        .isEqualTo(WORKSPACE_URI);
      assertThat(currentThreadName(executor)).matches("diagnostic-computer-workspace-\\d+");
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void diagnosticExecutorPreservesExplicitForkJoinParallelism() {
    var property = "java.util.concurrent.ForkJoinPool.common.parallelism";
    var previous = System.getProperty(property);
    System.setProperty(property, Integer.toString(ForkJoinPool.getCommonPoolParallelism()));
    WorkspaceContextHolder.set(WORKSPACE_URI, WORKSPACE_NAME);
    ExecutorService executor = null;
    try {
      executor = new ExecutorConfiguration().diagnosticComputerExecutor();
      var pool = (ThreadPoolExecutor) ReflectionTestUtils.getField(executor, "delegate");
      assertThat(pool.getCorePoolSize()).isEqualTo(ForkJoinPool.getCommonPoolParallelism());
      assertThat(pool.getMaximumPoolSize()).isEqualTo(pool.getCorePoolSize());
    } finally {
      if (executor != null) executor.shutdownNow();
      if (previous == null) System.clearProperty(property); else System.setProperty(property, previous);
    }
  }

  @Test
  void diagnosticFutureDoesNotCompensateTheCallingForkJoinWorker() throws Exception {
    WorkspaceContextHolder.set(WORKSPACE_URI, WORKSPACE_NAME);
    var executor = new ExecutorConfiguration().diagnosticComputerExecutor();
    var caller = new ForkJoinPool(1);
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var callerThread = new AtomicReference<Thread>();
    try {
      var outer = caller.submit(() -> {
        WorkspaceContextHolder.set(WORKSPACE_URI, WORKSPACE_NAME);
        callerThread.set(Thread.currentThread());
        try {
          return executor.submit(() -> {
            entered.countDown();
            assertThat(release.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            return WorkspaceContextHolder.get();
          }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } finally {
          WorkspaceContextHolder.clear();
        }
      });
      assertThat(entered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
      await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(() ->
        callerThread.get().getState() == Thread.State.WAITING
          || callerThread.get().getState() == Thread.State.TIMED_WAITING);
      assertThat(caller.getPoolSize()).as("plain Future wait does not expand the document pool").isEqualTo(1);
      release.countDown();
      assertThat(outer.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isEqualTo(WORKSPACE_URI);
    } finally {
      release.countDown();
      executor.shutdownNow();
      caller.shutdownNow();
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
