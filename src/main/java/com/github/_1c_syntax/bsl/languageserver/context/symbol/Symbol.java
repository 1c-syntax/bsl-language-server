/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2023
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
package com.github._1c_syntax.bsl.languageserver.context.symbol;

import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.SymbolTag;

import java.util.Collections;
import java.util.List;

/**
 * Мета-информация о логически конечной единице в модуле (переменная, метод, класс и т.д.).
 */
public interface Symbol {

  /**
   * @return Имя символа.
   */
  String getName();

  /**
   * @return Тип символа.
   */
  SymbolKind getSymbolKind();

  /**
   * @return Является ли символ "устаревшим".
   */
  default boolean isDeprecated() {
    return false;
  }

  /**
   * @return Список тегов символа.
   */
  default List<SymbolTag> getTags() {
    return this.isDeprecated()
      ? Collections.singletonList(SymbolTag.Deprecated)
      : Collections.emptyList();
  }

  /**
   * Обработчик захода в символ при обходе символьного дерева.
   *
   * @param visitor Обходчик дерева.
   */
  void accept(SymbolTreeVisitor visitor);

}
