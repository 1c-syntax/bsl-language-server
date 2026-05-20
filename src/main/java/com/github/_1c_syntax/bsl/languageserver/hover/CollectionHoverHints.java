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
package com.github._1c_syntax.bsl.languageserver.hover;

import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;

/**
 * Утилита для добавления в hover-блок коллекционных подсказок (обход
 * {@code Для Каждого} и индексатор {@code [...]}) на основании данных из
 * {@link TypeRegistry}. Информация подмешивается источниками
 * платформенных типов (bsl-context / JSON-fallback) из синтакс-помощника.
 */
public final class CollectionHoverHints {

  private CollectionHoverHints() {
  }

  /**
   * Добавляет markdown-блоки про обход и индексатор для типа, если
   * соответствующие признаки заданы в {@link TypeRegistry}. Если у типа нет
   * ни forEach, ни indexAccess — ничего не пишет.
   * <p>
   * Формат блоков:
   * <pre>
   * **Обход коллекции:** &lt;описание из синтакс-помощника&gt;
   * **Индексатор:** &lt;описание из синтакс-помощника&gt;
   * </pre>
   * Если в TypeRegistry есть {@code supportsForEach}, но описание пустое —
   * пишем общий текст: «доступен обход в цикле {@code Для Каждого}».
   */
  public static void append(StringBuilder sb, TypeRef ref, TypeRegistry registry) {
    if (sb == null || ref == null || registry == null) {
      return;
    }
    var supportsForEach = registry.supportsForEach(ref);
    var supportsIndex = registry.supportsIndexAccess(ref);
    if (!supportsForEach && !supportsIndex) {
      return;
    }
    if (supportsForEach) {
      sb.append("\n\n**Обход коллекции:** ");
      var description = registry.getForEachDescription(ref);
      sb.append(description.isBlank()
        ? "доступен обход `Для Каждого … Из … Цикл`."
        : description);
    }
    if (supportsIndex) {
      sb.append("\n\n**Индексатор `[…]`:** ");
      var description = registry.getIndexAccessDescription(ref);
      sb.append(description.isBlank()
        ? "доступен доступ к элементу по индексу или ключу."
        : description);
    }
  }
}
