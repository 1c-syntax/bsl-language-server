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
package com.github._1c_syntax.bsl.languageserver.references;

import com.github._1c_syntax.bsl.languageserver.context.symbol.ConstructorSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.Exportable;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;

/**
 * Политика доступности ссылки: может ли место обращения фактически «видеть»
 * целевой символ (например, неэкспортный метод доступен только из своего модуля).
 */
final class ReferenceAccessibility {

  private ReferenceAccessibility() {
  }

  /**
   * Проверить доступность ссылки.
   *
   * @param reference проверяемая ссылка.
   * @return {@code true}, если целевой символ доступен из места обращения.
   */
  static boolean isAccessible(Reference reference) {
    if (!reference.isSourceDefinedSymbolReference()) {
      return true;
    }

    var to = reference.getSourceDefinedSymbol().orElseThrow();
    var from = reference.from();
    if (to.getOwner().equals(from.getOwner())) {
      return true;
    }

    // Конструктор OneScript-класса (ПриСозданииОбъекта/OnObjectCreate) по
    // convention'у объявляется без `Экспорт`, но фактически вызывается извне
    // через `Новый ИмяКласса()` — поэтому такая ссылка всегда accessible.
    if (to instanceof ConstructorSymbol) {
      return true;
    }

    if (to instanceof Exportable exportable) {
      return exportable.isExport();
    }

    return true;
  }
}
