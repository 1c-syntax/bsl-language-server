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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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

  /**
   * Однословные запросы — прежний путь (префикс + полный fuzzy-скан для не-префиксных).
   */
  private static final List<String> SINGLE_FRAGMENT_QUERIES = List.of(
    "",        // пустой — вся выдача
    "П",       // одна буква
    "Пров",    // короткий префикс
    "Документ", // слово из середины имени — ключевой кейс word-start trie
    "првдок",  // один токен в нижнем регистре — подпоследовательность через скан
    "ОбработкаПроведения" // редкий длинный префикс
  );

  /**
   * Многословные camel-hump запросы — быстрый путь варианта 3 (пересечение по фрагментам, без скана).
   */
  private static final List<String> MULTI_FRAGMENT_QUERIES = List.of(
    "ПрДок",   // 2 фрагмента-аббревиатуры
    "ПолСсыл", // 2 фрагмента-аббревиатуры
    "ПолучитьСсылкуОбъекта" // реалистичный 3-словный запрос
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

    // если ни один корпус не задан — пропускаем чисто, чтобы прогон не падал
    Assumptions.assumeTrue(
      (corpusA != null && !corpusA.isBlank()) || (corpusB != null && !corpusB.isBlank()),
      "ни один корпус не задан (" + CORPUS_A_ENV + "/" + CORPUS_B_ENV + "), бенчмарк пропущен");

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

    // when / then — однословные запросы (прежний путь со сканом для не-префиксных)
    LOGGER.info("корпус {}: однословные запросы (прежний путь)", label);
    for (var query : SINGLE_FRAGMENT_QUERIES) {
      benchmarkQuery(query);
    }

    // when / then — многословные camel-hump запросы (быстрый путь варианта 3, пересечение без скана)
    LOGGER.info("корпус {}: многословные camel-hump запросы (вариант 3, без полного скана)", label);
    for (var query : MULTI_FRAGMENT_QUERIES) {
      benchmarkQuery(query);
    }
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
    var p95Ms = percentile(samplesNs, 95) / 1_000_000.0;

    LOGGER.info("query=\"{}\" совпадений={} среднее={} ms медиана={} ms p95={} ms ({} итераций)",
      query,
      resultSize,
      String.format("%.3f", averageMs),
      String.format("%.3f", medianMs),
      String.format("%.3f", p95Ms),
      ITERATIONS);
  }

  private static double percentile(List<Long> samplesNs, int percentile) {
    var sorted = new ArrayList<>(samplesNs);
    sorted.sort(Long::compareTo);
    var rank = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
    var index = Math.max(0, Math.min(sorted.size() - 1, rank));
    return sorted.get(index);
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
