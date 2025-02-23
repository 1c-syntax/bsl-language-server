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

import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import lombok.Value;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;

import java.net.URI;
import java.util.Optional;

/**
 * Ссылка на символ.
 */
@Value
public class Reference {

  /**
   * Символ, в котором располагается данная ссылка.
   */
  SourceDefinedSymbol from;

  /**
   * Символ, на который указывает ссылка.
   */
  Symbol symbol;

  /**
   * URI, в котором находится ссылка.
   */
  URI uri;

  /**
   * Диапазон, в котором располагается ссылка.
   */
  Range selectionRange;

  /**
   * Тип обращения к символу в ссылке.
   */
  OccurrenceType occurrenceType;

  public Optional<SourceDefinedSymbol> getSourceDefinedSymbol() {
    return Optional.of(symbol)
      .filter(SourceDefinedSymbol.class::isInstance)
      .map(SourceDefinedSymbol.class::cast);
  }

  public boolean isSourceDefinedSymbolReference() {
    return symbol instanceof SourceDefinedSymbol;
  }

  public Location toLocation() {
    return new Location(uri.toString(), selectionRange);
  }

  public static Reference of(SourceDefinedSymbol from, Symbol symbol, Location location) {
    return of(from, symbol, location, OccurrenceType.REFERENCE);
  }

  public static Reference of(
    SourceDefinedSymbol from,
    Symbol symbol,
    Location location,
    OccurrenceType occurrenceType
  ) {
    return new Reference(from, symbol, URI.create(location.getUri()), location.getRange(), occurrenceType);
  }

}
