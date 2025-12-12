/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp4j.Diagnostic;
import org.springframework.beans.factory.annotation.Lookup;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Вычислитель диагностик для документа.
 * <p>
 * Абстрактный класс, обеспечивающий параллельное вычисление диагностик
 * всеми зарегистрированными анализаторами с обработкой ошибок.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public abstract class DiagnosticComputer {

  @Qualifier("diagnosticComputerExecutor")
  private final ThreadPoolTaskExecutor executor;

  @PostConstruct
  private void init() {
  }

  /**
   * Вычислить все диагностики для документа.
   *
   * @param documentContext Контекст документа для анализа
   * @return Список найденных диагностик
   */
  public List<Diagnostic> compute(DocumentContext documentContext) {
    return CompletableFuture
      .supplyAsync(() -> internalCompute(documentContext), executor)
      .join();
  }

  private List<Diagnostic> internalCompute(DocumentContext documentContext) {
    DiagnosticIgnoranceComputer.Data diagnosticIgnorance = documentContext.getDiagnosticIgnorance();

    return diagnostics(documentContext).parallelStream()
      .flatMap((BSLDiagnostic diagnostic) -> {
        try {
          return diagnostic.getDiagnostics(documentContext).stream();
        } catch (RuntimeException e) {
          var message = String.format(
            "Diagnostic computation error.%nFile: %s%nDiagnostic: %s",
            documentContext.getUri(),
            diagnostic.getInfo().getCode()
          );
          LOGGER.error(message, e);

          return Stream.empty();
        }
      })
      .filter(Predicate.not(diagnosticIgnorance::diagnosticShouldBeIgnored))
      .toList();

  }

  @Lookup("diagnostics")
  protected abstract List<BSLDiagnostic> diagnostics(DocumentContext documentContext);
}
