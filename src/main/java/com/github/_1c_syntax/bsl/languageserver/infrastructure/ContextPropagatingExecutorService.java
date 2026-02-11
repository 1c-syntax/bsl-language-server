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

import io.micrometer.context.ContextSnapshotFactory;
import lombok.RequiredArgsConstructor;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Обёртка над {@link ExecutorService}, прокидывающая контекст (Sentry, MDC и т.д.)
 * из вызывающего потока в поток исполнителя через {@link ContextSnapshotFactory}.
 * <p>
 * Оборачивает каждый {@link Runnable} и {@link Callable} перед передачей делегату:
 * захватывает снимок текущих ThreadLocal-значений и восстанавливает их в потоке исполнителя.
 */
@RequiredArgsConstructor
public class ContextPropagatingExecutorService implements ExecutorService {

  private static final ContextSnapshotFactory SNAPSHOT_FACTORY = ContextSnapshotFactory.builder().build();

  private final ExecutorService delegate;

  @Override
  public void execute(Runnable command) {
    delegate.execute(wrap(command));
  }

  @Override
  public <T> Future<T> submit(Callable<T> task) {
    return delegate.submit(wrap(task));
  }

  @Override
  public <T> Future<T> submit(Runnable task, T result) {
    return delegate.submit(wrap(task), result);
  }

  @Override
  public Future<?> submit(Runnable task) {
    return delegate.submit(wrap(task));
  }

  @Override
  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
    return delegate.invokeAll(tasks.stream().map(this::wrap).toList());
  }

  @Override
  public <T> List<Future<T>> invokeAll(
    Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit
  ) throws InterruptedException {
    return delegate.invokeAll(tasks.stream().map(this::wrap).toList(), timeout, unit);
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
    throws InterruptedException, ExecutionException {
    return delegate.invokeAny(tasks.stream().map(this::wrap).toList());
  }

  @Override
  public <T> T invokeAny(
    Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit
  ) throws InterruptedException, ExecutionException, TimeoutException {
    return delegate.invokeAny(tasks.stream().map(this::wrap).toList(), timeout, unit);
  }

  @Override
  public void shutdown() {
    delegate.shutdown();
  }

  @Override
  public List<Runnable> shutdownNow() {
    return delegate.shutdownNow();
  }

  @Override
  public boolean isShutdown() {
    return delegate.isShutdown();
  }

  @Override
  public boolean isTerminated() {
    return delegate.isTerminated();
  }

  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    return delegate.awaitTermination(timeout, unit);
  }

  private Runnable wrap(Runnable runnable) {
    var snapshot = SNAPSHOT_FACTORY.captureAll();
    return () -> {
      try (var scope = snapshot.setThreadLocals()) {
        runnable.run();
      }
    };
  }

  private <T> Callable<T> wrap(Callable<T> callable) {
    var snapshot = SNAPSHOT_FACTORY.captureAll();
    return () -> {
      try (var scope = snapshot.setThreadLocals()) {
        return callable.call();
      }
    };
  }
}
