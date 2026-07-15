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
package com.github._1c_syntax.bsl.languageserver.references.model;

import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.Comparator;

import static java.util.Comparator.comparing;

/**
 * Обращение к символу в файле.
 * <p>
 * Поля расположения ({@code uri} и координаты диапазона) хранятся прямо в записи, а не
 * в отдельном объекте {@code Location}: на больших проектах это убирает второй заголовок
 * объекта и лишний прыжок по указателю на каждое из миллионов вхождений. {@code URI}
 * шарится, поэтому размножается только запись обращения. Диапазон/позиция при
 * необходимости строятся на лету ({@link #range()}, {@link #startPosition()}).
 *
 * @param occurrenceType Тип обращения к символу.
 * @param symbol         Символ, к которому происходит обращение.
 * @param uri            URI файла, в котором расположено обращение.
 * @param startLine      Строка начала обращения.
 * @param startCharacter Столбец начала обращения.
 * @param endLine        Строка конца обращения.
 * @param endCharacter   Столбец конца обращения.
 */
public record SymbolOccurrence(
  OccurrenceType occurrenceType,
  Symbol symbol,
  URI uri,
  int startLine,
  int startCharacter,
  int endLine,
  int endCharacter
) implements Comparable<SymbolOccurrence> {

  // Компаратор, вынесенный в константу. Иначе вложенная цепочка (включая
  // сравнение URI и Ranges.compare, а также рекурсивный Symbol-компаратор)
  // пересобиралась на каждый вызов compareTo при навигации по сортированным
  // множествам обращений reference-индекса.
  private static final Comparator<SymbolOccurrence> COMPARATOR =
    comparing(SymbolOccurrence::uri)
      .thenComparing((SymbolOccurrence o1, SymbolOccurrence o2) -> Ranges.compare(
        o1.startLine, o1.startCharacter, o1.endLine, o1.endCharacter,
        o2.startLine, o2.startCharacter, o2.endLine, o2.endCharacter))
      .thenComparing(SymbolOccurrence::occurrenceType)
      .thenComparing(SymbolOccurrence::symbol);

  /**
   * Создать обращение к символу из диапазона.
   *
   * @param occurrenceType Тип обращения.
   * @param symbol         Символ, к которому происходит обращение.
   * @param uri            URI файла.
   * @param range          Диапазон обращения.
   * @return построенное обращение.
   */
  public static SymbolOccurrence of(OccurrenceType occurrenceType, Symbol symbol, URI uri, Range range) {
    var start = range.getStart();
    var end = range.getEnd();
    return new SymbolOccurrence(
      occurrenceType, symbol, uri,
      start.getLine(), start.getCharacter(), end.getLine(), end.getCharacter());
  }

  /**
   * @return Диапазон обращения.
   */
  public Range range() {
    return Ranges.create(startLine, startCharacter, endLine, endCharacter);
  }

  /**
   * @return Позиция начала обращения.
   */
  public Position startPosition() {
    return new Position(startLine, startCharacter);
  }

  @Override
  public int compareTo(@Nullable SymbolOccurrence other) {
    if (other == null) {
      return 1;
    }

    return COMPARATOR.compare(this, other);
  }
}
