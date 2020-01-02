/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

public final class Lazy<T> {

  private Supplier<T> supplier;
  private ReentrantLock lock;
  private volatile T value;

  public Lazy(Supplier<T> supplier) {
    this(supplier, new ReentrantLock());
  }

  public Lazy(Supplier<T> supplier, ReentrantLock lock) {
    // no need to initialize lazy-value
    this.supplier = supplier;
    this.lock = lock;
  }

  public T getOrCompute(Supplier<T> supplier) {
    final T result = value; // Just one volatile read
    if (result == null) {
      lock.lock();
      T localResult = maybeCompute(supplier);
      lock.unlock();
      return localResult;
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
    return value;
  }
}
