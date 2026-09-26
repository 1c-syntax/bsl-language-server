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
package com.github._1c_syntax.bsl.languageserver.types.registry;

import com.github._1c_syntax.bsl.context.api.ContextQueryTable;
import com.github._1c_syntax.bsl.mdclasses.CF;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.mdo.storage.form.FormDynamicListAttribute;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Запрос полей таблицы языка запросов.
 *
 * @param tableName    имя таблицы, как его задаёт конфигурация
 *                     ({@code Catalog.Номенклатура},
 *                     {@code AccumulationRegister.Продажи.Turnovers}).
 * @param mdo          объект метаданных таблицы; {@code null}, если по имени он
 *                     не нашёлся — например, у битой ссылки.
 * @param configuration метаданные конфигурации; {@code null}, если конфигурация
 *                     ещё не прочитана. Нужны источникам, которым мало объекта
 *                     таблицы: общий реквизит объявлен не в её составе, а со
 *                     своей стороны.
 * @param table        платформенное описание таблицы из синтакс-помощника;
 *                     {@code null}, если платформа не установлена либо такой
 *                     таблицы в её описании нет.
 * @param nameBindings имена, подставленные в плейсхолдеры имени таблицы
 *                     ({@code Имя справочника → Номенклатура}); пусто, если
 *                     таблица не найдена.
 * @param list         динамический список, спрашивающий поля; {@code null},
 *                     если поля спрашивают не для списка.
 */
record QueryTableRequest(String tableName,
                         @Nullable MD mdo,
                         @Nullable CF configuration,
                         @Nullable ContextQueryTable table,
                         Map<String, String> nameBindings,
                         @Nullable FormDynamicListAttribute list) {

  /**
   * Спрашивают ли поля собственной таблицы объекта метаданных, а не его
   * виртуальной: имя собственной — это ссылка объекта целиком, у виртуальной к
   * ней добавлен сегмент. Состав виртуальной таблицы задаёт платформа, и без её
   * описания сказать о нём нечего, а реквизиты объекта полями его собственной
   * таблицы являются всегда.
   *
   * @return {@code true}, если имя таблицы — это ссылка объекта.
   */
  boolean ownTableOfMdo() {
    if (mdo == null) {
      return false;
    }
    var reference = mdo.getMdoReference();
    return tableName.equalsIgnoreCase(reference.getMdoRef())
      || tableName.equalsIgnoreCase(reference.getMdoRefRu());
  }

  /**
   * Плейсхолдеры, которые платформа объявляет полями этой таблицы: у таблицы
   * оборотов это только измерения, ресурсы и общие реквизиты, а собственных
   * реквизитов регистра там нет вовсе. Пусто, если таблица не найдена.
   *
   * @return имена плейсхолдеров без угловых скобок.
   */
  List<String> declaredPlaceholders() {
    if (table == null) {
      return List.of();
    }
    return table.fields().stream()
      .filter(QueryTableFieldSource::isBarePlaceholder)
      .map(field -> QueryTableFieldSource.placeholderNames(field).get(0))
      .distinct()
      .toList();
  }
}
