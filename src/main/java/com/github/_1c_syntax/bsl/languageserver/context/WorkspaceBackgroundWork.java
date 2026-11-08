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
package com.github._1c_syntax.bsl.languageserver.context;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Допуск и остановка фоновой работы одной рабочей области.
 * <p>
 * Работа держит блокировку на чтение. Остановка сначала запрещает новую работу, затем берёт
 * блокировку на запись и дожидается завершения уже запущенной. Долгая работа проверяет
 * {@link #isStopped()} и заканчивается, освободив блокировку. Остановленный объект не переиспользуется.
 */
final class WorkspaceBackgroundWork {
  private final ReadWriteLock lock = new ReentrantReadWriteLock();
  private volatile boolean stopped;

  boolean run(Runnable work) {
    lock.readLock().lock();
    try {
      if (stopped) {
        return false;
      }
      work.run();
      return true;
    } finally {
      lock.readLock().unlock();
    }
  }

  void stop() {
    stopped = true;
    lock.writeLock().lock();
    lock.writeLock().unlock();
  }

  boolean isStopped() {
    return stopped;
  }
}
