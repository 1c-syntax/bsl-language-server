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
import com.github._1c_syntax.bsl.parser.description.MethodDescription;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Setter;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Символ аннотации.
 * <p>
 * Представляет компиляторную аннотацию или директиву препроцессора
 * с параметрами, применяемую к методу или модулю.
 */
@Value
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"parent"})
public class AnnotationSymbol implements SourceDefinedSymbol, Describable {

  String name;

  @EqualsAndHashCode.Include
  DocumentContext owner;

  Range range;

  @EqualsAndHashCode.Include
  Range selectionRange;

  @Setter
  @NonFinal
  @Builder.Default
  Optional<SourceDefinedSymbol> parent = Optional.empty();

  Optional<MethodDescription> description;

  @Override
  public List<SourceDefinedSymbol> getChildren() {
    return Collections.emptyList();
  }

  public SymbolKind getSymbolKind() {
    return SymbolKind.Interface;
  }

  @Override
  public void accept(SymbolTreeVisitor visitor) {
    // no-op
  }

  public static AnnotationSymbol from(String name, MethodSymbol methodSymbol) {
    return AnnotationSymbol.builder()
      .name(name)
      .owner(methodSymbol.getOwner())
      .range(methodSymbol.getRange())
      .selectionRange(methodSymbol.getSelectionRange())
      .description(methodSymbol.getDescription())
      .parent(Optional.of(methodSymbol))
      .build();
  }
}
