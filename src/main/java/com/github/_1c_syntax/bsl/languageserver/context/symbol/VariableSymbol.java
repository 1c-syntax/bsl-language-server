/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2021
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
import org.eclipse.lsp4j.SymbolKind;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Value
@Builder
@EqualsAndHashCode(exclude = {"children", "parent", "description"})
@ToString(exclude = {"children", "parent"})
public class VariableSymbol implements SourceDefinedSymbol, Exportable, Describable {

  /**
   * Имя переменной.
   */
  String name;

  /**
   * Область доступности символа. Метод или модуль.
   */
  SourceDefinedSymbol scope;

  /**
   * Тип символа. По умолчанию переменная.
   */
  @Builder.Default
  SymbolKind symbolKind = SymbolKind.Variable;

  /**
   * Файл в котором располагается переменная.
   */
  DocumentContext owner;

  Range range;

  Range variableNameRange;

  @Getter
  @Setter
  @Builder.Default
  @NonFinal
  Optional<SourceDefinedSymbol> parent = Optional.empty();

  @Builder.Default
  List<SourceDefinedSymbol> children = Collections.emptyList();

  /**
   * Тип переменной.
   */
  VariableKind kind;

  /**
   * Признак экспортной переменной.
   */
  boolean export;

  /**
   * Описание переменной.
   */
  Optional<VariableDescription> description;

  @Override
  public void accept(SymbolTreeVisitor visitor) {
    visitor.visitVariable(this);
  }

  @Override
  public Range getSelectionRange() {
    return getVariableNameRange();
  }

}
