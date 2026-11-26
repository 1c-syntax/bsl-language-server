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

/**
 * Ленивое поле «открытого» объекта: тип значения задан {@code см.}-ссылкой на
 * локальную функцию ({@link LazyTypeSet}), плюс текстовое описание из
 * doc-комментария ({@code * Поле - см. X - текст}). Аналог {@link LocalField}
 * для отложенно вычисляемых полей — описание сохраняется и доходит до подсказок.
 *
 * @param types       ленивый тип значения поля
 * @param description текстовое описание поля (может быть пустым)
 */
public record LazyField(LazyTypeSet types, String description) {

  public LazyField {
    if (description == null) {
      description = "";
    }
  }

  /**
   * Материализовать в {@link LocalField}: форсит ленивый тип, сохраняя описание.
   */
  public LocalField materialize() {
    return new LocalField(types.get(), description);
  }

  /**
   * Слияние одноимённых ленивых полей при union наборов: ленивые типы
   * объединяются ({@link LazyTypeSet#combine}), описание — первое непустое.
   */
  public static LazyField merge(LazyField first, LazyField second) {
    var mergedTypes = LazyTypeSet.combine(first.types, second.types);
    var mergedDescription = first.description.isBlank() ? second.description : first.description;
    return new LazyField(mergedTypes, mergedDescription);
  }
}
