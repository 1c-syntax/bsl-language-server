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

import com.github._1c_syntax.bsl.languageserver.reporters.data.FileInfo;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Формирование отчётов активными репортёрами от начала анализа до его завершения.
 * <p>
 * Результаты передаются по одному через {@link #accept(FileInfo)} и раздаются всем репортёрам.
 * Запись выполняется в отдельном потоке: вызывающий не ждёт ввода-вывода, а {@link FileInfo}
 * освобождается сразу после того, как его записали все репортёры.
 * <p>
 * Отчёты считаются полными только после {@link #commit()}. Закрытие без фиксации — как и сбой
 * записи — прерывает отчёты: недописанные файлы не остаются на диске.
 */
public final class ReportSession implements AutoCloseable {

  private final List<DiagnosticReporter> reporters;
  private final ReportWriteQueue writeQueue;

  private boolean committed;

  ReportSession(List<DiagnosticReporter> reporters) {
    this.reporters = List.copyOf(reporters);
    this.writeQueue = new ReportWriteQueue("reporters");
  }

  /**
   * Передать результаты анализа файла всем репортёрам.
   * <p>
   * Возврат из метода не означает, что данные записаны: вызов лишь ставит запись в очередь
   * и не ждёт ни её выполнения, ни ввода-вывода.
   *
   * @param fileInfo результаты анализа одного файла
   */
  public void accept(FileInfo fileInfo) {
    writeQueue.submit(() -> reporters.forEach(reporter -> reporter.accept(fileInfo)));
  }

  /**
   * Пометить анализ успешно завершённым. Без этого отчёты будут прерваны при закрытии.
   */
  public void commit() {
    committed = true;
  }

  /**
   * Дождаться записи всех переданных результатов и завершить отчёты.
   *
   * @throws RuntimeException если запись или завершение отчёта не удались
   */
  @Override
  public void close() {
    RuntimeException writeFailure = null;
    try {
      writeQueue.close();
    } catch (RuntimeException e) {
      writeFailure = e;
    }

    var failure = finishReporters(committed && writeFailure == null);

    if (writeFailure != null) {
      if (failure != null) {
        writeFailure.addSuppressed(failure);
      }
      throw writeFailure;
    }
    if (failure != null) {
      throw failure;
    }
  }

  private @Nullable RuntimeException finishReporters(boolean successful) {
    RuntimeException failure = null;

    // ни один сбой не должен оборвать цикл: остальные репортёры обязаны получить завершение,
    // иначе их файлы останутся открытыми и недописанными
    for (var reporter : reporters) {
      var thrown = finishReporter(reporter, successful);
      if (thrown == null) {
        continue;
      }
      if (failure == null) {
        failure = thrown;
      } else {
        failure.addSuppressed(thrown);
      }
    }

    return failure;
  }

  private static @Nullable RuntimeException finishReporter(DiagnosticReporter reporter, boolean successful) {
    if (!successful) {
      return abortQuietly(reporter);
    }

    try {
      reporter.endReport();
      return null;
    } catch (RuntimeException e) {
      // завершить отчёт не вышло — не оставляем недописанный файл
      var abortFailure = abortQuietly(reporter);
      if (abortFailure != null) {
        e.addSuppressed(abortFailure);
      }
      return e;
    }
  }

  private static @Nullable RuntimeException abortQuietly(DiagnosticReporter reporter) {
    try {
      reporter.abortReport();
      return null;
    } catch (RuntimeException e) {
      return e;
    }
  }
}
