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

import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.utils.Absolute;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ручной бенчмарк поиска {@link WorkspaceSymbolIndex} на реальных конфигурациях.
 * <p>
 * Помечен {@link Disabled}, чтобы не грузить CI: прогон наполняет серверный контекст целой
 * конфигурацией, что занимает заметное время и память. Запускать вручную:
 * {@code ./gradlew test --tests "*WorkspaceSymbolIndexBenchmark*" -Dversioning.disable=true -Pversion=0.0.0-DEV}
 * (предварительно сняв {@link Disabled} или включив disabled-тесты).
 * <p>
 * Пути к корпусам берутся из переменных окружения {@code BSLLS_BENCH_CORPUS_A} и
 * {@code BSLLS_BENCH_CORPUS_B} (корпус B опционален), чтобы абсолютные пути не попадали в исходники.
 * Без них прогон корпуса пропускается.
 * <p>
 * Что измеряется: после наполнения контекста печатается число проиндексированных символов и
 * среднее/медиана времени {@link WorkspaceSymbolIndex#search(String, CancelChecker)} по набору
 * запросов (пустой, одна буква, короткий префикс, слово из середины имени, подпоследовательность,
 * редкий префикс) с прогревом и усреднением по {@link #ITERATIONS} итераций.
 */
@SpringBootTest
@Slf4j
@Disabled("ручной бенчмарк: наполняет контекст целой конфигурацией, тяжело для CI")
class WorkspaceSymbolIndexBenchmarkTest {

  private static final String CORPUS_A_ENV = "BSLLS_BENCH_CORPUS_A";
  private static final String CORPUS_B_ENV = "BSLLS_BENCH_CORPUS_B";

  private static final int WARMUP_ITERATIONS = 20;
  private static final int ITERATIONS = 200;

  private static final CancelChecker NO_CANCEL = () -> {
    // no-op: отмена в бенчмарке не нужна
  };

  private static final List<String> QUERIES = List.of(
    "",        // пустой — вся выдача
    "П",       // одна буква
    "Пров",    // короткий префикс
    "Документ", // слово из середины имени — ключевой кейс word-start trie
    "ПрвДок",  // подпоследовательность
    "ОбработкаПроведения" // редкий длинный префикс
  );

  /**
   * Запросы для изолированного сравнения subsequence-перечисления (плоский скан vs trie-обход).
   * Однобуквенный «П» — самый тяжёлый случай (огромное поддерево), «ПрвДок» — ключевая
   * подпоследовательность варианта 2, длинный запрос проверяет эффект отсечения.
   */
  private static final List<String> SUBSEQUENCE_QUERIES = List.of(
    "П",
    "Пров",
    "Документ",
    "ПрвДок",
    "ОбработкаПроведения"
  );

  @Autowired
  private ServerContextProvider serverContextProvider;

  @Autowired
  private WorkspaceSymbolIndex index;

  @Test
  void benchmarkSearch() {
    // given — корпуса берём из окружения, чтобы абсолютные пути не лежали в исходниках
    var corpusA = System.getenv(CORPUS_A_ENV);
    var corpusB = System.getenv(CORPUS_B_ENV);

    // when / then — каждый заданный корпус прогоняется отдельно
    if (corpusA != null && !corpusA.isBlank()) {
      benchmarkCorpus("A", corpusA);
    } else {
      LOGGER.warn("корпус A не задан ({}), прогон пропущен", CORPUS_A_ENV);
    }
    if (corpusB != null && !corpusB.isBlank()) {
      benchmarkCorpus("B", corpusB);
    } else {
      LOGGER.warn("корпус B не задан ({}), прогон пропущен", CORPUS_B_ENV);
    }
  }

  private void benchmarkCorpus(String label, String path) {
    // given — наполняем серверный контекст конфигурацией корпуса
    var workspaceUri = Absolute.uri(Path.of(path).toUri());
    var workspaceContext = serverContextProvider.addWorkspace(workspaceUri);

    var populateStart = System.nanoTime();
    workspaceContext.populateContext();
    var populateMs = (System.nanoTime() - populateStart) / 1_000_000.0;

    var indexedSymbols = index.search("", NO_CANCEL).size();
    LOGGER.info("корпус {}: populate={} ms, проиндексировано символов={}",
      label, String.format("%.0f", populateMs), indexedSymbols);

    // when / then — для каждого запроса прогрев + замер полного search
    for (var query : QUERIES) {
      benchmarkQuery(query);
    }

    // when / then — изолированный замер subsequence-перечисления: плоский скан (до) vs trie-обход (после)
    benchmarkSubsequenceEnumeration();
  }

  /**
   * Сравнить латентность ПЕРЕЧИСЛЕНИЯ subsequence-кандидатов двумя способами над одними и теми же
   * записями индекса: плоский скан всех имён ({@code до}) и рекурсивный обход
   * {@link SubsequenceCharTrie} с отсечением ({@code после}). Сортировка результата исключена —
   * меряется только сам перебор кандидатов, где и состоит выигрыш варианта 2.
   */
  private void benchmarkSubsequenceEnumeration() {
    var entries = index.search("", NO_CANCEL);
    var trie = new SubsequenceCharTrie();
    for (var entry : entries) {
      trie.add(entry.lowerName(), entry);
    }

    for (var query : SUBSEQUENCE_QUERIES) {
      var lowerQuery = query.toLowerCase(Locale.ENGLISH);

      for (var i = 0; i < WARMUP_ITERATIONS; i++) {
        countFlatScan(entries, lowerQuery);
        countTrie(trie, lowerQuery);
      }

      var matches = countTrie(trie, lowerQuery);

      var flatNs = new ArrayList<Long>(ITERATIONS);
      var trieNs = new ArrayList<Long>(ITERATIONS);
      for (var i = 0; i < ITERATIONS; i++) {
        var t0 = System.nanoTime();
        countFlatScan(entries, lowerQuery);
        flatNs.add(System.nanoTime() - t0);

        var t1 = System.nanoTime();
        countTrie(trie, lowerQuery);
        trieNs.add(System.nanoTime() - t1);
      }

      LOGGER.info("subseq-enum query=\"{}\" кандидатов={} плоский_скан_медиана={} ms trie_медиана={} ms",
        query,
        matches,
        String.format("%.3f", median(flatNs) / 1_000_000.0),
        String.format("%.3f", median(trieNs) / 1_000_000.0));
    }
  }

  private static int countFlatScan(List<Entry> entries, String lowerQuery) {
    var count = 0;
    for (var entry : entries) {
      if (isSubsequence(lowerQuery, entry.lowerName())) {
        count++;
      }
    }
    return count;
  }

  private static int countTrie(SubsequenceCharTrie trie, String lowerQuery) {
    var counter = new int[1];
    trie.forEachSubsequenceMatch(lowerQuery, entry -> counter[0]++);
    return counter[0];
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

  private void benchmarkQuery(String query) {
    // прогрев — JIT + прогрев кэшей строк
    for (var i = 0; i < WARMUP_ITERATIONS; i++) {
      index.search(query, NO_CANCEL);
    }

    var resultSize = index.search(query, NO_CANCEL).size();

    var samplesNs = new ArrayList<Long>(ITERATIONS);
    for (var i = 0; i < ITERATIONS; i++) {
      var start = System.nanoTime();
      index.search(query, NO_CANCEL);
      samplesNs.add(System.nanoTime() - start);
    }

    var averageMs = average(samplesNs) / 1_000_000.0;
    var medianMs = median(samplesNs) / 1_000_000.0;

    LOGGER.info("query=\"{}\" совпадений={} среднее={} ms медиана={} ms ({} итераций)",
      query,
      resultSize,
      String.format("%.3f", averageMs),
      String.format("%.3f", medianMs),
      ITERATIONS);
  }

  private static double average(List<Long> samplesNs) {
    var sum = 0L;
    for (var sample : samplesNs) {
      sum += sample;
    }
    return (double) sum / samplesNs.size();
  }

  private static double median(List<Long> samplesNs) {
    var sorted = new ArrayList<>(samplesNs);
    sorted.sort(Long::compareTo);
    var middle = sorted.size() / 2;
    if (sorted.size() % 2 == 0) {
      return (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
    }
    return sorted.get(middle);
  }
}
