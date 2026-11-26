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
package com.github._1c_syntax.bsl.languageserver.types.model;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Набор полей «открытого» объекта данных: цена накопления по одному полю за раз.
 * <p>
 * Так поля и набираются в жизни: по вызову {@code Х.Вставить("Поле", …)}, по колонке
 * {@code Х.Колонки.Добавить(…)}, по строке документирующего комментария. Каждый шаг
 * создаёт новый неизменяемый набор, копируя уже накопленные поля, поэтому цена растёт
 * квадратично по их числу.
 * <p>
 * Параметр {@code fieldCount} — сколько полей у объекта. Структуры на десятки ключей
 * в коде 1С обычны.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class TypeSetFieldsBenchmark {

  @Param({"5", "20", "50"})
  public int fieldCount;

  private TypeRef structure;
  private TypeSet valueTypes;
  private List<String> fieldNames;

  @Setup(Level.Trial)
  public void setup() {
    structure = new TypeRef(TypeKind.PLATFORM, "Структура");
    valueTypes = TypeSet.of(new TypeRef(TypeKind.PRIMITIVE, "Строка"));
    fieldNames = new ArrayList<>(fieldCount);
    for (var i = 0; i < fieldCount; i++) {
      fieldNames.add("Поле" + i);
    }
  }

  /**
   * Накопление полей по одному — как это делают вызовы {@code Вставить} по ходу метода.
   *
   * @param blackhole приёмник результата.
   */
  @Benchmark
  public void addFieldsOneByOne(Blackhole blackhole) {
    var types = TypeSet.of(structure);
    for (var name : fieldNames) {
      types = types.withField(structure, name, valueTypes);
    }
    blackhole.consume(types);
  }

  /**
   * Накопление тех же полей разом — так их знают колонки таблицы и doc-комментарий.
   *
   * @param blackhole приёмник результата.
   */
  @Benchmark
  public void addFieldsAtOnce(Blackhole blackhole) {
    var fields = new LinkedHashMap<String, LocalField>();
    for (var name : fieldNames) {
      fields.put(name, new LocalField(valueTypes, ""));
    }
    blackhole.consume(TypeSet.of(structure).withFields(structure, fields));
  }
}
