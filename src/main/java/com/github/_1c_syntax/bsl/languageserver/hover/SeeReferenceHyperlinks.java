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

import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;

/**
 * Создание markdown-гиперссылок на определения, на которые указывают
 * {@code См.}/{@code See}-ссылки, для отображения в hover.
 * <p>
 * Вынесено отдельно как переиспользуемая точка: используется уже сейчас для
 * меток обрыва рекурсивных см.-цепочек и готово к применению при доработке
 * представления см.-ссылок в подсказках.
 */
public final class SeeReferenceHyperlinks {

  private SeeReferenceHyperlinks() {
    // utility class
  }

  /**
   * Markdown-ссылка {@code [text](uri#L<line>,<col>)} на определение символа,
   * на которое указывает {@code См.}-ссылка.
   * <p>
   * Формат таргета совпадает с тем, что отдаёт
   * {@code SeeReferenceDocumentLinkSupplier} для ссылок в самом тексте
   * комментария, поэтому переход и позиционирование на нужную строку работают в
   * клиентах, поддерживающих такие ссылки (VS Code и совместимые).
   *
   * @param text   видимый текст ссылки (например, имя функции-конструктора)
   * @param target символ-определение, на которое указывает {@code См.}-ссылка
   * @return строка markdown-ссылки на определение {@code target}
   */
  public static String toMarkdownLink(String text, SourceDefinedSymbol target) {
    var start = target.getSelectionRange().getStart();
    return "[%s](%s#L%d,%d)".formatted(
      text,
      target.getOwner().getUri(),
      start.getLine() + 1,
      start.getCharacter() + 1
    );
  }
}
