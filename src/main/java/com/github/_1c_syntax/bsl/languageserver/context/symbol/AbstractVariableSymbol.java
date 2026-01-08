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
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.bsl.parser.description.VariableDescription;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.experimental.NonFinal;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Общая реализация символа переменной.
 */
@Value
@NonFinal
@Builder(builderClassName = "Builder")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"parent"})
public abstract class AbstractVariableSymbol implements VariableSymbol {

  /**
   * Имя переменной.
   */
  @EqualsAndHashCode.Include
  String name;

  /**
   * Область доступности символа. Метод или модуль.
   */
  SourceDefinedSymbol scope;

  /**
   * Файл в котором располагается переменная.
   */
  @EqualsAndHashCode.Include
  DocumentContext owner;

  /**
   * Символ, внутри которого располагается данный символ.
   */
  @Getter
  @Setter
  @NonFinal
  Optional<SourceDefinedSymbol> parent;

  /**
   * Тип переменной.
   */
  byte kind;

  /**
   * Признак экспортной переменной.
   */
  boolean export;

  /**
   * Описание переменной.
   */
  Optional<VariableDescription> description;

  @Override
  public List<SourceDefinedSymbol> getChildren() {
    return Collections.emptyList();
  }

  @Override
  public SymbolKind getSymbolKind() {
    return SymbolKind.Variable;
  }

  @Override
  public VariableKind getKind() {
    return VariableKind.values()[kind];
  }

  @Override
  public void accept(SymbolTreeVisitor visitor) {
    visitor.visitVariable(this);
  }

  @Override
  public Range getSelectionRange() {
    return getVariableNameRange();
  }

  public static class Builder {

    @Setter
    @Accessors(fluent = true, chain = true)
    private VariableKind kind;

    @Setter
    @Accessors(fluent = true, chain = true)
    Optional<SourceDefinedSymbol> parent = Optional.empty();

    private int startLine;
    private int startCharacter;
    private int endLine;
    private int endCharacter;
    private int variableNameLine;
    private int variableNameStartCharacter;
    private int variableNameEndCharacter;

    public Builder range(Range range) {
      var start = range.getStart();
      var end = range.getEnd();
      startLine = start.getLine();
      startCharacter = start.getCharacter();
      endLine = end.getLine();
      endCharacter = end.getCharacter();

      return this;
    }

    public Builder variableNameRange(Range range) {
      var start = range.getStart();
      var end = range.getEnd();
      variableNameLine = start.getLine();
      variableNameStartCharacter = start.getCharacter();
      variableNameEndCharacter = end.getCharacter();

      return this;
    }

    public VariableSymbol build() {

      // Ленивое булево вычисление диапазона переменной
      var shortBased = startLine <= Short.MAX_VALUE
        && endLine <= Short.MAX_VALUE
        && startCharacter <= Short.MAX_VALUE
        && endCharacter <= Short.MAX_VALUE
        && variableNameLine <= Short.MAX_VALUE
        && variableNameStartCharacter <= Short.MAX_VALUE
        && variableNameEndCharacter <= Short.MAX_VALUE;

      if (shortBased) {
        return new ShortBasedVariableSymbol(
          name,
          scope,
          owner,
          parent,
          (byte) kind.ordinal(),
          export,
          description,
          (short) startLine,
          (short) startCharacter,
          (short) endLine,
          (short) endCharacter,
          (short) variableNameLine,
          (short) variableNameStartCharacter,
          (short) variableNameEndCharacter
        );
      } else {
        return new IntBasedVariableSymbol(
          name,
          scope,
          owner,
          parent,
          (byte) kind.ordinal(),
          export,
          description,
          startLine,
          startCharacter,
          endLine,
          endCharacter,
          variableNameLine,
          variableNameStartCharacter,
          variableNameEndCharacter
        );
      }
    }
  }

}
