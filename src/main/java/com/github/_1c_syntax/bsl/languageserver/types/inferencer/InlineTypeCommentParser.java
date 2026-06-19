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
package com.github._1c_syntax.bsl.languageserver.types.inferencer;

import lombok.experimental.UtilityClass;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Парсер inline-типизирующих комментариев вида
 * <pre>
 *   X = F();        // Тип -
 *   X = F();        // Тип1, Тип2 -
 *   X = F();        // Тип
 *   Перем Y;        // Тип -
 * </pre>
 * Стандарт 1С:EDT допускает указание типа в строке инициализации; формат
 * фактически совпадает с записью {@code TypeName[, TypeName]* ['-' описание]}.
 * Парсер возвращает список идентификаторов типов; всё после первого
 * непробельного дефиса игнорируется как пользовательское описание.
 * <p>
 * Ссылки {@code См. Модуль.Метод} здесь не поддерживаются — это территория
 * отдельной задачи; такая запись возвращает пустой список.
 */
@UtilityClass
public class InlineTypeCommentParser {

  /**
   * Извлечь имена типов из текста комментария.
   *
   * @param commentText текст комментария вместе с ведущими {@code //}
   * @return список trim'нутых имён типов; пустой, если комментарий не парсится
   */
  public static List<String> parseTypeNames(String commentText) {
    if (commentText == null) {
      return Collections.emptyList();
    }
    var body = commentText.trim();
    if (body.startsWith("//")) {
      body = body.substring(2).trim();
    }
    if (body.isEmpty()) {
      return Collections.emptyList();
    }
    // Ссылка на функцию-конструктор обрабатывается отдельной задачей.
    if (body.regionMatches(true, 0, "См.", 0, 3) || body.regionMatches(true, 0, "See.", 0, 4)) {
      return Collections.emptyList();
    }
    var dashIndex = findDescriptionDash(body);
    if (dashIndex >= 0) {
      body = body.substring(0, dashIndex).trim();
    }
    if (body.isEmpty()) {
      return Collections.emptyList();
    }
    var parts = body.split(",");
    var result = new java.util.ArrayList<String>(parts.length);
    for (var part : parts) {
      var name = headType(part.trim());
      if (!name.isEmpty() && isLikelyTypeIdentifier(name)) {
        result.add(name);
      }
    }
    return result;
  }

  /**
   * Голова коллекционного типа в нотации {@code Тип из ЭлементТип} /
   * {@code Type of Element} (например {@code Массив из Число} → {@code Массив}).
   * Разделитель {@code из}/{@code of} должен быть отдельным словом (окружён
   * пробелами), иначе часть возвращается без изменений — чтобы свободный
   * многословный комментарий остался невалидным идентификатором и был отброшен.
   */
  private static String headType(String part) {
    var lower = part.toLowerCase(Locale.ROOT);
    for (var separator : SEPARATORS) {
      var idx = lower.indexOf(separator);
      if (idx > 0) {
        return part.substring(0, idx).trim();
      }
    }
    return part;
  }

  private static final List<String> SEPARATORS = List.of(" из ", " of ");

  /**
   * Найти позицию дефиса-разделителя «типы — описание». Дефис должен быть
   * окружён пробелами (или стоять в конце), чтобы не съесть составные имена
   * типа {@code Справочник-Контрагент} (теоретически невозможные в 1С, но
   * на всякий случай).
   */
  private static int findDescriptionDash(String body) {
    int i = 0;
    while (i < body.length()) {
      int dash = body.indexOf('-', i);
      if (dash < 0) {
        return -1;
      }
      boolean leftOk = dash == 0 || Character.isWhitespace(body.charAt(dash - 1));
      boolean rightOk = dash == body.length() - 1 || Character.isWhitespace(body.charAt(dash + 1));
      if (leftOk && rightOk) {
        return dash;
      }
      i = dash + 1;
    }
    return -1;
  }

  /**
   * Грубая фильтрация: имя типа в 1С начинается с буквы и состоит из букв,
   * цифр и точек (для qualified-имён вида {@code СправочникСсылка.Товары}).
   */
  private static boolean isLikelyTypeIdentifier(String s) {
    if (s.isEmpty() || !Character.isLetter(s.charAt(0))) {
      return false;
    }
    for (int i = 1; i < s.length(); i++) {
      var c = s.charAt(i);
      if (!Character.isLetterOrDigit(c) && c != '.' && c != '_') {
        return false;
      }
    }
    return true;
  }
}
