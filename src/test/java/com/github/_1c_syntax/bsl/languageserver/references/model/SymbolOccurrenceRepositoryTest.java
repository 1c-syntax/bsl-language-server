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
import org.eclipse.lsp4j.SymbolKind;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Прямые тесты компактного хранилища: одиночное значение, массив, пакетная
 * запись {@code saveAll} и удаление со схлопыванием.
 */
class SymbolOccurrenceRepositoryTest {

  private static final URI DOC_URI = URI.create("file:///doc.bsl");
  private static final Symbol SYMBOL = Symbol.builder()
    .mdoRef("CommonModule.Модуль")
    .moduleType(ModuleType.CommonModule)
    .scopeName("")
    .symbolKind(SymbolKind.Method)
    .symbolName("метод")
    .build();

  private final SymbolOccurrenceRepository repository = new SymbolOccurrenceRepository();

  private static SymbolOccurrence occurrence(int line) {
    return SymbolOccurrence.of(OccurrenceType.REFERENCE, SYMBOL, DOC_URI, Ranges.create(line, 0, line, 5));
  }

  @Test
  void emptyByDefault() {
    assertThat(repository.getAllBySymbol(SYMBOL)).isEmpty();
  }

  @Test
  void singleOccurrence() {
    var occurrence = occurrence(1);
    repository.save(occurrence);
    assertThat(repository.getAllBySymbol(SYMBOL)).containsExactly(occurrence);
  }

  @Test
  void duplicateSaveIsIdempotent() {
    var occurrence = occurrence(1);
    repository.save(occurrence);
    repository.save(occurrence);
    assertThat(repository.getAllBySymbol(SYMBOL)).containsExactly(occurrence);
  }

  @Test
  void arrayKeepsSortedOrderRegardlessOfInsertionOrder() {
    var first = occurrence(1);
    var second = occurrence(2);
    var third = occurrence(3);
    repository.save(third);
    repository.save(first);
    repository.save(second);
    assertThat(repository.getAllBySymbol(SYMBOL)).containsExactly(first, second, third);
  }

  @Test
  void largeCollectionKeepsAllOccurrencesSorted() {
    // крупная коллекция (> 4096 обращений) остаётся отсортированным массивом.
    var count = 4200;
    for (var line = count - 1; line >= 0; line--) {
      repository.save(occurrence(line));
    }
    var all = repository.getAllBySymbol(SYMBOL);
    assertThat(all).hasSize(count);
    assertThat(all).first().isEqualTo(occurrence(0));
    assertThat(all).last().isEqualTo(occurrence(count - 1));
  }

  @Test
  void deleteAllRemovesSingle() {
    var occurrence = occurrence(1);
    repository.save(occurrence);
    repository.deleteAll(Set.of(occurrence));
    assertThat(repository.getAllBySymbol(SYMBOL)).isEmpty();
  }

  @Test
  void deleteFromArrayCollapsesToSingle() {
    var first = occurrence(1);
    var second = occurrence(2);
    repository.save(first);
    repository.save(second);
    repository.deleteAll(Set.of(first));
    assertThat(repository.getAllBySymbol(SYMBOL)).containsExactly(second);
  }

  @Test
  void deleteMiddleFromLargerArray() {
    var first = occurrence(1);
    var second = occurrence(2);
    var third = occurrence(3);
    List.of(first, second, third).forEach(repository::save);
    repository.deleteAll(Set.of(second));
    assertThat(repository.getAllBySymbol(SYMBOL)).containsExactly(first, third);
  }

  @Test
  void deleteUnknownOccurrenceIsNoop() {
    var occurrence = occurrence(1);
    repository.save(occurrence);
    repository.deleteAll(Set.of(occurrence(999)));
    assertThat(repository.getAllBySymbol(SYMBOL)).containsExactly(occurrence);
  }

  @Test
  void saveAllMergesSortedAndDeduplicated() {
    repository.save(occurrence(2));
    // пачка с дубликатом (2) и элементами до/после существующего
    repository.saveAll(SYMBOL, List.of(occurrence(3), occurrence(1), occurrence(2)));
    assertThat(repository.getAllBySymbol(SYMBOL))
      .containsExactly(occurrence(1), occurrence(2), occurrence(3));
  }

  @Test
  void saveAllEmptyIsNoop() {
    repository.saveAll(SYMBOL, List.of());
    assertThat(repository.getAllBySymbol(SYMBOL)).isEmpty();
  }

  @Test
  void saveAllLargeBatchKeepsAllSorted() {
    var count = 4200;
    var batch = new java.util.ArrayList<SymbolOccurrence>();
    for (var line = count - 1; line >= 0; line--) {
      batch.add(occurrence(line));
    }
    repository.saveAll(SYMBOL, batch);
    var all = repository.getAllBySymbol(SYMBOL);
    assertThat(all).hasSize(count);
    assertThat(all).first().isEqualTo(occurrence(0));
    assertThat(all).last().isEqualTo(occurrence(count - 1));
  }

  @Test
  void saveAllMergesIntoLargeCollection() {
    var count = 4200;
    var first = new java.util.ArrayList<SymbolOccurrence>();
    for (var line = 0; line < count; line++) {
      first.add(occurrence(line));
    }
    repository.saveAll(SYMBOL, first); // уже крупный массив
    repository.saveAll(SYMBOL, List.of(occurrence(count), occurrence(count + 1)));
    assertThat(repository.getAllBySymbol(SYMBOL)).hasSize(count + 2);
  }

  @Test
  void deleteFromLargeCollectionShrinksAndEmpties() {
    var count = 4200;
    for (var line = 0; line < count; line++) {
      repository.save(occurrence(line));
    }
    var toDelete = new java.util.ArrayList<SymbolOccurrence>();
    for (var line = 0; line < count; line++) {
      toDelete.add(occurrence(line));
    }
    // удалить часть — остаётся массив
    repository.deleteAll(toDelete.subList(0, 100));
    assertThat(repository.getAllBySymbol(SYMBOL)).hasSize(count - 100);
    // удалить остальное — ключ исчезает
    repository.deleteAll(toDelete);
    assertThat(repository.getAllBySymbol(SYMBOL)).isEmpty();
  }
}
