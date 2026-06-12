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

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;

/**
 * Определение символа: где он объявлен.
 *
 * @param uri URI файла с объявлением.
 * @param range Полный диапазон объявления.
 * @param selectionRange Диапазон имени в объявлении.
 */
public record DefinitionDto(String uri, RangeDto range, RangeDto selectionRange) {

  public static DefinitionDto from(LocationLink link) {
    return new DefinitionDto(
      link.getTargetUri(),
      RangeDto.from(link.getTargetRange()),
      RangeDto.from(link.getTargetSelectionRange())
    );
  }

  public static DefinitionDto from(Location location) {
    return new DefinitionDto(
      location.getUri(),
      RangeDto.from(location.getRange()),
      RangeDto.from(location.getRange())
    );
  }
}
