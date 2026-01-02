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
package com.github._1c_syntax.bsl.languageserver.context.symbol;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Символ региона (области кода).
 * <p>
 * Представляет область кода, ограниченную директивами #Область/#Region и #КонецОбласти/#EndRegion,
 * с поддержкой вложенных регионов и методов.
 */
@Value
@Builder(access = lombok.AccessLevel.PUBLIC)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"children", "parent"})
public class RegionSymbol implements SourceDefinedSymbol {
  String name;
  @Builder.Default
  SymbolKind symbolKind = SymbolKind.Namespace;
  @EqualsAndHashCode.Include
  DocumentContext owner;
  Range range;
  Range startRange;
  Range endRange;
  @EqualsAndHashCode.Include
  Range regionNameRange;

  @Getter
  @Setter
  @Builder.Default
  @NonFinal
  Optional<SourceDefinedSymbol> parent = Optional.empty();

  @Builder.Default
  List<SourceDefinedSymbol> children = new ArrayList<>();

  public List<MethodSymbol> getMethods() {
    return children.stream()
      .filter(MethodSymbol.class::isInstance)
      .map(MethodSymbol.class::cast)
      .collect(Collectors.toList());
  }

  @Override
  public void accept(SymbolTreeVisitor visitor) {
    visitor.visitRegion(this);
  }

  @Override
  public Range getSelectionRange() {
    return getRegionNameRange();
  }
}
