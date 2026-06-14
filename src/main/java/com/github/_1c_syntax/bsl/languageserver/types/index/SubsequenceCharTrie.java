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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Посимвольный (character-level) trie над lowercase-именами записей индекса для поиска
 * совпадений по подпоследовательности и непрерывной подстроке РЕКУРСИВНЫМ обходом дерева с
 * отсечением — вместо линейного скана всех имён.
 * <p>
 * <b>Статус: исследованная, но отклонённая по замерам альтернатива (вариант 2).</b> Класс не
 * используется в активном пути {@link WorkspaceSymbolIndex}: бенчмарк на двух реальных конфигурациях
 * ({@link WorkspaceSymbolIndexBenchmarkTest}) показал, что этот обход для коротких/неселективных
 * запросов в 3–75 раз МЕДЛЕННЕЕ плоского скана уникальных записей — отсечение по глубине почти не
 * срабатывает, а узлов посимвольного trie на порядок больше числа записей. Класс оставлен вместе с
 * юнит-тестом и сравнительным бенчмарком, чтобы отрицательный результат был воспроизводим.
 * <p>
 * Каждый узел хранит детей {@code Map<Character, узел>} и список записей, чьё полное lowercase-имя
 * заканчивается ровно в этом узле (имена не уникальны — несколько записей могут делить терминальный
 * узел). Общие префиксы имён делят путь от корня, поэтому обход не повторяет совпавший префикс для
 * каждого имени отдельно: в этом и состоит выигрыш над плоским сканом.
 * <p>
 * Каждый узел также помнит максимальную длину имени в своём поддереве ({@code maxNameLengthInSubtree}).
 * Это даёт отсечение обхода по подпоследовательности: если из узла на глубине {@code depth} самое
 * длинное имя в поддереве короче, чем {@code depth + остаток запроса}, ни одно имя в поддереве не
 * сможет добрать оставшиеся символы запроса — вся ветвь отбрасывается без спуска.
 * <p>
 * Структура мутабельна и не потокобезопасна: вызывающая сторона ({@link WorkspaceSymbolIndex})
 * защищает её тем же {@link java.util.concurrent.locks.ReadWriteLock}, что и остальные индексные
 * структуры.
 */
final class SubsequenceCharTrie {

  private final Node root = new Node();

  /**
   * Добавить запись в trie под её полным lowercase-именем.
   * <p>
   * По пути от корня до терминального узла обновляется {@code maxNameLengthInSubtree} каждого узла,
   * чтобы поддерживать отсечение обхода корректным.
   *
   * @param lowerName полное lowercase-имя записи (непустое)
   * @param entry     добавляемая запись
   */
  void add(String lowerName, Entry entry) {
    var nameLength = lowerName.length();
    var node = root;
    node.bumpMaxNameLength(nameLength);
    for (var i = 0; i < nameLength; i++) {
      node = node.children.computeIfAbsent(lowerName.charAt(i), c -> new Node());
      node.bumpMaxNameLength(nameLength);
    }
    // глубина терминального узла равна длине имени; пригодится recompute после удалений
    node.terminalNameLength = nameLength;
    node.entries.add(entry);
  }

  /**
   * Удалить набор записей из терминального узла данного имени; опустевшие узлы при этом не
   * вычищаются, но {@code maxNameLengthInSubtree} полностью пересчитывается с корня, чтобы отсечение
   * обхода не опиралось на устаревшие максимумы после удаления.
   *
   * @param lowerName полное lowercase-имя удаляемых записей
   * @param toRemove  удаляемые записи (сравнение по идентичности через {@code ==})
   */
  void remove(String lowerName, Set<Entry> toRemove) {
    var node = root;
    for (var i = 0; i < lowerName.length(); i++) {
      node = node.children.get(lowerName.charAt(i));
      if (node == null) {
        return;
      }
    }
    node.entries.removeIf(toRemove::contains);
  }

  /**
   * Пересчитать {@code maxNameLengthInSubtree} всех узлов снизу вверх. Вызывается после серии
   * удалений: добавление поддерживает максимумы инкрементально, а удаление их только обнуляет в
   * терминальном узле, поэтому корректность отсечения восстанавливается полным пересчётом.
   */
  void recomputeMaxDepths() {
    recompute(root);
  }

  private static int recompute(Node node) {
    var max = node.entries.isEmpty() ? 0 : node.terminalNameLength;
    for (var child : node.children.values()) {
      max = Math.max(max, recompute(child));
    }
    node.maxNameLengthInSubtree = max;
    return max;
  }

  /**
   * Полностью очистить trie.
   */
  void clear() {
    root.children.clear();
    root.entries.clear();
    root.maxNameLengthInSubtree = 0;
  }

  /**
   * Рекурсивный обход по подпоследовательности с отсечением: для каждой записи, чьё lowercase-имя
   * содержит {@code lowerQuery} как подпоследовательность (символы запроса встречаются в имени по
   * порядку, не обязательно подряд), вызвать {@code consumer}.
   * <p>
   * На каждом ребре с символом {@code c}: если {@code c} совпадает с текущим ожидаемым символом
   * запроса — спускаемся, продвинув позицию запроса (жадно, что для подпоследовательности на
   * отдельном пути оптимально); иначе спускаемся, не продвигая запрос («пропуск» символа имени).
   * Когда позиция запроса достигла его конца, все записи во всём поддереве подходят и отдаются
   * целиком. Ветвь отбрасывается, если {@code maxNameLengthInSubtree} узла не позволяет добрать
   * остаток запроса с текущей глубины.
   *
   * @param lowerQuery lowercase-запрос (непустой)
   * @param consumer   приёмник совпавших записей; одна запись отдаётся не более одного раза
   */
  void forEachSubsequenceMatch(String lowerQuery, Consumer<Entry> consumer) {
    dfs(root, lowerQuery, 0, 0, consumer);
  }

  private static void dfs(Node node, String query, int queryIndex, int depth, Consumer<Entry> consumer) {
    if (queryIndex == query.length()) {
      collectSubtree(node, consumer);
      return;
    }
    var remaining = query.length() - queryIndex;
    if (node.maxNameLengthInSubtree - depth < remaining) {
      return;
    }
    var expected = query.charAt(queryIndex);
    for (var child : node.children.entrySet()) {
      var nextQueryIndex = child.getKey() == expected ? queryIndex + 1 : queryIndex;
      dfs(child.getValue(), query, nextQueryIndex, depth + 1, consumer);
    }
  }

  private static void collectSubtree(Node node, Consumer<Entry> consumer) {
    for (var entry : node.entries) {
      consumer.accept(entry);
    }
    for (var child : node.children.values()) {
      collectSubtree(child, consumer);
    }
  }

  /**
   * Узел посимвольного trie: дети по символу, записи терминала и максимальная длина имени в
   * поддереве для отсечения обхода.
   */
  private static final class Node {
    private final Map<Character, Node> children = new HashMap<>();
    private final List<Entry> entries = new ArrayList<>();
    private int maxNameLengthInSubtree;
    private int terminalNameLength;

    private void bumpMaxNameLength(int nameLength) {
      if (nameLength > maxNameLengthInSubtree) {
        maxNameLengthInSubtree = nameLength;
      }
    }
  }
}
