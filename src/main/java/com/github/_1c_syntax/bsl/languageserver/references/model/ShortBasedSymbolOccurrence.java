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

import java.net.URI;

/**
 * Компактная реализация {@link SymbolOccurrence} с координатами диапазона в {@code short}.
 * <p>
 * Четыре {@code short} вместо четырёх {@code int} экономят 8 байт на экземпляр (40 → 32 байта
 * при сжатых указателях), что заметно на миллионах вхождений reference-индекса. Подходит для
 * файлов, где строки и столбцы укладываются в {@code short} ({@literal <=} {@link Short#MAX_VALUE});
 * выбор реализации делает фабрика {@link SymbolOccurrence#of}.
 * <p>
 * Тип обращения отдельным полем не хранится, а закодирован знаком строки начала: у определения
 * в {@link #typedStartLine} лежит побитовое дополнение строки, у ссылки — сама строка. Координаты
 * неотрицательные, поэтому знак свободен. С компактными заголовками объектов это убирает
 * последнюю ссылку сверх 16 байт полей, и вхождение занимает 24 байта вместо 32; с обычными
 * заголовками размер прежний.
 * <p>
 * Компоненты хранятся как {@code short}, но аксессоры {@link #startLine()} и прочие расширяют их
 * до {@code int} по контракту {@link SymbolOccurrence}.
 *
 * @param symbol              Символ, к которому происходит обращение.
 * @param uri                 URI файла, в котором расположено обращение.
 * @param typedStartLine      Строка начала обращения, у определения — её побитовое дополнение.
 * @param startCharacterShort Столбец начала обращения.
 * @param endLineShort        Строка конца обращения.
 * @param endCharacterShort   Столбец конца обращения.
 */
public record ShortBasedSymbolOccurrence(
  Symbol symbol,
  URI uri,
  short typedStartLine,
  short startCharacterShort,
  short endLineShort,
  short endCharacterShort
) implements SymbolOccurrence {

  /**
   * Создать обращение по типу и координатам, укладывающимся в {@code short}.
   *
   * @param occurrenceType Тип обращения к символу.
   * @param symbol         Символ, к которому происходит обращение.
   * @param uri            URI файла.
   * @param startLine      Строка начала обращения.
   * @param startCharacter Столбец начала обращения.
   * @param endLine        Строка конца обращения.
   * @param endCharacter   Столбец конца обращения.
   */
  public ShortBasedSymbolOccurrence(OccurrenceType occurrenceType, Symbol symbol, URI uri,
                                    short startLine, short startCharacter, short endLine, short endCharacter) {
    this(symbol, uri,
      occurrenceType == OccurrenceType.DEFINITION ? (short) ~startLine : startLine,
      startCharacter, endLine, endCharacter);
  }

  @Override
  public OccurrenceType occurrenceType() {
    return typedStartLine < 0 ? OccurrenceType.DEFINITION : OccurrenceType.REFERENCE;
  }

  @Override
  public int startLine() {
    return typedStartLine < 0 ? ~typedStartLine : typedStartLine;
  }

  @Override
  public int startCharacter() {
    return startCharacterShort;
  }

  @Override
  public int endLine() {
    return endLineShort;
  }

  @Override
  public int endCharacter() {
    return endCharacterShort;
  }
}
