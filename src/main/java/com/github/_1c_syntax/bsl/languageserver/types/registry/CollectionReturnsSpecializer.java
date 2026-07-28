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

import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberSource;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Уточняет типы возврата у членов коллекции под её конкретную строку.
 * <p>
 * Платформа описывает коллекцию обобщённо: {@code Добавить()} у любой табличной части
 * возвращает {@code Строка табличной части}, а {@code НайтиСтроки()} — просто
 * {@code Массив}. У специализации коллекции строка своя, с колонками, и без уточнения
 * цепочка обрывается на первом же вызове: {@code Товары.Добавить().Цена} не
 * разыменовывается, а обход результата {@code НайтиСтроки()} даёт нетипизированный элемент.
 * <p>
 * Уточняются три вида членов:
 * <ul>
 *   <li>возвращающие обобщённую строку ({@code Добавить}, {@code Вставить},
 *       {@code Получить}, {@code Найти}) — ссылка заменяется на конкретную строку;</li>
 *   <li>{@link #ROWS_SEARCH_METHOD} — возвращаемому {@code Массив} проставляется тип элемента;</li>
 *   <li>{@link #UNLOAD_METHOD} — возвращаемой {@code ТаблицаЗначений} проставляется строка
 *       с колонками коллекции.</li>
 * </ul>
 * Последние два уточняются по имени метода: тип элемента в объявлении возврата платформа
 * не называет, вывести его из объявления нечем.
 */
final class CollectionReturnsSpecializer {

  /** Метод коллекции, возвращающий {@code Массив} её строк. */
  private static final String ROWS_SEARCH_METHOD = "НайтиСтроки";

  /** Метод коллекции, выгружающий её в {@code ТаблицаЗначений} с теми же колонками. */
  private static final String UNLOAD_METHOD = "Выгрузить";

  /** Строка таблицы значений — носитель колонок выгруженной таблицы. */
  static final String VALUE_TABLE_ROW = "СтрокаТаблицыЗначений";

  private CollectionReturnsSpecializer() {
    // утилитный класс
  }

  /**
   * Отбирает члены обобщённой коллекции, тип возврата которых удалось уточнить,
   * и возвращает их уточнённые копии. Члены, у которых уточнять нечего, в результат
   * не попадают: он предназначен для регистрации override'ом поверх унаследованных.
   *
   * @param genericMembers члены обобщённого типа коллекции.
   * @param genericRow     обобщённый тип строки.
   * @param row            конкретный тип строки этой коллекции.
   * @param unloadedRow    строка выгруженной таблицы значений с колонками;
   *                       {@link TypeSet#EMPTY} — не уточнять {@code Выгрузить}.
   * @return уточнённые члены; пустой список, если уточнять было нечего.
   */
  static List<MemberDescriptor> specialize(Collection<MemberDescriptor> genericMembers,
                                           TypeRef genericRow, TypeRef row, TypeSet unloadedRow) {
    var specialized = new ArrayList<MemberDescriptor>();
    for (var member : genericMembers) {
      var refined = refine(member, genericRow, row, unloadedRow);
      if (refined != null) {
        specialized.add(refined);
      }
    }
    return List.copyOf(specialized);
  }

  /**
   * Уточнённая копия одного члена.
   *
   * @return копия с уточнённым типом возврата; {@code null}, если уточнять в члене нечего
   *   и он наследуется от обобщённого типа как есть.
   */
  @Nullable
  private static MemberDescriptor refine(MemberDescriptor member, TypeRef genericRow, TypeRef row,
                                         TypeSet unloadedRow) {
    var refs = member.returnTypes().refs();
    if (refs.contains(genericRow)) {
      var converted = new ArrayList<TypeRef>(refs.size());
      for (var ref : refs) {
        converted.add(ref.equals(genericRow) ? row : ref);
      }
      return member.withReturnTypes(TypeSet.of(converted));
    }
    if (member.matches(ROWS_SEARCH_METHOD)) {
      return member.withReturnTypes(withElements(member.returnTypes(), TypeSet.of(row)));
    }
    if (member.matches(UNLOAD_METHOD) && !unloadedRow.isEmpty()) {
      return member.withReturnTypes(withElements(member.returnTypes(), unloadedRow));
    }
    return null;
  }

  /** Тот же набор типов, но с проставленным типом элемента у каждой ссылки. */
  private static TypeSet withElements(TypeSet types, TypeSet elementTypes) {
    var decorated = types;
    for (var ref : types.refs()) {
      decorated = decorated.withElement(ref, elementTypes);
    }
    return decorated;
  }

  /**
   * Строка таблицы значений с колонками коллекции — то, что лежит в результате
   * {@code Выгрузить()}. Платформа объявляет возврат просто {@code ТаблицаЗначений},
   * хотя колонки у выгруженной таблицы те же, что у выгружаемой коллекции.
   *
   * @param valueTableRow ссылка на тип строки таблицы значений; {@code null} — тип
   *                      не зарегистрирован, уточнять не на чем.
   * @param columns       источник колонок коллекции.
   * @return типы строки с полями-колонками; {@link TypeSet#EMPTY}, если уточнять не на чем.
   */
  static TypeSet unloadedRow(@Nullable TypeRef valueTableRow, MemberSource columns) {
    if (valueTableRow == null) {
      return TypeSet.EMPTY;
    }
    var row = TypeSet.of(valueTableRow);
    for (var column : columns.getMembers()) {
      row = row.withField(valueTableRow, column.name(), column.returnTypes(), column.description());
    }
    return row;
  }
}
