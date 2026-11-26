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
package com.github._1c_syntax.bsl.languageserver.mcp.dto;

import org.eclipse.lsp4j.CallHierarchyItem;
import org.jspecify.annotations.Nullable;

/**
 * Элемент иерархии вызовов (метод/процедура).
 *
 * @param name Имя метода/процедуры.
 * @param kind Вид символа (имя {@code SymbolKind}).
 * @param detail Дополнительные сведения; {@code null}, если их нет.
 * @param uri URI файла, в котором объявлен элемент.
 * @param range Диапазон имени элемента в файле.
 */
public record CallHierarchyItemDto(
  String name,
  String kind,
  @Nullable String detail,
  String uri,
  RangeDto range
) {

  public static CallHierarchyItemDto from(CallHierarchyItem item) {
    return new CallHierarchyItemDto(
      item.getName(),
      item.getKind().name(),
      item.getDetail(),
      item.getUri(),
      RangeDto.from(item.getSelectionRange())
    );
  }
}
