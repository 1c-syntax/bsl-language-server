/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
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
package com.github._1c_syntax.bsl.languageserver.context.symbol;

import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.Annotation;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.CompilerDirectiveKind;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.eclipse.lsp4j.Range;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Value
@Builder
@EqualsAndHashCode(exclude = {"children", "parent"})
@ToString(exclude = {"children", "parent"})
public class MethodSymbol implements Symbol {
  String name;

  Range range;
  Range subNameRange;

  @Getter
  @Setter
  @Builder.Default
  @NonFinal
  Optional<Symbol> parent = Optional.empty();

  @Builder.Default
  List<Symbol> children = new ArrayList<>();

  boolean function;
  boolean export;
  Optional<MethodDescription> description;

  boolean deprecated;

  /**
   * Ссылка на объект метаданных, в модуле которого находится метод
   * Формат ссылки: Document.Заказ, CommonModule.ОбщегоНазначения
   */
  String mdoRef;

  @Builder.Default
  List<ParameterDefinition> parameters = new ArrayList<>();

  @Builder.Default
  Optional<CompilerDirectiveKind> compilerDirectiveKind = Optional.empty();
  @Builder.Default
  List<Annotation> annotations = new ArrayList<>();

  public Optional<RegionSymbol> getRegion() {
    return getParent()
      .filter(symbol -> symbol instanceof RegionSymbol)
      .map(symbol -> (RegionSymbol) symbol);
  }

  @Override
  public void accept(SymbolTreeVisitor visitor) {
    visitor.visitMethod(this);
  }
}
