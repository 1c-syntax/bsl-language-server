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
package com.github._1c_syntax.bsl.languageserver.context.events;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import org.springframework.context.ApplicationEvent;

import java.io.Serial;

/**
 * Событие изменения содержимого контекста документа.
 * <p>
 * Публикуется при изменении текста документа
 * и необходимости пересчета контекстной информации.
 * <p>
 * Разбор документа не всегда означает правку: тот же самый текст перечитывается заново
 * после освобождения вторичных данных. Такой разбор выводы, сделанные по прежнему тексту,
 * обычно оставляет верными — отличить один случай от другого позволяет
 * {@link #isContentChanged()}. Исключение — дерево символов, устаревшее без правки текста
 * (классификаторы отвечают о его методах и присваиваниях иначе, чем при построении): по тому
 * же тексту оно строится заново, символы уже не равны прежним, и такой разбор — изменение.
 */
public class DocumentContextContentChangedEvent extends ApplicationEvent {

  @Serial
  private static final long serialVersionUID = 3091414460731918073L;

  /** Изменилось ли построенное по документу с прошлого разбора. */
  private final transient boolean contentChanged;

  /**
   * Событие разбора документа с изменившимся содержимым.
   *
   * @param source документ.
   */
  public DocumentContextContentChangedEvent(DocumentContext source) {
    this(source, true);
  }

  /**
   * @param source         документ.
   * @param contentChanged {@code true}, если разобранное содержимое отличается от
   *                       разобранного в прошлый раз либо по тому же тексту заново
   *                       построено устаревшее дерево символов; {@code false}, если
   *                       документ перечитан без изменений.
   */
  public DocumentContextContentChangedEvent(DocumentContext source, boolean contentChanged) {
    super(source);
    this.contentChanged = contentChanged;
  }

  /**
   * @return {@code true}, если разобранное содержимое отличается от разобранного в прошлый
   *     раз либо по тому же тексту заново построено устаревшее дерево символов: символы
   *     тогда не равны прежним; {@code false}, если это перечитывание того же текста с
   *     прежним деревом.
   */
  public boolean isContentChanged() {
    return contentChanged;
  }

  @Override
  public DocumentContext getSource() {
    return (DocumentContext) super.getSource();
  }
}
