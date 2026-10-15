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

import com.github._1c_syntax.bsl.parser.description.VariableDescription;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.NonFinal;

import java.util.Optional;

/**
 * Символ переменной с описанием. Описанных переменных малая доля, поэтому описание хранит
 * только этот подкласс, а не каждая переменная.
 */
@Value
@NonFinal
@ToString(callSuper = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
public class DescribedVariableSymbol extends IntBasedVariableSymbol {

  Optional<VariableDescription> description;

  public DescribedVariableSymbol(
    String name,
    SourceDefinedSymbol scope,
    Optional<SourceDefinedSymbol> parent,
    byte kind,
    boolean export,
    Optional<VariableDescription> description,
    int startLine,
    int startCharacter,
    int endLine,
    int endCharacter,
    int variableNameLine,
    int variableNameStartCharacter,
    int variableNameEndCharacter
  ) {
    super(
      name,
      scope,
      parent,
      kind,
      export,
      startLine,
      startCharacter,
      endLine,
      endCharacter,
      variableNameLine,
      variableNameStartCharacter,
      variableNameEndCharacter
    );
    this.description = description;
  }

  @Override
  public Optional<VariableDescription> getDescription() {
    return description;
  }
}
