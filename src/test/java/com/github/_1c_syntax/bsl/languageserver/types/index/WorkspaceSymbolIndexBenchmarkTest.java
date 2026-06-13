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

/**
 * Ручной бенчмарк поиска {@link WorkspaceSymbolIndex} на реальной конфигурации.
 * <p>
 * Помечен {@link Disabled}, чтобы не грузить CI: прогон наполняет серверный контекст всей
 * конфигурацией ssl_3_2 (~2175 .bsl), что занимает заметное время и память. Запускать вручную:
 * {@code ./gradlew test --tests "*WorkspaceSymbolIndexBenchmark*" -Dversioning.disable=true -Pversion=0.0.0-DEV}
 * (предварительно сняв {@link Disabled} или включив disabled-тесты).
 * <p>
 * Что измеряется: после наполнения контекста печатается число проиндексированных символов и
 * среднее/медиана времени {@link WorkspaceSymbolIndex#search(String, CancelChecker)} по набору
 * запросов (пустой, одна буква, короткий префикс, подпоследовательность, редкий префикс) с
 * прогревом и усреднением по {@link #ITERATIONS} итераций.
 */
@SpringBootTest
@Slf4j
@Disabled("ручной бенчмарк: наполняет контекст всей ssl_3_2, тяжело для CI")
class WorkspaceSymbolIndexBenchmarkTest {

  private static final String PATH_TO_CONFIGURATION = "/home/nfedkin/git_tree/github/1c-syntax/ssl_3_2";

  private static final int WARMUP_ITERATIONS = 20;
  private static final int ITERATIONS = 200;

  private static final CancelChecker NO_CANCEL = () -> {
    // no-op: отмена в бенчмарке не нужна
  };

  private static final List<String> QUERIES = List.of(
    "",        // пустой — вся выдача
    "П",       // одна буква
    "Пров",    // короткий префикс
    "ПрвДок",  // подпоследовательность
    "ОбработкаПроведения" // редкий длинный префикс
  );

  @Autowired
  private ServerContextProvider serverContextProvider;

  @Autowired
  private WorkspaceSymbolIndex index;

  @Test
  void benchmarkSearch() {
    // given — наполняем серверный контекст реальной конфигурацией
    var workspaceUri = Absolute.uri(Path.of(PATH_TO_CONFIGURATION).toUri());
    var workspaceContext = serverContextProvider.addWorkspace(workspaceUri);

    var populateStart = System.nanoTime();
    workspaceContext.populateContext();
    var populateMs = (System.nanoTime() - populateStart) / 1_000_000.0;

    var indexedSymbols = index.search("", NO_CANCEL).size();
    LOGGER.info("WorkspaceSymbolIndex bench: populate={} ms, проиндексировано символов={}",
      String.format("%.0f", populateMs), indexedSymbols);

    // when / then — для каждого запроса прогрев + замер
    for (var query : QUERIES) {
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
