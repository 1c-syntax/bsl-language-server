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

/**
 * Символ модуля документа.
 */
@Value
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"children", "parent"})
public class ModuleSymbol implements SourceDefinedSymbol {
  /**
   * Имя символа.
   * <p>
   * Если у документа есть валидный mdoRef, то содержит его и (при необходимости) квалификатор в виде типа модуля
   * ({@link com.github._1c_syntax.bsl.types.ModuleType}).
   * В остальных случаях содержит строковое представление uri ({@link DocumentContext#getUri()}.
   */
  String name;

  SymbolKind symbolKind;

  @EqualsAndHashCode.Include
  DocumentContext owner;

  Range range;

  /**
   * Область первого токена модуля
   */
  @EqualsAndHashCode.Include
  Range selectionRange;

  @Getter
  @Setter
  @Builder.Default
  @NonFinal
  Optional<SourceDefinedSymbol> parent = Optional.empty();

  @Builder.Default
  List<SourceDefinedSymbol> children = new ArrayList<>();

  @Override
  public void accept(SymbolTreeVisitor visitor) {
    visitor.visitModule(this);
  }

}
