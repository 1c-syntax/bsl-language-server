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

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Агрегатор репортеров результатов анализа.
 * <p>
 * Управляет вызовом всех зарегистрированных репортеров
 * для формирования отчетов о результатах анализа.
 */
@Component
@RequiredArgsConstructor
public class ReportersAggregator {

  @Autowired
  private List<DiagnosticReporter> reporters;

  @Autowired
  @Qualifier("filteredReporters")
  @Lazy
  // Don't remove @Autowired annotation. It's needed for injecting filteredReporters bean correctly.
  private List<DiagnosticReporter> filteredReporters;

  /**
   * Начать формирование отчётов всеми активными репортёрами.
   * <p>
   * Результаты анализа передаются в возвращённую сессию по одному. Сессию необходимо закрыть,
   * а при успешном завершении анализа — предварительно зафиксировать.
   *
   * @param context   сведения об анализе
   * @param outputDir директория для сохранения отчётов
   * @return сессия формирования отчётов
   */
  public ReportSession beginReport(ReportContext context, Path outputDir) {
    var activeReporters = List.copyOf(filteredReporters);
    var startedReporters = new ArrayList<DiagnosticReporter>(activeReporters.size());

    try {
      for (DiagnosticReporter reporter : activeReporters) {
        // в список до вызова: упавший на полпути тоже мог успеть создать файл
        startedReporters.add(reporter);
        reporter.beginReport(context, outputDir);
      }
    } catch (RuntimeException e) {
      startedReporters.forEach(DiagnosticReporter::abortReport);
      throw e;
    }

    return new ReportSession(activeReporters);
  }

  /**
   * Определить, требуется ли вычисление метрик документов для активных репортеров.
   * <p>
   * Возвращает {@code true}, если хотя бы один из активных (отфильтрованных по опции
   * {@code --reporter}) репортеров заявляет о необходимости метрик. Позволяет вызывающей
   * стороне пропустить вычисление метрик, когда ни один отчет их не использует.
   *
   * @return {@code true}, если хотя бы одному активному репортеру нужны метрики
   */
  public boolean isMetricCalculationRequired() {
    return filteredReporters.stream().anyMatch(DiagnosticReporter::isMetricCalculationRequired);
  }

  /**
   * Получить список ключей всех доступных репортеров.
   *
   * @return Список ключей репортеров
   */
  public List<String> reporterKeys() {
    return reporters.stream()
      .map(DiagnosticReporter::key)
      .collect(Collectors.toList());
  }
}
