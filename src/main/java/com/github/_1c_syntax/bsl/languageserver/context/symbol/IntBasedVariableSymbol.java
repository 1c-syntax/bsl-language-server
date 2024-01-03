/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2024
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
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.eclipse.lsp4j.Range;

import java.util.List;
import java.util.Optional;

/**
 * Реализация символа переменной, хранящая позицию в виде int.
 */
@Value
@NonFinal
@ToString(callSuper = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
public class IntBasedVariableSymbol extends AbstractVariableSymbol {

  @Getter(AccessLevel.NONE)
  int startLine;
  @Getter(AccessLevel.NONE)
  int startCharacter;
  @Getter(AccessLevel.NONE)
  int endLine;
  @Getter(AccessLevel.NONE)
  int endCharacter;

  @Getter(AccessLevel.NONE)
  int variableNameLine;
  @Getter(AccessLevel.NONE)
  int variableNameStartCharacter;
  @Getter(AccessLevel.NONE)
  int variableNameEndCharacter;

  public IntBasedVariableSymbol(
    String name,
    SourceDefinedSymbol scope,
    DocumentContext owner,
    Optional<SourceDefinedSymbol> parent,
    List<SourceDefinedSymbol> children,
    byte kind,
    boolean export,
    Optional<VariableDescription> description,
    int startLine,
    int startCharacter,
    int endLine,
    int endCharacter,
    int variableNameLine,
    int variableNameStartCharacter,
    int variableNameEndCharacter,
    String type
  ) {
    super(name, scope, owner, parent, children, kind, export, description, type);

    this.startLine = startLine;
    this.startCharacter = startCharacter;
    this.endLine = endLine;
    this.endCharacter = endCharacter;
    this.variableNameLine = variableNameLine;
    this.variableNameStartCharacter = variableNameStartCharacter;
    this.variableNameEndCharacter = variableNameEndCharacter;
  }

  @Override
  public Range getRange() {
    return Ranges.create(startLine, startCharacter, endLine, endCharacter);
  }

  @Override
  @EqualsAndHashCode.Include
  public Range getVariableNameRange() {
    return Ranges.create(
      variableNameLine,
      variableNameStartCharacter,
      variableNameLine,
      variableNameEndCharacter
    );
  }

}
