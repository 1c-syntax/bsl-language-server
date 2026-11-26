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
 * <p>
 * Координаты диапазона в подавляющем большинстве файлов укладываются в {@code short}
 * (строки/столбцы {@literal <=} {@link Short#MAX_VALUE}). Поэтому реализация выбирается
 * фабрикой {@link #of}: для таких файлов создаётся {@link ShortBasedSymbolOccurrence}
 * (четыре {@code short} вместо четырёх {@code int} — на 8 байт меньше на экземпляр), для
 * остальных — {@link IntBasedSymbolOccurrence}. Аксессоры координат всегда возвращают
 * {@code int}, поэтому выбор реализации незаметен для вызывающего кода.
 */
public sealed interface SymbolOccurrence extends Comparable<SymbolOccurrence>
  permits IntBasedSymbolOccurrence, ShortBasedSymbolOccurrence {

  // Компаратор, вынесенный в константу. Иначе вложенная цепочка (включая
  // сравнение URI и Ranges.compare, а также рекурсивный Symbol-компаратор)
  // пересобиралась на каждый вызов compareTo при навигации по сортированным
  // множествам обращений reference-индекса.
  Comparator<SymbolOccurrence> COMPARATOR =
    comparing(SymbolOccurrence::uri)
      .thenComparing((SymbolOccurrence o1, SymbolOccurrence o2) -> Ranges.compare(
        o1.startLine(), o1.startCharacter(), o1.endLine(), o1.endCharacter(),
        o2.startLine(), o2.startCharacter(), o2.endLine(), o2.endCharacter()))
      .thenComparing(SymbolOccurrence::occurrenceType)
      .thenComparing(SymbolOccurrence::symbol);

  /**
   * @return Тип обращения к символу.
   */
  OccurrenceType occurrenceType();

  /**
   * @return Символ, к которому происходит обращение.
   */
  Symbol symbol();

  /**
   * @return URI файла, в котором расположено обращение.
   */
  URI uri();

  /**
   * @return Строка начала обращения.
   */
  int startLine();

  /**
   * @return Столбец начала обращения.
   */
  int startCharacter();

  /**
   * @return Строка конца обращения.
   */
  int endLine();

  /**
   * @return Столбец конца обращения.
   */
  int endCharacter();

  /**
   * Создать обращение к символу из диапазона.
   *
   * @param occurrenceType Тип обращения.
   * @param symbol         Символ, к которому происходит обращение.
   * @param uri            URI файла.
   * @param range          Диапазон обращения.
   * @return построенное обращение.
   */
  static SymbolOccurrence of(OccurrenceType occurrenceType, Symbol symbol, URI uri, Range range) {
    var start = range.getStart();
    var end = range.getEnd();
    return of(
      occurrenceType, symbol, uri,
      start.getLine(), start.getCharacter(), end.getLine(), end.getCharacter());
  }

  /**
   * Создать обращение к символу по координатам диапазона.
   * <p>
   * Если все координаты укладываются в {@code short}, создаётся компактная
   * {@link ShortBasedSymbolOccurrence}, иначе — {@link IntBasedSymbolOccurrence}.
   *
   * @param occurrenceType Тип обращения.
   * @param symbol         Символ, к которому происходит обращение.
   * @param uri            URI файла.
   * @param startLine      Строка начала обращения.
   * @param startCharacter Столбец начала обращения.
   * @param endLine        Строка конца обращения.
   * @param endCharacter   Столбец конца обращения.
   * @return построенное обращение.
   */
  static SymbolOccurrence of(OccurrenceType occurrenceType, Symbol symbol, URI uri,
                             int startLine, int startCharacter, int endLine, int endCharacter) {
    if (fitsShort(startLine) && fitsShort(startCharacter)
      && fitsShort(endLine) && fitsShort(endCharacter)) {
      return new ShortBasedSymbolOccurrence(
        occurrenceType, symbol, uri,
        (short) startLine, (short) startCharacter, (short) endLine, (short) endCharacter);
    }
    return new IntBasedSymbolOccurrence(
      occurrenceType, symbol, uri, startLine, startCharacter, endLine, endCharacter);
  }

  private static boolean fitsShort(int value) {
    return value >= 0 && value <= Short.MAX_VALUE;
  }

  /**
   * @return Диапазон обращения.
   */
  default Range range() {
    return Ranges.create(startLine(), startCharacter(), endLine(), endCharacter());
  }

  /**
   * @return Позиция начала обращения.
   */
  default Position startPosition() {
    return new Position(startLine(), startCharacter());
  }

  @Override
  default int compareTo(@Nullable SymbolOccurrence other) {
    if (other == null) {
      return 1;
    }

    return COMPARATOR.compare(this, other);
  }
}
