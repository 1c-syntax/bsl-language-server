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
package com.github._1c_syntax.bsl.languageserver.diagnostics.infrastructure;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.diagnostics.BSLDiagnostic;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Провайдер для создания экземпляров диагностик.
 * <p>
 * Заменяет функциональность {@code DiagnosticBeanPostProcessor} —
 * устанавливает {@link DiagnosticInfo} и применяет конфигурацию параметров.
 */
@Component
@RequiredArgsConstructor
public class DiagnosticObjectProvider {

  private final ApplicationContext applicationContext;

  /**
   * Получить экземпляр диагностики без инициализации.
   * <p>
   * Используется для получения "голого" бина. Для полноценной работы
   * диагностики необходимо вызвать {@link #get(DiagnosticInfo, LanguageServerConfiguration)}.
   *
   * @param clazz класс диагностики
   * @return экземпляр диагностики без установленного {@link DiagnosticInfo}
   */
  public <T extends BSLDiagnostic> T get(Class<T> clazz) {
    return applicationContext.getBean(clazz);
  }

  /**
   * Получить полностью инициализированный экземпляр диагностики.
   * <p>
   * Устанавливает {@link DiagnosticInfo} и применяет конфигурацию параметров.
   *
   * @param info          метаданные диагностики
   * @param configuration per-workspace конфигурация
   * @return инициализированный экземпляр диагностики
   */
  @SuppressWarnings("unchecked")
  public <T extends BSLDiagnostic> T get(DiagnosticInfo info, LanguageServerConfiguration configuration) {
    T diagnostic = (T) applicationContext.getBean(info.getDiagnosticClass());

    // Set DiagnosticInfo (was done in DiagnosticBeanPostProcessor.postProcessBeforeInitialization)
    diagnostic.setInfo(info);

    // Initialize after info is set (replaces @PostConstruct in diagnostics that need info)
    diagnostic.initAfterInfoSet();

    // Configure diagnostic parameters (was done in DiagnosticBeanPostProcessor.postProcessAfterInitialization)
    Either<Boolean, Map<String, Object>> diagnosticConfiguration =
      configuration.getDiagnosticsOptions().getParameters().get(info.getCode().getStringValue());

    if (diagnosticConfiguration != null && diagnosticConfiguration.isRight()) {
      diagnostic.configure(diagnosticConfiguration.getRight());
    }

    return diagnostic;
  }

}
