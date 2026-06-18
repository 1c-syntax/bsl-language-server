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

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.utils.Resources;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Утилита для добавления в hover-блок коллекционных подсказок (обход
 * {@code Для Каждого} и индексатор {@code [...]}) на основании данных о типе,
 * получаемых через {@link TypeService}. Информация подмешивается источниками
 * платформенных типов (bsl-context / JSON-fallback) из синтакс-помощника.
 */
@Component
@RequiredArgsConstructor
public class CollectionHoverHints {

  private final Resources resources;
  private final LanguageServerConfiguration configuration;
  private final TypeService typeService;

  /**
   * Добавляет markdown-блоки про обход и индексатор для типа, если
   * соответствующие признаки у типа заданы. Если у типа нет
   * ни forEach, ни indexAccess — ничего не пишет.
   * <p>
   * Формат блоков:
   * <pre>
   * **&lt;label-обхода&gt;** &lt;описание из синтакс-помощника&gt;
   * **&lt;label-индексатора&gt;** &lt;описание из синтакс-помощника&gt;
   * </pre>
   * Если тип поддерживает обход, но описание пустое — пишем общий текст-fallback.
   */
  public void append(StringBuilder sb, TypeRef ref, FileType fileType) {
    var supportsForEach = typeService.supportsForEach(ref, fileType);
    var supportsIndex = typeService.supportsIndexAccess(ref, fileType);
    if (!supportsForEach && !supportsIndex) {
      return;
    }
    var lang = configuration.getLanguage();
    if (supportsForEach) {
      sb.append("\n\n**").append(tr("forEachLabel")).append("** ");
      var description = typeService.getForEachDescription(ref, fileType, lang);
      sb.append(description.isBlank() ? tr("forEachFallback") : description);
    }
    if (supportsIndex) {
      sb.append("\n\n**").append(tr("indexAccessLabel")).append("** ");
      var description = typeService.getIndexAccessDescription(ref, fileType, lang);
      sb.append(description.isBlank() ? tr("indexAccessFallback") : description);
    }
  }

  private String tr(String key) {
    return resources.getResourceString(getClass(), key);
  }
}
