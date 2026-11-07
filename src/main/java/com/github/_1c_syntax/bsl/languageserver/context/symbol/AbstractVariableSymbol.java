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
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.Annotation;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.bsl.parser.description.VariableDescription;
import lombok.EqualsAndHashCode;
import lombok.Setter;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.experimental.NonFinal;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Общая реализация символа переменной.
 */
@Value
@NonFinal
@lombok.Builder(builderClassName = "Builder")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"parent"})
public abstract class AbstractVariableSymbol implements VariableSymbol {

  /**
   * Имя переменной.
   */
  @EqualsAndHashCode.Include
  String name;

  /**
   * Область доступности символа. Метод или модуль; файл переменной — её файл
   * (см. {@link #getOwner()}).
   */
  SourceDefinedSymbol scope;

  /**
   * Символ, внутри которого располагается данный символ. Без обёртки в {@link Optional}:
   * переменных на большой конфигурации миллионы, и обёртка на каждую стоила бы десятки мегабайт.
   */
  @NonFinal
  @Nullable
  SourceDefinedSymbol parent;

  /**
   * Тип переменной.
   */
  byte kind;

  /**
   * Признак экспортной переменной.
   */
  boolean export;

  protected AbstractVariableSymbol(
    String name,
    SourceDefinedSymbol scope,
    Optional<SourceDefinedSymbol> parent,
    byte kind,
    boolean export
  ) {
    this.name = name;
    this.scope = scope;
    this.parent = parent.orElse(null);
    this.kind = kind;
    this.export = export;
  }

  /**
   * Файл, в котором располагается переменная, — файл её области. Не хранится: переменных на
   * большой конфигурации миллионы, и поле на каждую стоило бы десятки мегабайт.
   */
  @Override
  @EqualsAndHashCode.Include
  public DocumentContext getOwner() {
    return scope.getOwner();
  }

  /**
   * Описание переменной. Описана малая доля переменных, и описание хранит отдельный подкласс
   * ({@link DescribedVariableSymbol}), а не поле каждой переменной.
   */
  @Override
  public Optional<VariableDescription> getDescription() {
    return Optional.empty();
  }

  @Override
  public Optional<SourceDefinedSymbol> getParent() {
    return Optional.ofNullable(parent);
  }

  @Override
  public void setParent(Optional<SourceDefinedSymbol> parent) {
    this.parent = parent.orElse(null);
  }

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
    return VariableKind.byOrdinal(kind);
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

    @Setter
    @Accessors(fluent = true, chain = true)
    private List<Annotation> annotations = Collections.emptyList();

    @Setter
    @Accessors(fluent = true, chain = true)
    private Optional<VariableDescription> description = Optional.empty();

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

      // Аннотированные переменные редки (поля-желуди ОСени) — отдельный
      // подкласс с ссылкой на список, чтобы не утяжелять остальные символы.
      if (annotations != null && !annotations.isEmpty()) {
        return new AnnotatedVariableSymbol(
          name,
          scope,
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
          variableNameEndCharacter,
          annotations
        );
      }

      // Описанных переменных — малая доля, описание хранит только их подкласс.
      if (description.isPresent()) {
        return new DescribedVariableSymbol(
          name,
          scope,
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
          parent,
          (byte) kind.ordinal(),
          export,
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
          parent,
          (byte) kind.ordinal(),
          export,
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
