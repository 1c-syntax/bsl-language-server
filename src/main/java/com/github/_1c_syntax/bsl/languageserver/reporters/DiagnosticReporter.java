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

import java.nio.file.Path;

/**
 * Репортёр результатов анализа: формирует отчёт в своём формате (JSON, JUnit XML, SARIF и т.д.).
 * <p>
 * Отчёт формируется потоково. Результат каждого файла поступает отдельным вызовом
 * {@link #accept(FileInfo)} сразу после его разбора и должен быть записан немедленно:
 * накапливать {@link FileInfo} нельзя, иначе на большой конфигурации отчёт не поместится в память.
 * Если формату нужны сводные данные, копить следует агрегаты, а не сами результаты.
 * <p>
 * Порядок файлов — порядок завершения их разбора, он недетерминирован.
 * <p>
 * <b>Потоки.</b> Все методы вызываются последовательно и из одного потока, поэтому состояние
 * репортёра (открытый файл, генератор) не требует синхронизации.
 * <p>
 * <b>Жизненный цикл.</b> {@link #beginReport} вызывается один раз, затем произвольное число раз
 * {@link #accept}, затем ровно один из {@link #endReport} (успех) или {@link #abortReport}
 * (прерывание).
 */
public interface DiagnosticReporter {

  /**
   * Получить ключ (идентификатор) репортера.
   *
   * @return Уникальный ключ репортера
   */
  String key();

  /**
   * Начать отчёт: открыть файл, записать заголовок.
   *
   * @param context   сведения об анализе
   * @param outputDir каталог для сохранения отчёта
   */
  void beginReport(ReportContext context, Path outputDir);

  /**
   * Записать результат одного файла.
   *
   * @param fileInfo результаты анализа файла; после возврата из метода не сохраняется
   */
  void accept(FileInfo fileInfo);

  /**
   * Завершить отчёт: дописать концовку и зафиксировать файл.
   */
  void endReport();

  /**
   * Прервать отчёт: освободить ресурсы и не оставлять недописанный файл.
   * <p>
   * Вызывается на пути обработки ошибки, поэтому исключений не бросает. Реализация по умолчанию
   * ничего не делает — она подходит репортёрам, которым нечего откатывать.
   */
  default void abortReport() {
    // нечего откатывать
  }

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
