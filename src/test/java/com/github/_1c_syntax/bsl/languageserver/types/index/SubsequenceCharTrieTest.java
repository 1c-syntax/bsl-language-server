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
package com.github._1c_syntax.bsl.languageserver.types.index;

import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Юнит-тест посимвольного {@link SubsequenceCharTrie}: обход по подпоследовательности с отсечением
 * эквивалентен плоскому скану (находит ровно надпоследовательности запроса), отсечение не теряет
 * совпадений, удаление и очистка работают.
 */
class SubsequenceCharTrieTest {

  private static final List<String> NAMES = List.of(
    "ПровестиДокумент",
    "ПровестиДокументОтбора",
    "ПроверитьЗаполнение",
    "ОбработкаПроведения",
    "ДобавитьОбработчикКоманды",
    "Очистить",
    "Дата",
    "ДатаНачала"
  );

  @Test
  void subsequenceTraversalEquivalentToFlatScan() {
    // given — trie и плоский список из одних и тех же записей
    var entries = entriesOf(NAMES);
    var trie = trieOf(entries);
    var queries = List.of("прв", "првдок", "док", "обр", "дат", "зап", "xyz", "проведения", "о");

    for (var query : queries) {
      // when — обход trie по подпоследовательности
      var lowerQuery = query.toLowerCase(Locale.ENGLISH);
      var fromTrie = new ArrayList<Entry>();
      trie.forEachSubsequenceMatch(lowerQuery, fromTrie::add);

      // then — ровно те записи, чьё имя — надпоследовательность запроса (плоский эталон)
      var expected = flatSupersequenceMatches(entries, lowerQuery);
      assertThat(fromTrie)
        .as("query=%s", query)
        .containsExactlyInAnyOrderElementsOf(expected);
    }
  }

  @Test
  void pruningDoesNotDropMatchesForLongQuery() {
    // given — запрос длиннее любой ветви короткого имени; отсечение обязано отбросить «Дата»/«Очистить»,
    // но сохранить длинные имена-надпоследовательности
    var entries = entriesOf(NAMES);
    var trie = trieOf(entries);

    // when
    var fromTrie = new ArrayList<Entry>();
    trie.forEachSubsequenceMatch("првдокотб", fromTrie::add);

    // then — найдено только «ПровестиДокументОтбора» (П-ро-в-Док-Отб как подпоследовательность)
    assertThat(fromTrie)
      .extracting(Entry::name)
      .containsExactly("ПровестиДокументОтбора");
  }

  @Test
  void removeDropsEntryFromTraversal() {
    // given
    var entries = entriesOf(List.of("ПровестиДокумент"));
    var trie = trieOf(entries);
    var target = entries.get(0);

    // when — удаляем запись и пересчитываем максимумы
    trie.remove(target.lowerName(), Set.of(target));
    trie.recomputeMaxDepths();

    // then — обход её больше не находит
    var fromTrie = new ArrayList<Entry>();
    trie.forEachSubsequenceMatch("док", fromTrie::add);
    assertThat(fromTrie).isEmpty();
  }

  @Test
  void clearEmptiesTrie() {
    // given
    var entries = entriesOf(NAMES);
    var trie = trieOf(entries);

    // when
    trie.clear();

    // then
    var fromTrie = new ArrayList<Entry>();
    trie.forEachSubsequenceMatch("док", fromTrie::add);
    assertThat(fromTrie).isEmpty();
  }

  private static SubsequenceCharTrie trieOf(List<Entry> entries) {
    var trie = new SubsequenceCharTrie();
    for (var entry : entries) {
      trie.add(entry.lowerName(), entry);
    }
    return trie;
  }

  private static List<Entry> entriesOf(List<String> names) {
    var uri = Absolute.uri("file:///module.bsl");
    var entries = new ArrayList<Entry>(names.size());
    for (var name : names) {
      entries.add(new Entry(
        uri,
        name,
        name.toLowerCase(Locale.ENGLISH),
        SymbolKind.Method,
        new Range(),
        List.of(),
        ""
      ));
    }
    return entries;
  }

  private static List<Entry> flatSupersequenceMatches(List<Entry> entries, String lowerQuery) {
    var matches = new ArrayList<Entry>();
    for (var entry : entries) {
      if (isSubsequence(lowerQuery, entry.lowerName())) {
        matches.add(entry);
      }
    }
    return matches;
  }

  private static boolean isSubsequence(String query, String name) {
    var queryIndex = 0;
    for (var i = 0; i < name.length() && queryIndex < query.length(); i++) {
      if (name.charAt(i) == query.charAt(queryIndex)) {
        queryIndex++;
      }
    }
    return queryIndex == query.length();
  }
}
