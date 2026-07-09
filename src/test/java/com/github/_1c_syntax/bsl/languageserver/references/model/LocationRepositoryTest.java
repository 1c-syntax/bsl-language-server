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

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.getDocumentContext;
import static org.assertj.core.api.Assertions.assertThat;

class LocationRepositoryTest extends AbstractServerContextAwareTest {

  @Autowired
  private LocationRepository locationRepository;

  @Test
  void findByPositionMatchesLinearScanForEveryOccurrence() {
    // given — модуль с несколькими использованиями переменных, в т.ч. двумя на одной строке.
    var documentContext = getDocumentContext("""
      Процедура Тест()
          А = 1;
          Б = А + А;
      КонецПроцедуры
      """);
    var uri = documentContext.getUri();
    var occurrences = locationRepository.getSymbolOccurrencesByLocationUri(uri).collect(Collectors.toList());

    // sanity — индекс наполнен.
    assertThat(occurrences).as("вхождения проиндексированы").isNotEmpty();

    // then — для начала каждого вхождения индекс даёт тот же результат, что линейный скан.
    for (var occurrence : occurrences) {
      var position = new Position(occurrence.location().startLine(), occurrence.location().startCharacter());
      assertThat(locationRepository.findByPosition(uri, position))
        .as("парити со сканом в позиции %s", position)
        .isEqualTo(linearScan(uri, position));
    }
  }

  @Test
  void findByPositionDisambiguatesTwoOccurrencesOnSameLine() {
    // given — «Б = А + А;» (А объявлена выше): два использования А на одной
    // строке в разных колонках.
    var documentContext = getDocumentContext("""
      Процедура Тест()
          А = 1;
          Б = А + А;
      КонецПроцедуры
      """);
    var uri = documentContext.getUri();

    // берём два разных вхождения на одной строке «Б = А + А;».
    var line = 2;
    var onLine = locationRepository.getSymbolOccurrencesByLocationUri(uri)
      .filter(o -> o.location().startLine() == line)
      .distinct()
      .collect(Collectors.toList());
    assertThat(onLine).as("минимум два вхождения на строке").hasSizeGreaterThanOrEqualTo(2);

    // then — по колонке каждого возвращается именно оно.
    for (var occurrence : onLine) {
      var position = new Position(line, occurrence.location().startCharacter());
      assertThat(locationRepository.findByPosition(uri, position)).contains(occurrence);
    }
  }

  @Test
  void findByPositionEmptyOffOccurrenceAndAfterDelete() {
    // given
    var documentContext = getDocumentContext("""
      Процедура Тест()
          А = 1;
          Б = А + А;
      КонецПроцедуры
      """);
    var uri = documentContext.getUri();

    // позиция на ключевом слове «Процедура» (0,0) — не вхождение.
    assertThat(locationRepository.findByPosition(uri, new Position(0, 0))).isEmpty();
    // несуществующая строка.
    assertThat(locationRepository.findByPosition(uri, new Position(999, 0))).isEmpty();

    // после delete индекс по URI очищен.
    var anyOccurrence = locationRepository.getSymbolOccurrencesByLocationUri(uri).findFirst().orElseThrow();
    var onOccurrence = new Position(
      anyOccurrence.location().startLine(), anyOccurrence.location().startCharacter());
    assertThat(locationRepository.findByPosition(uri, onOccurrence)).isPresent();

    locationRepository.delete(uri);
    assertThat(locationRepository.findByPosition(uri, onOccurrence)).as("сброс на delete").isEmpty();
  }

  private Optional<SymbolOccurrence> linearScan(URI uri, Position position) {
    return locationRepository.getSymbolOccurrencesByLocationUri(uri)
      .filter(o -> {
        var l = o.location();
        return Ranges.containsPosition(l.startLine(), l.startCharacter(), l.endLine(), l.endCharacter(), position);
      })
      .findAny();
  }
}
