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

import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.jspecify.annotations.Nullable;

/**
 * Символ рабочей папки в ответе MCP-инструмента {@code workspace_folder_symbol_search}.
 *
 * @param name Имя символа.
 * @param kind Вид символа ({@link org.eclipse.lsp4j.SymbolKind}).
 * @param containerName Ссылка на объект метаданных, которому принадлежит модуль символа (например,
 *                      {@code CommonModule.Сервис}); {@code null}, если у модуля нет объекта метаданных
 *                      (файл OneScript, модуль вне конфигурации).
 * @param location Расположение символа.
 */
public record WorkspaceSymbolDto(
  String name,
  String kind,
  @Nullable String containerName,
  LocationDto location
) {

  /**
   * Собрать DTO из LSP-символа рабочей области.
   *
   * @param symbol Символ рабочей области.
   * @return DTO символа.
   */
  public static WorkspaceSymbolDto from(WorkspaceSymbol symbol) {
    var either = symbol.getLocation();
    // Провайдер всегда отдаёт полное расположение; на случай облегчённого варианта с одним URI диапазон пустой.
    var location = either.isLeft()
      ? either.getLeft()
      : new Location(either.getRight().getUri(), Ranges.create());
    return new WorkspaceSymbolDto(
      symbol.getName(),
      symbol.getKind().name(),
      symbol.getContainerName(),
      LocationDto.from(location)
    );
  }
}
