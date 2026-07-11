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
package com.github._1c_syntax.bsl.languageserver.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AbstractObjectPoolTest {

  private static final class CountingPool extends AbstractObjectPool<Object> {

    private final AtomicInteger created = new AtomicInteger();

    private CountingPool() {
    }

    private CountingPool(int maxSize) {
      super(maxSize);
    }

    @Override
    protected Object create() {
      created.incrementAndGet();
      return new Object();
    }
  }

  @Test
  void checkOutReusesReturnedInstance() {
    var pool = new CountingPool();

    var first = pool.checkOut();
    pool.checkIn(first);
    var second = pool.checkOut();

    assertThat(second).isSameAs(first);
    assertThat(pool.created.get()).isEqualTo(1);
  }

  @Test
  void unboundedPoolCreatesInstancePerConcurrentCheckout() {
    var pool = new CountingPool();

    var first = pool.checkOut();
    var second = pool.checkOut();

    assertThat(second).isNotSameAs(first);
    assertThat(pool.created.get()).isEqualTo(2);
  }

  @Test
  @Timeout(10)
  void boundedPoolBlocksUntilCheckInAndHandsOverInstance() throws InterruptedException {
    var pool = new CountingPool(1);
    var first = pool.checkOut();

    var handedOver = new AtomicReference<>();
    var waiterStarted = new CountDownLatch(1);
    var waiterFinished = new CountDownLatch(1);
    var waiter = new Thread(() -> {
      waiterStarted.countDown();
      handedOver.set(pool.checkOut());
      waiterFinished.countDown();
    });
    waiter.start();

    assertThat(waiterStarted.await(5, TimeUnit.SECONDS)).isTrue();
    // Ожидающий поток не должен создать новый экземпляр сверх лимита
    assertThat(waiterFinished.await(200, TimeUnit.MILLISECONDS)).isFalse();
    assertThat(pool.created.get()).isEqualTo(1);

    pool.checkIn(first);

    assertThat(waiterFinished.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(handedOver.get()).isSameAs(first);
    assertThat(pool.created.get()).isEqualTo(1);
  }

  @Test
  @Timeout(10)
  void interruptedWaiterGetsInstanceOverCapAndKeepsInterruptFlag() throws InterruptedException {
    var pool = new CountingPool(1);
    pool.checkOut();

    var wasInterrupted = new AtomicReference<Boolean>();
    var received = new AtomicReference<>();
    var waiter = new Thread(() -> {
      received.set(pool.checkOut());
      wasInterrupted.set(Thread.currentThread().isInterrupted());
    });
    waiter.start();

    // Дождаться входа в ожидание и прервать
    while (waiter.getState() != Thread.State.WAITING) {
      TimeUnit.MILLISECONDS.sleep(10);
    }
    waiter.interrupt();
    waiter.join(TimeUnit.SECONDS.toMillis(5));

    assertThat(waiter.isAlive()).isFalse();
    assertThat(received.get()).isNotNull();
    assertThat(pool.created.get()).isEqualTo(2);
    assertThat(wasInterrupted.get()).isTrue();
  }

  @Test
  void toStringReportsPoolState() {
    var pool = new CountingPool();
    var instance = pool.checkOut();

    assertThat(pool).hasToString("Pool available=0 inUse=1");

    pool.checkIn(instance);

    assertThat(pool).hasToString("Pool available=1 inUse=0");
  }
}
