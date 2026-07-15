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
import com.github._1c_syntax.bsl.types.ModuleType;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.SymbolKind;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тесты фабрики {@link SymbolOccurrence#of}: выбор компактной {@code short}-реализации
 * для обычных координат, {@code int}-реализации для координат за пределами {@code short},
 * а также согласованность аксессоров, {@code equals}/{@code hashCode} и {@code compareTo}.
 */
class SymbolOccurrenceTest {

  private static final URI DOC_URI = Absolute.uri("file:///doc.bsl");
  private static final Symbol SYMBOL = Symbol.builder()
    .mdoRef("CommonModule.Модуль")
    .moduleType(ModuleType.CommonModule)
    .scopeName("")
    .symbolKind(SymbolKind.Method)
    .symbolName("метод")
    .build();

  @Test
  void smallCoordinatesProduceShortBasedOccurrence() {
    // when
    var occurrence = SymbolOccurrence.of(OccurrenceType.REFERENCE, SYMBOL, DOC_URI, Ranges.create(10, 4, 10, 9));

    // then
    assertThat(occurrence).isInstanceOf(ShortBasedSymbolOccurrence.class);
    assertThat(occurrence.startLine()).isEqualTo(10);
    assertThat(occurrence.startCharacter()).isEqualTo(4);
    assertThat(occurrence.endLine()).isEqualTo(10);
    assertThat(occurrence.endCharacter()).isEqualTo(9);
    assertThat(occurrence.uri()).isEqualTo(DOC_URI);
    assertThat(occurrence.symbol()).isEqualTo(SYMBOL);
    assertThat(occurrence.occurrenceType()).isEqualTo(OccurrenceType.REFERENCE);
  }

  @Test
  void boundaryValueFitsShort() {
    // when
    var occurrence = SymbolOccurrence.of(OccurrenceType.REFERENCE, SYMBOL, DOC_URI,
      Short.MAX_VALUE, Short.MAX_VALUE, Short.MAX_VALUE, Short.MAX_VALUE);

    // then
    assertThat(occurrence).isInstanceOf(ShortBasedSymbolOccurrence.class);
    assertThat(occurrence.endCharacter()).isEqualTo(Short.MAX_VALUE);
  }

  @Test
  void largeLineFallsBackToIntBased() {
    // given
    var bigLine = Short.MAX_VALUE + 1;

    // when
    var occurrence = SymbolOccurrence.of(OccurrenceType.REFERENCE, SYMBOL, DOC_URI,
      bigLine, 0, bigLine, 5);

    // then
    assertThat(occurrence).isInstanceOf(IntBasedSymbolOccurrence.class);
    assertThat(occurrence.startLine()).isEqualTo(bigLine);
    assertThat(occurrence.endLine()).isEqualTo(bigLine);
  }

  @Test
  void rangeAndStartPositionAreReconstructed() {
    // when
    var occurrence = SymbolOccurrence.of(OccurrenceType.DEFINITION, SYMBOL, DOC_URI, Ranges.create(3, 1, 4, 8));

    // then
    assertThat(occurrence.range()).isEqualTo(Ranges.create(3, 1, 4, 8));
    assertThat(occurrence.startPosition().getLine()).isEqualTo(3);
    assertThat(occurrence.startPosition().getCharacter()).isEqualTo(1);
  }

  @Test
  void equalOccurrencesShareEqualityAndHash() {
    // when
    var first = SymbolOccurrence.of(OccurrenceType.REFERENCE, SYMBOL, DOC_URI, Ranges.create(7, 2, 7, 6));
    var second = SymbolOccurrence.of(OccurrenceType.REFERENCE, SYMBOL, DOC_URI, Ranges.create(7, 2, 7, 6));

    // then
    assertThat(first).isEqualTo(second);
    assertThat(first.hashCode()).isEqualTo(second.hashCode());
    assertThat(first.compareTo(second)).isZero();
  }

  @Test
  void compareToOrdersByRangeConsistentlyAcrossImplementations() {
    // given
    var bigLine = Short.MAX_VALUE + 1;
    var shortBased = SymbolOccurrence.of(OccurrenceType.REFERENCE, SYMBOL, DOC_URI, Ranges.create(10, 0, 10, 3));
    var intBased = SymbolOccurrence.of(OccurrenceType.REFERENCE, SYMBOL, DOC_URI,
      bigLine, 0, bigLine, 3);

    // then
    assertThat(shortBased).isInstanceOf(ShortBasedSymbolOccurrence.class);
    assertThat(intBased).isInstanceOf(IntBasedSymbolOccurrence.class);
    assertThat(shortBased.compareTo(intBased)).isNegative();
    assertThat(intBased.compareTo(shortBased)).isPositive();
  }
}
