/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2021
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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

import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.Optional;

/**
 * Ссылка на символ.
 */
@Value
@Builder
public class Reference {

  /**
   * Символ, в котором располагается данная ссылка.
   */
  @NonNull
  SourceDefinedSymbol from;

  /**
   * Символ, на который указывает ссылка.
   */
  @NonNull
  Symbol symbol;

  /**
   * URI, в котором находится ссылка.
   */
  @NonNull
  URI uri;

  /**
   * Диапазон, в котором располагается ссылка.
   */
  @NonNull
  Range selectionRange;

  /**
   * Признак указывающий на перезапись значения в месте расположения ссылки
   */
  @Builder.Default
  boolean isWrite = false;

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

}
