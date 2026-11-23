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
package com.github._1c_syntax.bsl.languageserver.types.index;

import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;

/**
 * Ключ объявления метода для индексов возвращаемых типов.
 * Естественный порядок совпадает с равенством стандартных символов методов:
 * URI документа, имя с учётом регистра и диапазон имени. Версия документа может меняться,
 * поэтому она не участвует в сравнении ключей.
 *
 * @param method символ объявления.
 */
public record MethodDeclarationKey(MethodSymbol method) implements Comparable<MethodDeclarationKey> {

  @Override
  public int compareTo(MethodDeclarationKey other) {
    if (method == other.method) {
      return 0;
    }

    var byDocument = method.getOwner().getUri().compareTo(other.method.getOwner().getUri());
    if (byDocument != 0) {
      return byDocument;
    }

    var byName = method.getName().compareTo(other.method.getName());
    if (byName != 0) {
      return byName;
    }

    return Ranges.compare(method.getSubNameRange(), other.method.getSubNameRange());
  }
}
