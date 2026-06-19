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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openjdk.jol.info.GraphLayout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * Замер реального прироста retained-памяти reference-индекса при замене структуры
 * {@link SymbolOccurrenceRepository} (#2: ConcurrentSkipListMap → ConcurrentHashMap).
 * <p>
 * В отличие от синтетического JMH-бенчмарка (равномерно ~4 обращения на символ) здесь
 * берётся настоящий индекс реальной конфигурации (по умолчанию SSL 3.2), полностью
 * наполненный {@code populateContext} через {@code ReferenceIndexFiller}. Реальное
 * распределение перекошено — много символов с одним-двумя обращениями, где накладные
 * расходы внутреннего множества важнее всего.
 * <p>
 * Над одними и теми же (общими) объектами {@link Symbol}/{@link SymbolOccurrence}
 * строятся и измеряются три варианта контейнера, поэтому разница totalSize — это
 * чистый overhead структуры (полезная нагрузка одинакова и сокращается в разнице):
 * <ul>
 *   <li><b>A skiplist/skiplist</b> — как в проде ({@link ConcurrentSkipListMap} +
 *       {@link ConcurrentSkipListSet});</li>
 *   <li><b>B hash/newKeySet</b> — вариант #2, как в JMH-бенчмарке
 *       ({@link ConcurrentHashMap} + {@code ConcurrentHashMap.newKeySet()});</li>
 *   <li><b>C hash/skiplist</b> — гибрид: hash снаружи (быстрый O(1) lookup и весь
 *       выигрыш по скорости), но лёгкое сортированное множество внутри (порядок обращений
 *       сохраняется, read-side не меняется).</li>
 * </ul>
 * Запуск:
 * <pre>{@code
 * LANG=C.UTF-8 ./gradlew test \
 *   --tests "*ReferenceIndexMemoryTest" -Dbsl.profile=true \
 *   -Dbsl.profile.root=/tmp/ssl_3_2/src/cf
 * }</pre>
 */
@EnabledIfSystemProperty(named = "bsl.profile", matches = "true")
class ReferenceIndexMemoryTest extends AbstractServerContextAwareTest {

  @Autowired
  private ApplicationContext applicationContext;

  @Test
  @SuppressWarnings("unchecked")
  void measureReferenceIndexFootprint() throws Exception {
    var root = System.getProperty("bsl.profile.root", "/tmp/ssl_3_2/src/cf");

    log("=".repeat(78));
    log("Reference index memory: skip-list → hash (#2) on real configuration");
    log("  configuration root : " + root);

    var populateStart = System.nanoTime();
    initServerContext(Path.of(root), true); // полный populate → реальный reference-индекс
    log("  populateContext    : %,.1f s".formatted((System.nanoTime() - populateStart) / 1e9));

    // Реальный (workspace-scoped) инстанс репозитория текущего workspace.
    var repository = (SymbolOccurrenceRepository) applicationContext
      .getBean("scopedTarget.symbolOccurrenceRepository");
    var field = SymbolOccurrenceRepository.class.getDeclaredField("occurrencesToSymbols");
    field.setAccessible(true);
    var real = (Map<Symbol, Set<SymbolOccurrence>>) field.get(repository);

    // Снимок, чтобы JOL не споткнулся о конкурентную модификацию и чтобы все три
    // варианта строились над идентичным набором одних и тех же объектов.
    var entries = new ArrayList<>(real.entrySet());

    var symbolCount = entries.size();
    var occurrenceCount = entries.stream().mapToLong(e -> e.getValue().size()).sum();
    if (symbolCount == 0) {
      log("  reference index is EMPTY — нечего мерить");
      return;
    }

    // Распределение размеров внутренних множеств — оно и объясняет overhead.
    var sizeBuckets = new TreeMap<String, Integer>();
    var singletons = 0;
    for (var e : entries) {
      var size = e.getValue().size();
      var bucket = size == 1 ? "1" : size == 2 ? "2" : size <= 4 ? "3-4" : size <= 8 ? "5-8" : size <= 32 ? "9-32" : ">32";
      sizeBuckets.merge(bucket, 1, Integer::sum);
      if (size == 1) {
        singletons++;
      }
    }
    log("  symbols (keys)     : %,d".formatted(symbolCount));
    log("  occurrences        : %,d".formatted(occurrenceCount));
    log("  singleton sets     : %,d  (%.1f%% ключей)".formatted(singletons, 100.0 * singletons / symbolCount));
    log("  set-size histogram : " + sizeBuckets);
    log("");

    // B: hash / newKeySet (#2 как в JMH) — над теми же объектами обращений.
    var variantB = new ConcurrentHashMap<Symbol, Set<SymbolOccurrence>>(symbolCount * 2);
    for (var e : entries) {
      Set<SymbolOccurrence> set = ConcurrentHashMap.newKeySet();
      set.addAll(e.getValue());
      variantB.put(e.getKey(), set);
    }

    // C: hash / skiplist-inner (гибрид) — внутреннее множество как в проде (natural ordering).
    var variantC = new ConcurrentHashMap<Symbol, Set<SymbolOccurrence>>(symbolCount * 2);
    for (var e : entries) {
      var set = new ConcurrentSkipListSet<SymbolOccurrence>();
      set.addAll(e.getValue());
      variantC.put(e.getKey(), set);
    }

    // A — измеряем настоящую прод-структуру как есть (truest baseline).
    var sizeA = GraphLayout.parseInstance(real).totalSize();
    var sizeB = GraphLayout.parseInstance(variantB).totalSize();
    var sizeC = GraphLayout.parseInstance(variantC).totalSize();

    log("retained totalSize (вкл. общую полезную нагрузку — она одинакова, поэтому разница = overhead контейнера):");
    report("A  skiplist / skiplist (prod baseline)", sizeA, sizeA);
    report("B  hash / newKeySet      (#2)          ", sizeB, sizeA);
    report("C  hash / skiplist-inner (гибрид)      ", sizeC, sizeA);
    log("");
    log("на один символ-ключ:");
    log("  A %,d B/key | B %,d B/key | C %,d B/key".formatted(
      sizeA / symbolCount, sizeB / symbolCount, sizeC / symbolCount));
    log("=".repeat(78));
  }

  private void report(String title, long size, long baseline) {
    var deltaBytes = size - baseline;
    var deltaPct = 100.0 * deltaBytes / baseline;
    log("  %-40s %,14d B   Δ %+,d B  (%+.1f%%)".formatted(title, size, deltaBytes, deltaPct));
  }

  private static void log(String message) {
    System.err.println("[mem] " + message);
  }
}
