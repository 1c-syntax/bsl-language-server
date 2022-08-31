/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2022
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
import org.eclipse.lsp4j.Range;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class VariableSymbolBuilder {
  private int startLine;
  private int startCharacter;
  private int endLine;
  private int endCharacter;
  private int variableNameLine;
  private int variableNameStartCharacter;
  private int variableNameEndCharacter;
  private VariableKind kind;
  private String name;
  private SourceDefinedSymbol scope;
  private DocumentContext owner;
  private Optional<SourceDefinedSymbol> parent = Optional.empty();
  private List<SourceDefinedSymbol> children = Collections.emptyList();
  private boolean export;
  private Optional<VariableDescription> description;

  public VariableSymbolBuilder range(Range range) {
    var start = range.getStart();
    var end = range.getEnd();
    startLine = start.getLine();
    startCharacter = start.getCharacter();
    endLine = end.getLine();
    endCharacter =  end.getCharacter();

    return this;
  }

  public VariableSymbolBuilder variableNameRange(Range range) {
    var start = range.getStart();
    var end = range.getEnd();
    variableNameLine = start.getLine();
    variableNameStartCharacter = start.getCharacter();
    variableNameEndCharacter = end.getCharacter();

    return this;
  }

  public VariableSymbolBuilder kind(VariableKind kind) {
    this.kind = kind;
    return this;
  }

  public VariableSymbolBuilder name(String name) {
    this.name = name;
    return this;
  }

  public VariableSymbolBuilder scope(SourceDefinedSymbol scope) {
    this.scope = scope;
    return this;
  }

  public VariableSymbolBuilder owner(DocumentContext owner) {
    this.owner = owner;
    return this;
  }

  public VariableSymbolBuilder parent(Optional<SourceDefinedSymbol> parent) {
    this.parent = parent;
    return this;
  }

  public VariableSymbolBuilder children(List<SourceDefinedSymbol> children) {
    this.children = children;
    return this;
  }

  public VariableSymbolBuilder export(boolean export) {
    this.export = export;
    return this;
  }

  public VariableSymbolBuilder description(Optional<VariableDescription> description) {
    this.description = description;
    return this;
  }

  public VariableSymbol build() {

    if (startLine <= Short.MAX_VALUE
      && endLine <= Short.MAX_VALUE
      && startCharacter <= Short.MAX_VALUE
      && endCharacter <= Short.MAX_VALUE
      && variableNameLine <= Short.MAX_VALUE
      && variableNameStartCharacter <= Short.MAX_VALUE
      && variableNameEndCharacter <= Short.MAX_VALUE) {
        return new ShortBasedVariableSymbol(
          name,
          scope,
          owner,
          (short) startLine,
          (short) startCharacter,
          (short) endLine,
          (short) endCharacter,
          (short) variableNameLine,
          (short) variableNameStartCharacter,
          (short) variableNameEndCharacter,
          parent,
          children,
          (byte) kind.ordinal(),
          export,
          description
        );
      } else {

      return new IntBasedVariableSymbol(
        name,
        scope,
        owner,
        startLine,
        startCharacter,
        endLine,
        endCharacter,
        variableNameLine,
        variableNameStartCharacter,
        variableNameEndCharacter,
        parent,
        children,
        (byte) kind.ordinal(),
        export,
        description
      );
    }
  }

  public VariableSymbol buildInt() {
    return new IntBasedVariableSymbol(
      name,
      scope,
      owner,
      startLine,
      startCharacter,
      endLine,
      endCharacter,
      variableNameLine,
      variableNameStartCharacter,
      variableNameEndCharacter,
      parent,
      children,
      (byte) kind.ordinal(),
      export,
      description
    );
  }
}
