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
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.Annotation;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.CompilerDirectiveKind;
import com.github._1c_syntax.bsl.languageserver.context.symbol.description.MethodDescription;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import edu.umd.cs.findbugs.annotations.Nullable;
import lombok.AccessLevel;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Символ метода или функции.
 * <p>
 * Представляет метод или функцию в модуле BSL с информацией о параметрах,
 * аннотациях, экспортности и вложенных элементах.
 */
@Value
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"children", "parent"})
public class MethodSymbol implements SourceDefinedSymbol, Exportable, Describable, Comparable<MethodSymbol> {
  @EqualsAndHashCode.Include
  String name;

  @Builder.Default
  SymbolKind symbolKind = SymbolKind.Method;

  @EqualsAndHashCode.Include
  DocumentContext owner;

  @Getter(AccessLevel.PRIVATE)
  int startLine;
  @Getter(AccessLevel.PRIVATE)
  int startCharacter;
  @Getter(AccessLevel.PRIVATE)
  int endLine;
  @Getter(AccessLevel.PRIVATE)
  int endCharacter;

  @Getter(AccessLevel.PRIVATE)
  int subNameLine;
  @Getter(AccessLevel.PRIVATE)
  int subNameStartCharacter;
  @Getter(AccessLevel.PRIVATE)
  int subNameEndCharacter;

  @Getter
  @Setter
  @Builder.Default
  @NonFinal
  Optional<SourceDefinedSymbol> parent = Optional.empty();

  @Builder.Default
  List<SourceDefinedSymbol> children = new ArrayList<>();

  boolean function;
  boolean export;
  Optional<MethodDescription> description;

  boolean deprecated;

  @Builder.Default
  List<ParameterDefinition> parameters = Collections.emptyList();

  @Builder.Default
  Optional<CompilerDirectiveKind> compilerDirectiveKind = Optional.empty();
  @Builder.Default
  List<Annotation> annotations = Collections.emptyList();

  @Override
  public Range getRange() {
    return Ranges.create(startLine, startCharacter, endLine, endCharacter);
  }

  @EqualsAndHashCode.Include
  public Range getSubNameRange() {
    return Ranges.create(subNameLine, subNameStartCharacter, subNameLine, subNameEndCharacter);
  }

  public Optional<RegionSymbol> getRegion() {
    return getParent()
      .filter(RegionSymbol.class::isInstance)
      .map(RegionSymbol.class::cast);
  }

  @Override
  public void accept(SymbolTreeVisitor visitor) {
    visitor.visitMethod(this);
  }

  @Override
  public Range getSelectionRange() {
    return getSubNameRange();
  }

  @Override
  public int compareTo(@Nullable MethodSymbol other) {
    if (other == null) {
      return 1;
    }

    return java.util.Comparator.comparing(MethodSymbol::getName)
      .thenComparing(MethodSymbol::getSymbolKind)
      .thenComparing(MethodSymbol::getOwner)
      .thenComparing(MethodSymbol::getStartLine)
      .thenComparing(MethodSymbol::getStartCharacter)
      .thenComparing(MethodSymbol::getEndLine)
      .thenComparing(MethodSymbol::getEndCharacter)
      .thenComparing(MethodSymbol::getSubNameLine)
      .thenComparing(MethodSymbol::getSubNameStartCharacter)
      .thenComparing(MethodSymbol::getSubNameEndCharacter)
      .compare(this, other);
  }

  public static MethodSymbolBuilder builder() {
    return new MethodSymbolBuilder();
  }

  public static class MethodSymbolBuilder {

    public MethodSymbolBuilder range(Range range) {
      var start = range.getStart();
      var end = range.getEnd();
      startLine = start.getLine();
      startCharacter = start.getCharacter();
      endLine = end.getLine();
      endCharacter = end.getCharacter();

      return this;
    }

    public MethodSymbolBuilder subNameRange(Range range) {
      var start = range.getStart();
      var end = range.getEnd();
      subNameLine = start.getLine();
      subNameStartCharacter = start.getCharacter();
      subNameEndCharacter = end.getCharacter();

      return this;
    }
  }
}
