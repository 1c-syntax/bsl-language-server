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
package com.github._1c_syntax.bsl.languageserver.context.computer;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.diagnostics.BSLDiagnostic;
import com.github._1c_syntax.bsl.languageserver.infrastructure.BslLsExecutors;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp4j.Diagnostic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Lookup;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.stream.Stream;

/**
 * Вычислитель диагностик для документа.
 * <p>
 * Параллельно прогоняет все зарегистрированные диагностики на общем CPU-пуле из
 * {@link BslLsExecutors}. Если вызов уже происходит из этого пула, работа
 * выполняется на текущем потоке без дополнительного {@code submit/join}.
 */
@Component
@Slf4j
public abstract class DiagnosticComputer {

  /** Минимальное число диагностик, при котором уход в parallel stream окупается. */
  private static final int PARALLEL_DIAGNOSTICS_THRESHOLD = 8;

  private BslLsExecutors executors;

  /**
   * Сеттер для Spring DI; вызывается фреймворком на этапе инициализации.
   *
   * @param executors общий holder исполнителей
   */
  @Autowired
  void setExecutors(BslLsExecutors executors) {
    this.executors = executors;
  }

  /**
   * Вычислить все диагностики для документа.
   *
   * @param documentContext Контекст документа для анализа
   * @return Список найденных диагностик
   */
  public List<Diagnostic> compute(DocumentContext documentContext) {
    if (executors != null && executors.isInCpuPool()) {
      return internalCompute(documentContext);
    }

    var pool = executors == null ? ForkJoinPool.commonPool() : executors.getCpuExecutor();
    return pool.invoke(ForkJoinTask.adapt(
      (Callable<List<Diagnostic>>) () -> internalCompute(documentContext)
    ));
  }

  /**
   * Параллельно (или последовательно для маленьких наборов) прогоняет все
   * диагностики и возвращает плоский список найденных, отфильтрованный по
   * подавлению из {@link DiagnosticIgnoranceComputer}.
   */
  private List<Diagnostic> internalCompute(DocumentContext documentContext) {
    var diagnosticIgnorance = documentContext.getDiagnosticIgnorance();

    var diagnostics = diagnostics(documentContext);
    if (diagnostics.isEmpty()) {
      return List.of();
    }

    var parallel = diagnostics.size() >= PARALLEL_DIAGNOSTICS_THRESHOLD;
    var stream = parallel ? diagnostics.parallelStream() : diagnostics.stream();

    var raw = stream
      .flatMap((BSLDiagnostic diagnostic) -> {
        try {
          return diagnostic.getDiagnostics(documentContext).stream();
        } catch (RuntimeException e) {
          var message = "Diagnostic computation error.%nFile: %s%nDiagnostic: %s".formatted(
            documentContext.getUri(),
            diagnostic.getInfo().getCode()
          );
          LOGGER.error(message, e);
          return Stream.empty();
        }
      })
      .toList();

    if (raw.isEmpty()) {
      return List.of();
    }

    var result = new ArrayList<Diagnostic>(raw.size());
    for (Diagnostic d : raw) {
      if (!diagnosticIgnorance.diagnosticShouldBeIgnored(d)) {
        result.add(d);
      }
    }
    return result;
  }

  /**
   * Список диагностик, применимых к данному документу. Реализация генерируется
   * Spring через {@link Lookup} — выбираются включённые диагностики из бина
   * {@code diagnostics} по конфигурации.
   *
   * @param documentContext документ, для которого подбираются диагностики
   * @return список диагностических правил
   */
  @Lookup("diagnostics")
  protected abstract List<BSLDiagnostic> diagnostics(DocumentContext documentContext);
}
