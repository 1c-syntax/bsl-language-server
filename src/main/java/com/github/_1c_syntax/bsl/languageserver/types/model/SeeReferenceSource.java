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
package com.github._1c_syntax.bsl.languageserver.types.model;

import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import org.eclipse.lsp4j.Range;

import java.net.URI;

/**
 * Функция, на которую ведёт {@code см.}-ссылка: ключ {@link LazyTypeSet} вместо самого символа.
 * <p>
 * Символ через родителя держит всё дерево символов своего документа, а ленивый тип живёт в
 * объявленных типах и после того, как документ перечитан с тем же текстом и получил новое
 * дерево. Ключом-символом он держал бы прежнее дерево целиком. Функции равны как ключи, когда
 * равны документ, имя и место имени, — как и символы одного текста.
 *
 * @param uri            документ функции.
 * @param name           имя функции.
 * @param selectionRange место имени функции.
 */
public record SeeReferenceSource(URI uri, String name, Range selectionRange) {

  /**
   * @param function функция.
   * @return ключ функции.
   */
  public static SeeReferenceSource of(MethodSymbol function) {
    return new SeeReferenceSource(function.getOwner().getUri(), function.getName(), function.getSelectionRange());
  }
}
