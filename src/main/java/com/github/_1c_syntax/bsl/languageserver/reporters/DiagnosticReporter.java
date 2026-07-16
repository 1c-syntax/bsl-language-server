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

import com.github._1c_syntax.bsl.languageserver.reporters.data.AnalysisInfo;

import java.nio.file.Path;

/**
 * Интерфейс для репортеров результатов анализа.
 * <p>
 * Репортеры формируют отчеты о найденных диагностиках в различных форматах
 * (JSON, JUnit XML, консольный вывод и т.д.).
 */
public interface DiagnosticReporter {
  /**
   * Получить ключ (идентификатор) репортера.
   *
   * @return Уникальный ключ репортера
   */
  String key();

  /**
   * Сформировать отчет о результатах анализа.
   *
   * @param analysisInfo Информация о результатах анализа
   * @param outputDir Директория для сохранения отчета
   */
  void report(AnalysisInfo analysisInfo, Path outputDir);

  /**
   * Признак того, что репортеру для формирования отчета необходимы метрики документов.
   * <p>
   * Значение по умолчанию — {@code false}. Переопределяется в {@code true} только теми
   * репортерами, которые действительно включают метрики в отчет (в файл или иной вывод).
   * Это позволяет пропустить вычисление метрик, если ни один из активных репортеров их не использует.
   *
   * @return {@code true}, если репортеру нужны метрики документов, иначе {@code false}
   */
  default boolean isMetricCalculationRequired() {
    return false;
  }
}
