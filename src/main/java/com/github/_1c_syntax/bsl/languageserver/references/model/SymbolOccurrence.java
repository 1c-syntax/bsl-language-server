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
package com.github._1c_syntax.bsl.languageserver.references.model;

import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

/**
 * Обращение к символу в файле.
 */
@Value
@AllArgsConstructor
@Builder
public class SymbolOccurrence implements Comparable<SymbolOccurrence> {

  /**
   * Тип обращения к символу.
   */
  OccurrenceType occurrenceType;

  /**
   * Символ, к которому происходит обращение.
   */
  Symbol symbol;

  /**
   * Месторасположение обращения к символу.
   */
  Location location;

  @Override
  public int compareTo(SymbolOccurrence o) {
    if (this.equals(o)) {
      return 0;
    }
    final var uriCompare = location.getUri().compareTo(o.location.getUri());
    if (uriCompare != 0) {
      return uriCompare;
    }
    final var rangesCompare = Ranges.compare(location.getRange(), o.location.getRange());
    if (rangesCompare != 0) {
      return rangesCompare;
    }
    final var occurenceCompare = occurrenceType.compareTo(o.occurrenceType);
    if (occurenceCompare != 0) {
      return occurenceCompare;
    }
    return symbol.compareTo(o.symbol);
  }
}
