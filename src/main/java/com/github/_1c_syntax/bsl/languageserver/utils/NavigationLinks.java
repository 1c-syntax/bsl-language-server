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
package com.github._1c_syntax.bsl.languageserver.utils;

import org.eclipse.lsp4j.Range;

import java.net.URI;

/**
 * Построение навигационных таргетов для кликабельных ссылок (document links,
 * гиперссылки в hover): {@code uri#L<line>,<col>}.
 */
public final class NavigationLinks {

  private NavigationLinks() {
    // utility class
  }

  /**
   * Навигационный таргет {@code uri#L<line>,<col>} на начало диапазона.
   * <p>
   * Координаты — 1-based (как принято в подобных ссылках). Клиенты,
   * поддерживающие такой фрагмент (VS Code и совместимые), открывают файл и
   * позиционируются на нужную строку.
   *
   * @param uri   URI документа
   * @param range диапазон, на начало которого ведёт ссылка (обычно selectionRange
   *              символа — имя определения)
   * @return строка таргета вида {@code uri#L<line>,<col>}
   */
  public static String toTarget(URI uri, Range range) {
    var start = range.getStart();
    return "%s#L%d,%d".formatted(uri, start.getLine() + 1, start.getCharacter() + 1);
  }
}
