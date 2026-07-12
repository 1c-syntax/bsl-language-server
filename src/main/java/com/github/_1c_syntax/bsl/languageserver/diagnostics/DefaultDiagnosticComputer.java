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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.computer.DiagnosticComputer;
import com.github._1c_syntax.bsl.languageserver.context.computer.DiagnosticIgnoranceComputer;
import com.github._1c_syntax.bsl.languageserver.context.computer.GitBlameComputer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp4j.Diagnostic;
import org.springframework.beans.factory.annotation.Lookup;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Predicate;

/**
 * Реализация {@link DiagnosticComputer} по умолчанию.
 * <p>
 * Параллельно вычисляет диагностики всеми зарегистрированными анализаторами {@link BSLDiagnostic}
 * с обработкой ошибок и фильтрацией по правилам подавления и игнорируемым авторам.
 * <p>
 * Каждый анализатор — отдельная задача на {@code diagnosticComputerExecutor}; результаты
 * собираются через {@link Future#get()}.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public abstract class DefaultDiagnosticComputer implements DiagnosticComputer {

  private final LanguageServerConfiguration configuration;

  @Qualifier("diagnosticComputerExecutor")
  private final ExecutorService executor;

  @Override
  public List<Diagnostic> compute(DocumentContext documentContext) {
    // Сбор через Future.get() обычного пула, а не parallelStream() на ForkJoinPool с блокирующим
    // join(): блокировка на get() не проходит через ForkJoinPool.managedBlock, поэтому блокирующий
    // вызов из воркера ForkJoinPool не плодит компенсирующие потоки и живые DocumentContext.
    DiagnosticIgnoranceComputer.Data diagnosticIgnorance = documentContext.getDiagnosticIgnorance();

    var ignoredAuthors = configuration.getDiagnosticsOptions().getIgnoredAuthors();
    GitBlameComputer.Data gitBlameIgnorance = ignoredAuthors.isEmpty()
      ? GitBlameComputer.Data.empty()
      : new GitBlameComputer(documentContext.getUri(), ignoredAuthors).compute();

    var futures = diagnostics(documentContext).stream()
      .map((BSLDiagnostic diagnostic) ->
        executor.submit(() -> safeGetDiagnostics(diagnostic, documentContext)))
      .toList();

    return futures.stream()
      .map(DefaultDiagnosticComputer::getUninterruptibly)
      .flatMap(List::stream)
      .filter(Predicate.not(diagnosticIgnorance::diagnosticShouldBeIgnored))
      .filter(Predicate.not(gitBlameIgnorance::diagnosticShouldBeIgnored))
      .toList();
  }

  private List<Diagnostic> safeGetDiagnostics(BSLDiagnostic diagnostic, DocumentContext documentContext) {
    try {
      return diagnostic.getDiagnostics(documentContext);
    } catch (RuntimeException e) {
      var message = "Diagnostic computation error.%nFile: %s%nDiagnostic: %s".formatted(
        documentContext.getUri(),
        diagnostic.getInfo().getCode()
      );
      LOGGER.error(message, e);

      return List.of();
    }
  }

  private static List<Diagnostic> getUninterruptibly(Future<List<Diagnostic>> future) {
    try {
      return future.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while computing diagnostics", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Error computing diagnostics", e);
    }
  }

  @Lookup("diagnostics")
  protected abstract List<BSLDiagnostic> diagnostics(DocumentContext documentContext);
}
