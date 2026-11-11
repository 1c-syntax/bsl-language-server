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
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Символ региона (области кода).
 * <p>
 * Представляет область кода, ограниченную директивами #Область/#Region и #КонецОбласти/#EndRegion,
 * с поддержкой вложенных регионов и методов. Диапазон области — от начала {@code #Область} до
 * конца {@code #КонецОбласти}.
 * <p>
 * Диапазоны хранятся числами, а {@link Range} собирается при обращении: областей на большой
 * конфигурации сотни тысяч, а четыре диапазона по две позиции стоили каждой почти триста байт.
 */
@Value
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"children", "parent"})
public class RegionSymbol implements SourceDefinedSymbol {
  String name;
  SymbolKind symbolKind;
  @EqualsAndHashCode.Include
  DocumentContext owner;

  @Getter(AccessLevel.NONE)
  int startLine;
  @Getter(AccessLevel.NONE)
  int startCharacter;
  @Getter(AccessLevel.NONE)
  int startEndLine;
  @Getter(AccessLevel.NONE)
  int startEndCharacter;

  @Getter(AccessLevel.NONE)
  int endStartLine;
  @Getter(AccessLevel.NONE)
  int endStartCharacter;
  @Getter(AccessLevel.NONE)
  int endLine;
  @Getter(AccessLevel.NONE)
  int endCharacter;

  @Getter(AccessLevel.NONE)
  @EqualsAndHashCode.Include
  int nameStartLine;
  @Getter(AccessLevel.NONE)
  @EqualsAndHashCode.Include
  int nameStartCharacter;
  @Getter(AccessLevel.NONE)
  @EqualsAndHashCode.Include
  int nameEndLine;
  @Getter(AccessLevel.NONE)
  @EqualsAndHashCode.Include
  int nameEndCharacter;

  @NonFinal
  @Nullable
  SourceDefinedSymbol parent;

  List<SourceDefinedSymbol> children;

  /**
   * @param name            имя области.
   * @param symbolKind      вид символа; по умолчанию {@link SymbolKind#Namespace}.
   * @param owner           документ области.
   * @param startRange      диапазон строки {@code #Область}.
   * @param endRange        диапазон строки {@code #КонецОбласти}.
   * @param regionNameRange диапазон имени области.
   * @param children        вложенные символы; по умолчанию пустой изменяемый список.
   */
  @Builder
  private RegionSymbol(String name, @Nullable SymbolKind symbolKind, DocumentContext owner,
                       Range startRange, Range endRange, Range regionNameRange,
                       @Nullable List<SourceDefinedSymbol> children) {
    this.name = name;
    this.symbolKind = symbolKind == null ? SymbolKind.Namespace : symbolKind;
    this.owner = owner;
    this.startLine = startRange.getStart().getLine();
    this.startCharacter = startRange.getStart().getCharacter();
    this.startEndLine = startRange.getEnd().getLine();
    this.startEndCharacter = startRange.getEnd().getCharacter();
    this.endStartLine = endRange.getStart().getLine();
    this.endStartCharacter = endRange.getStart().getCharacter();
    this.endLine = endRange.getEnd().getLine();
    this.endCharacter = endRange.getEnd().getCharacter();
    this.nameStartLine = regionNameRange.getStart().getLine();
    this.nameStartCharacter = regionNameRange.getStart().getCharacter();
    this.nameEndLine = regionNameRange.getEnd().getLine();
    this.nameEndCharacter = regionNameRange.getEnd().getCharacter();
    this.children = children == null ? new ArrayList<>() : children;
  }

  @Override
  public Range getRange() {
    return Ranges.create(startLine, startCharacter, endLine, endCharacter);
  }

  @Override
  public boolean containsPosition(Position position) {
    return Ranges.containsPosition(startLine, startCharacter, endLine, endCharacter, position);
  }

  /**
   * @return диапазон строки {@code #Область}.
   */
  public Range getStartRange() {
    return Ranges.create(startLine, startCharacter, startEndLine, startEndCharacter);
  }

  /**
   * @return диапазон строки {@code #КонецОбласти}.
   */
  public Range getEndRange() {
    return Ranges.create(endStartLine, endStartCharacter, endLine, endCharacter);
  }

  /**
   * @return диапазон имени области.
   */
  public Range getRegionNameRange() {
    return Ranges.create(nameStartLine, nameStartCharacter, nameEndLine, nameEndCharacter);
  }

  @Override
  public Optional<SourceDefinedSymbol> getParent() {
    return Optional.ofNullable(parent);
  }

  @Override
  public void setParent(Optional<SourceDefinedSymbol> parent) {
    this.parent = parent.orElse(null);
  }

  public List<MethodSymbol> getMethods() {
    return children.stream()
      .filter(MethodSymbol.class::isInstance)
      .map(MethodSymbol.class::cast)
      .collect(Collectors.toList());
  }

  @Override
  public void accept(SymbolTreeVisitor visitor) {
    visitor.visitRegion(this);
  }

  @Override
  public Range getSelectionRange() {
    return getRegionNameRange();
  }
}
