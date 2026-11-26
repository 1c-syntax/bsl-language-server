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
 * Поле «открытого» объекта данных ({@code Структура} / {@code ТаблицаЗначений}
 * с известным набором ключей/колонок): типы значения плюс текстовое описание из
 * doc-комментария (секции {@code Параметры:} / {@code Возвращаемое значение:}).
 * <p>
 * Описание может быть пустым ({@code ""}) — например, для ключей, накопленных из
 * {@code Структура.Вставить(...)} или {@code Колонки.Добавить(...)}, где текста нет.
 *
 * @param types       типы значения поля (union); никогда не {@code null}
 * @param description текстовое описание поля; никогда не {@code null}, может быть пустым
 */
public record LocalField(TypeSet types, String description) {

  public LocalField {
    if (types == null) {
      types = TypeSet.EMPTY;
    }
    if (description == null) {
      description = "";
    }
  }

  /**
   * Поле без описания (типы значения известны, текста нет).
   */
  public static LocalField of(TypeSet types) {
    return new LocalField(types, "");
  }

  /**
   * Слияние одноимённых полей при union наборов типов: типы объединяются,
   * описание — первое непустое (детерминированно, приоритет у {@code first}).
   */
  public static LocalField merge(LocalField first, LocalField second) {
    var mergedTypes = first.types.union(second.types);
    var mergedDescription = first.description.isBlank() ? second.description : first.description;
    return new LocalField(mergedTypes, mergedDescription);
  }
}
