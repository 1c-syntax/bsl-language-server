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
package com.github._1c_syntax.bsl.languageserver.types.inferencer;

import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.junit.jupiter.api.Test;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTree;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;

class RootFlowComputationsTest {
  private final URI uri = URI.create("file:///root-flow.bsl");
  private final BSLParser.CodeBlockContext body = new BSLParser.CodeBlockContext(null, -1);
  private final RootFlowComputations computations = new RootFlowComputations();

  @Test
  void parallelRootsReadOnePublishedResult() throws Exception {
    var cache = new AtomicReference<String>();
    var calls = new AtomicInteger();
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var followerThread = new AtomicReference<Thread>();
    var executor = Executors.newFixedThreadPool(2, Thread.ofPlatform().daemon().factory());
    try {
      var first = executor.submit(() -> computations.compute(uri, body, ExecutionContext.SERVER, cache::get, () -> {
        calls.incrementAndGet(); entered.countDown(); require(release); cache.set("complete"); return "complete";
      }));
      assertThat(entered.await(5, SECONDS)).isTrue();
      var second = executor.submit(() -> {
        followerThread.set(Thread.currentThread());
        return computations.compute(uri, body, ExecutionContext.SERVER, cache::get, () -> {
          calls.incrementAndGet(); cache.set("duplicate"); return "duplicate";
        });
      });
      await().atMost(5, SECONDS).until(() -> second.isDone()
        || followerThread.get() != null && followerThread.get().getState() == Thread.State.WAITING);
      assertThat(calls).hasValue(1);
      release.countDown();
      assertThat(first.get(5, SECONDS)).isEqualTo("complete");
      assertThat(second.get(5, SECONDS)).isEqualTo("complete");
    } finally { release.countDown(); executor.shutdownNow(); }
  }

  @Test
  void ownersWithCrossedNestedRequestsDoNotWaitForEachOther() throws Exception {
    var otherBody = new BSLParser.CodeBlockContext(null, -1);
    var bothOwners = new CyclicBarrier(2);
    var executor = Executors.newFixedThreadPool(2, Thread.ofPlatform().daemon().factory());
    try {
      var first = executor.submit(() -> computations.compute(uri, body, ExecutionContext.SERVER, () -> null, () -> {
        meet(bothOwners);
        return computations.compute(uri, otherBody, ExecutionContext.SERVER, () -> null, () -> "nested-a");
      }));
      var second = executor.submit(() -> computations.compute(uri, otherBody, ExecutionContext.SERVER, () -> null, () -> {
        meet(bothOwners);
        return computations.compute(uri, body, ExecutionContext.SERVER, () -> null, () -> "nested-b");
      }));
      assertThat(first.get(5, SECONDS)).isEqualTo("nested-a");
      assertThat(second.get(5, SECONDS)).isEqualTo("nested-b");
    } finally { executor.shutdownNow(); }
  }

  @Test
  void failedComputationDoesNotCacheFailureOrKeepItsSlot() {
    assertThatThrownBy(() -> computations.compute(uri, body, ExecutionContext.SERVER, () -> null,
      () -> { throw new IllegalStateException("failed input"); })).isInstanceOf(IllegalStateException.class);
    assertThat(computations.compute(uri, body, ExecutionContext.SERVER, () -> null, () -> "retry"))
      .isEqualTo("retry");
  }

  @Test
  void unpersistedApproximationIsNotPassedToTheFollower() throws Exception {
    var cache = new AtomicReference<String>();
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var followerThread = new AtomicReference<Thread>();
    var executor = Executors.newFixedThreadPool(2, Thread.ofPlatform().daemon().factory());
    try {
      var first = executor.submit(() -> computations.compute(uri, body, ExecutionContext.SERVER, cache::get, () -> {
        entered.countDown(); require(release); return "partial";
      }));
      assertThat(entered.await(5, SECONDS)).isTrue();
      var second = executor.submit(() -> {
        followerThread.set(Thread.currentThread());
        return computations.compute(uri, body, ExecutionContext.SERVER, cache::get, () -> {
          cache.set("complete"); return "complete";
        });
      });
      await().atMost(5, SECONDS).until(() -> second.isDone()
        || followerThread.get() != null && followerThread.get().getState() == Thread.State.WAITING);
      release.countDown();
      assertThat(first.get(5, SECONDS)).isEqualTo("partial");
      assertThat(second.get(5, SECONDS)).isEqualTo("complete");
    } finally { release.countDown(); executor.shutdownNow(); }
  }

  @Test
  void failedOwnerReleasesTheFollowerForItsOwnRetry() throws Exception {
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var followerThread = new AtomicReference<Thread>();
    var executor = Executors.newFixedThreadPool(2, Thread.ofPlatform().daemon().factory());
    try {
      var first = executor.submit(() -> computations.compute(uri, body, ExecutionContext.SERVER, () -> null, () -> {
        entered.countDown(); require(release); throw new IllegalStateException("failed input");
      }));
      assertThat(entered.await(5, SECONDS)).isTrue();
      var second = executor.submit(() -> {
        followerThread.set(Thread.currentThread());
        return computations.compute(uri, body, ExecutionContext.SERVER, () -> null, () -> "retry");
      });
      await().atMost(5, SECONDS).until(() -> second.isDone()
        || followerThread.get() != null && followerThread.get().getState() == Thread.State.WAITING);
      release.countDown();
      assertThatThrownBy(() -> first.get(5, SECONDS)).hasCauseInstanceOf(IllegalStateException.class);
      assertThat(second.get(5, SECONDS)).isEqualTo("retry");
    } finally { release.countDown(); executor.shutdownNow(); }
  }

  @Test
  void differentModesDoNotShare() throws Exception {
    independent(uri, body, ExecutionContext.THIN_CLIENT);
  }

  @Test
  void newSymbolTreesDoNotWaitForPreviousModuleCellCalculations() throws Exception {
    var beforeEdit = mock(SymbolTree.class);
    var afterEdit = mock(SymbolTree.class);
    var entered = new CountDownLatch(2);
    var release = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2, Thread.ofPlatform().daemon().factory());
    try {
      var first = executor.submit(() -> computations.computeCells(uri, beforeEdit, ExecutionContext.SERVER,
        () -> null, () -> { entered.countDown(); require(release); return "before"; }));
      var second = executor.submit(() -> computations.computeCells(uri, afterEdit, ExecutionContext.SERVER,
        () -> null, () -> { entered.countDown(); require(release); return "after"; }));
      assertThat(entered.await(5, SECONDS)).as("an edited document has an independent calculation").isTrue();
      release.countDown();
      assertThat(first.get(5, SECONDS)).isEqualTo("before");
      assertThat(second.get(5, SECONDS)).isEqualTo("after");
    } finally { release.countDown(); executor.shutdownNow(); }
  }

  @Test
  void differentBodiesDoNotShare() throws Exception {
    independent(uri, new BSLParser.CodeBlockContext(null, -1), ExecutionContext.SERVER);
  }

  @Test
  void differentDocumentsDoNotShare() throws Exception {
    independent(URI.create("file:///other-root-flow.bsl"), body, ExecutionContext.SERVER);
  }

  private void independent(URI secondUri, BSLParser.CodeBlockContext secondBody, ExecutionContext secondMode)
    throws Exception {
    var entered = new CountDownLatch(2);
    var release = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2, Thread.ofPlatform().daemon().factory());
    try {
      var first = executor.submit(() -> computations.compute(uri, body, ExecutionContext.SERVER, () -> null, () -> {
        entered.countDown(); require(release); return "first";
      }));
      var second = executor.submit(() -> computations.compute(secondUri, secondBody, secondMode, () -> null, () -> {
        entered.countDown(); require(release); return "second";
      }));
      assertThat(entered.await(5, SECONDS)).as("independent keys can compute together").isTrue();
      release.countDown();
      assertThat(first.get(5, SECONDS)).isEqualTo("first");
      assertThat(second.get(5, SECONDS)).isEqualTo("second");
    } finally { release.countDown(); executor.shutdownNow(); }
  }

  private static void require(CountDownLatch latch) {
    try { if (!latch.await(10, SECONDS)) throw new AssertionError("peer did not finish"); }
    catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
  }

  private static void meet(CyclicBarrier barrier) {
    try { barrier.await(5, SECONDS); }
    catch (Exception error) { throw new AssertionError(error); }
  }
}
