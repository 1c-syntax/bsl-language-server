/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2023
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
package com.github._1c_syntax.bsl.languageserver.aop.measures;

import com.github._1c_syntax.bsl.languageserver.utils.ThrowingSupplier;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Выполнение и агрегация замеров времени выполнения переданных операций.
 */
@Component
@ConditionalOnMeasuresEnabled
@NoArgsConstructor
@Slf4j
public class MeasureCollector {

  /**
   * Коллекция замеров.
   * <p>
   * В качестве ключа выступает тип замера (метрики, дерево разбора, диагностика, и т. д.), в качестве значений -
   * список длительностей выполнения замера.
   */
  @Getter
  private final Map<String, List<Long>> measures = new ConcurrentHashMap<>();

  /**
   * Выполнить операцию замера.
   *
   * @param supplier    Замеряемая операция.
   * @param measureType Текстовый идентификатор замера.
   *                    Результаты замеров с совпадающим идентификатором будут просуммированы.
   * @return Результат работы замеряемой операции.
   */
  @SneakyThrows
  public Object measureIt(ThrowingSupplier<?> supplier, String measureType) {
    long startDI = System.currentTimeMillis();
    var result = supplier.get();
    long endDI = System.currentTimeMillis();

    measures.computeIfAbsent(measureType, s -> new CopyOnWriteArrayList<>()).add(endDI - startDI);
    return result;
  }

  /**
   * Вывод накопленных замеров в лог.
   */
  public void printMeasures() {
    measures.entrySet().stream()
      .map(entry -> Map.entry(entry.getKey(), entry.getValue().stream().mapToLong(value -> value).sum()))
      .sorted(Comparator.comparingLong(Map.Entry::getValue))
      .map(entry -> String.format("%s - %d", entry.getKey(), entry.getValue()))
      .forEach(LOGGER::info);
  }
}
