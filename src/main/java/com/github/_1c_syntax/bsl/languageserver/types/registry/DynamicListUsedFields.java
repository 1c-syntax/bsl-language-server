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

import com.github._1c_syntax.bsl.mdo.storage.FormData;
import com.github._1c_syntax.bsl.mdo.storage.form.FormAttribute;
import com.github._1c_syntax.bsl.mdo.storage.form.FormDataPathOwner;
import com.github._1c_syntax.bsl.mdo.storage.form.FormDynamicListAttribute;
import com.github._1c_syntax.bsl.mdo.storage.form.FormTable;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Поля динамического списка, которые читает форма.
 * <p>
 * Данные строки — это не все поля списка: платформа читает только те, которые
 * форме зачем-то нужны. Нужными их делают
 * <ul>
 *   <li>элемент формы, привязанный к полю;</li>
 *   <li>картинка строки таблицы — колонки у такого поля нет, а данные читаются;</li>
 *   <li>пометка «использовать всегда» у реквизита;</li>
 *   <li>отбор, порядок и условное оформление самого списка;</li>
 *   <li>условное оформление формы.</li>
 * </ul>
 * Путь вглубь называет две колонки сразу: звено-ссылку и разыменованное поле
 * с составным именем — у элемента на {@code Список.Организация.ИНН} в данных
 * строки лежат и {@code Организация}, и {@code Организация.ИНН}.
 * Всё остальное, что объявляет запрос или таблица, в данные строки не попадает.
 * <p>
 * Тильдой форма помечает битое свойство — путь ведёт туда, чего в источнике
 * нет. Поле формы под таким путём всё равно есть, поэтому имя его в набор
 * попадает; типа у такой колонки не будет.
 */
final class DynamicListUsedFields {

  private DynamicListUsedFields() {
  }

  /**
   * Поля, которые форма читает у каждого своего динамического списка.
   *
   * @param data содержимое формы.
   * @return {@code имя реквизита (lower) → имена полей}; у списка, полей
   *   которого форма не читает, набор пуст.
   */
  static Map<String, Set<String>> collect(FormData data) {
    var result = new LinkedHashMap<String, Set<String>>();
    for (var attribute : data.getAttributes()) {
      if (attribute instanceof FormDynamicListAttribute list && !list.getName().isBlank()) {
        result.put(list.getName().toLowerCase(Locale.ROOT), fieldsOf(list, data));
      }
    }
    return Map.copyOf(result);
  }

  private static Set<String> fieldsOf(FormDynamicListAttribute list, FormData data) {
    var fields = new LinkedHashSet<String>();
    var listName = list.getName();
    for (var element : data.getPlainElements()) {
      if (element instanceof FormDataPathOwner pathOwner) {
        addPath(fields, listName, pathOwner.getDataPath());
      }
      if (element instanceof FormTable table) {
        addPath(fields, listName, table.getRowPictureDataPath());
      }
    }
    list.getUseAlwaysFields().forEach(path -> addPath(fields, listName, path));
    data.getConditionalAppearanceFields().forEach(path -> addPath(fields, listName, path));
    // Настройки самого списка называют поля без имени реквизита: список — их владелец.
    list.getSettingsFields().forEach(field -> addField(fields, field));
    return Set.copyOf(fields);
  }

  /**
   * Добавляет поле из пути формы ({@code Список.Контрагент.ИНН}), если путь
   * ведёт в этот список. Полем строки становится первое звено после имени
   * реквизита: остальные — уже свойства его типа.
   */
  private static void addPath(Set<String> fields, String listName, String dataPath) {
    var path = withoutBrokenMarker(dataPath);
    var separator = path.indexOf('.');
    if (separator < 0 || !path.regionMatches(true, 0, listName, 0, separator)
      || separator != listName.length()) {
      return;
    }
    addField(fields, path.substring(separator + 1));
  }

  /**
   * Поле, названное путём внутри списка. Путь вглубь даёт две колонки: само
   * звено-ссылку и разыменованное поле, у которого имя составное —
   * {@code Организация.ИНН} лежит в данных строки под этим самым именем,
   * рядом с {@code Организация}.
   */
  private static void addField(Set<String> fields, String field) {
    var name = withoutBrokenMarker(field);
    if (name.isBlank()) {
      return;
    }
    var separator = name.indexOf('.');
    if (separator < 0) {
      fields.add(name);
      return;
    }
    var head = name.substring(0, separator);
    if (!head.isBlank()) {
      fields.add(head);
      fields.add(name);
    }
  }

  /**
   * Снимает тильду — пометку битого свойства. Поле формы под таким путём есть,
   * так что имя его нужно; типа у него не будет — источник такого поля не знает.
   */
  private static String withoutBrokenMarker(String path) {
    return path.startsWith("~") ? path.substring(1) : path;
  }

  /**
   * Читает ли форма хоть одно поле списка. Список, у которого не читается
   * ничего, строку всё равно получает: у неё есть члены самой строки данных.
   */
  static boolean isEmpty(Map<String, Set<String>> usedFields, FormAttribute list) {
    return usedFields.getOrDefault(list.getName().toLowerCase(Locale.ROOT), Set.of()).isEmpty();
  }

  /**
   * Имена полей одного списка.
   */
  static List<String> of(Map<String, Set<String>> usedFields, FormAttribute list) {
    return List.copyOf(usedFields.getOrDefault(list.getName().toLowerCase(Locale.ROOT), Set.of()));
  }
}
