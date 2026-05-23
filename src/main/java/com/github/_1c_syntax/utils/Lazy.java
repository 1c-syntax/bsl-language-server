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
package com.github._1c_syntax.utils;

import org.jspecify.annotations.Nullable;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * Реализация хранения данных с ленивым чтением.
 * <p>
 * Shadow-копия из {@code io.github.1c-syntax:utils:0.7.0} с критичным фиксом:
 * {@code lock.unlock()} вынесен в {@code finally}-блок. В оригинальной версии
 * unlock стоит после {@code maybeCompute(...)} вне try/finally — любое исключение
 * в supplier'е приводит к навечно захваченному lock'у и deadlock'у всех последующих
 * обращений. Это особенно болезненно в native-image, где prototype-бины
 * ({@code CyclomaticComplexityComputer}, {@code CognitiveComplexityComputer})
 * хватают NPE на необложенных Spring AOT-инъекциях (`stringInterner`) и оставляют
 * {@code computeLock} навсегда занятым, после чего весь analyze-пул виснет на
 * {@code AbstractQueuedSynchronizer.acquire}.
 *
 * <p>На JVM баг тоже есть, просто не триггерится — supplier'ы там не падают.
 */
public final class Lazy<T> {

  private final Supplier<T> supplier;
  private final ReentrantLock lock;
  private volatile @Nullable T value;

  public Lazy(Supplier<T> supplier) {
    this(supplier, new ReentrantLock());
  }

  public Lazy(Supplier<T> supplier, ReentrantLock lock) {
    this.supplier = supplier;
    this.lock = lock;
  }

  @Nullable
  public T get() {
    return value;
  }

  public T getOrCompute(Supplier<T> supplier) {
    final T result = value; // Just one volatile read
    if (result == null) {
      lock.lock();
      try {
        return maybeCompute(supplier);
      } finally {
        lock.unlock();
      }
    }
    return result;
  }

  public T getOrCompute() {
    return getOrCompute(supplier);
  }

  public boolean isPresent() {
    final T result = value;
    return result != null;
  }

  public void clear() {
    value = null;
  }

  private T maybeCompute(Supplier<T> supplier) {
    if (value == null) {
      requireNonNull(supplier);
      value = requireNonNull(supplier.get());
    }
    return requireNonNull(value);
  }
}
