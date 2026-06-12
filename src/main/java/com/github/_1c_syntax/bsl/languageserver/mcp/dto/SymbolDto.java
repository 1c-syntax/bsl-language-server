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

import org.eclipse.lsp4j.DocumentSymbol;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Символ документа (метод, процедура, переменная и т.п.) с вложенными дочерними символами.
 *
 * @param name Имя символа.
 * @param kind Вид символа (имя {@code SymbolKind}: {@code Method}, {@code Variable}, ...).
 * @param detail Дополнительные сведения (например, сигнатура); {@code null}, если их нет.
 * @param range Диапазон символа в тексте.
 * @param children Вложенные дочерние символы.
 */
public record SymbolDto(
  String name,
  String kind,
  @Nullable String detail,
  RangeDto range,
  List<SymbolDto> children
) {

  public static SymbolDto from(DocumentSymbol symbol) {
    var children = symbol.getChildren() == null
      ? List.<SymbolDto>of()
      : symbol.getChildren().stream().map(SymbolDto::from).toList();

    return new SymbolDto(
      symbol.getName(),
      symbol.getKind().name(),
      symbol.getDetail(),
      RangeDto.from(symbol.getRange()),
      children
    );
  }
}
