/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2021
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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

import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import org.eclipse.lsp4j.MarkupContent;

/**
 * Интерфейс построителя контента для всплывающего окна на основе символа.
 *
 * @param <T> Символ ({@link Symbol}) для которого предназначен данный построитель.
 */
public interface MarkupContentBuilder<T extends Symbol> {
  /**
   * Возвращает контент для всплывающего окна на основе символа.
   *
   * @param symbol Символ, для которого нужно построить контент.
   * @return Сконструированный контент.
   */
  MarkupContent getContent(T symbol);

  /**
   * Тип символа, на основе которого работает данный построитель.
   *
   * @return тип символа (см. {@link Symbol})
   */
  Class<T> getType();
}
