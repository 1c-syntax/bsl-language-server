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

import com.github._1c_syntax.bsl.context.api.ContextNames;
import com.github._1c_syntax.bsl.context.api.ContextQueryTableField;
import com.github._1c_syntax.bsl.context.api.Placeholder;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;

import java.util.List;

/**
 * Источник полей таблицы языка запросов.
 * <p>
 * Поля одной таблицы приходят из разных мест — из синтакс-помощника, из
 * метаданных объекта, из общих реквизитов, из состава полей самого списка, —
 * и {@link QueryTableResolver} только опрашивает источники по очереди.
 * Новый вид источника добавляется бином, а не правкой резолвера; порядок
 * задаётся {@link org.springframework.core.annotation.Order}, и при
 * совпадении имён выигрывает поле из источника, спрошенного раньше.
 */
interface QueryTableFieldSource {

  /**
   * Поля, которые этот источник знает про запрошенную таблицу.
   *
   * @param request запрос полей.
   * @return поля; пусто, если источник к этой таблице неприменим.
   */
  List<MemberDescriptor> fields(QueryTableRequest request);

  /**
   * Плейсхолдеры имени поля без угловых скобок ({@code <Имя ресурса>Оборот} →
   * {@code [Имя ресурса]}).
   *
   * @param field поле таблицы.
   * @return имена плейсхолдеров в порядке появления; пусто, если имя конкретное.
   */
  static List<String> placeholderNames(ContextQueryTableField field) {
    return ContextNames.placeholders(field.name().getName()).stream()
      .map(Placeholder::name)
      .toList();
  }

  /**
   * Имя поля — ровно один плейсхолдер и ничего больше ({@code <Имя измерения>}).
   * Такое поле называет вид детей объекта метаданных целиком, поэтому его
   * материализует источник метаданных, знающий их настоящие типы. Поле с
   * плейсхолдером внутри имени ({@code <Имя ресурса>Оборот}) — другое дело:
   * структуру имени задаёт платформа, и материализует его платформенный источник.
   *
   * @param field поле таблицы.
   * @return {@code true}, если имя состоит из единственного плейсхолдера.
   */
  static boolean isBarePlaceholder(ContextQueryTableField field) {
    var name = field.name().getName();
    var placeholders = ContextNames.placeholders(name);
    return placeholders.size() == 1
      && placeholders.get(0).start() == 0
      && placeholders.get(0).end() == name.length();
  }
}
