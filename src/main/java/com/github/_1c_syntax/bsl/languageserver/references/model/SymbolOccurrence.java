/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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
package com.github._1c_syntax.bsl.languageserver.references.model;

import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * Обращение к символу в файле.
 *
 * @param occurrenceType Тип обращения к символу.
 * @param symbol         Символ, к которому происходит обращение.
 * @param location       Месторасположение обращения к символу.
 */
@Builder
public record SymbolOccurrence(
  OccurrenceType occurrenceType,
  Symbol symbol,
  Location location
) implements Comparable<SymbolOccurrence> {

  @Override
  public int compareTo(@Nullable SymbolOccurrence other) {
    if (other == null) {
      return 1;
    }

    return java.util.Comparator
      .comparing(SymbolOccurrence::location, java.util.Comparator.comparing(Location::getUri)
        .thenComparing((l1, l2) -> Ranges.compare(l1.getRange(), l2.getRange())))
      .thenComparing(SymbolOccurrence::occurrenceType)
      .thenComparing(SymbolOccurrence::symbol)
      .compare(this, other);
  }
}
