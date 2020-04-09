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

import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableDescription;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.eclipse.lsp4j.Range;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Value
@Builder
@EqualsAndHashCode(exclude = {"children", "parent"})
@ToString(exclude = {"children", "parent"})
public class VariableSymbol implements Symbol {
  String name;
  Range range;
  Range variableNameRange;

  @Getter
  @Setter
  @Builder.Default
  @NonFinal
  Optional<Symbol> parent = Optional.empty();

  @Builder.Default
  List<Symbol> children = Collections.emptyList();

  VariableKind kind;
  boolean export;
  Optional<VariableDescription> description;

  ArrayList<Usage> usages = new ArrayList<>();

  public void addUsage(Usage usage) {
    usages.add(usage);
  }
}
