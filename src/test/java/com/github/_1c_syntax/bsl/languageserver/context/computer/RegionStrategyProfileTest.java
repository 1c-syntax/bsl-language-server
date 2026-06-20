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
package com.github._1c_syntax.bsl.languageserver.context.computer;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.RegionSymbol;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Сравнение двух стратегий вычисления регионов: визитор ({@link RegionSymbolComputer}) против
 * точечного {@code Trees.findAllRuleNodes(regionStart, regionEnd)} за один pre-order проход.
 * Запуск: {@code ./gradlew test --tests "*RegionStrategyProfileTest" -Dbsl.profile=true}.
 */
@EnabledIfSystemProperty(named = "bsl.profile", matches = "true")
class RegionStrategyProfileTest extends AbstractServerContextAwareTest {

  @Test
  void profile() throws Exception {
    var root = System.getProperty("bsl.profile.root", "/tmp/ssl_3_2/src/cf");
    var moduleRel = System.getProperty("bsl.profile.module",
      "CommonModules/УправлениеДоступомСлужебный/Ext/Module.bsl");
    var iterations = Integer.getInteger("bsl.profile.iterations", 300);
    var warmup = Integer.getInteger("bsl.profile.warmup", 50);

    var content = Files.readString(Path.of(root, moduleRel));
    var doc = TestUtils.getDocumentContext(content);

    var viaVisitor = new RegionSymbolComputer(doc).compute();
    var viaFindAll = computeViaFindAll(doc);
    log("=".repeat(72));
    log("module: %s (%,d строк)".formatted(moduleRel, content.split("\n", -1).length));
    log("регионов: визитор=%d, findAllRuleNodes=%d".formatted(viaVisitor.size(), viaFindAll.size()));

    // Эквивалентность: один и тот же набор по (имя + диапазоны).
    assertThat(sortKey(viaFindAll))
      .as("findAllRuleNodes даёт те же регионы, что и визитор")
      .isEqualTo(sortKey(viaVisitor));

    var visitorTimes = new ArrayList<Long>();
    var findAllTimes = new ArrayList<Long>();
    for (var i = 0; i < warmup + iterations; i++) {
      var t0 = System.nanoTime();
      var a = new RegionSymbolComputer(doc).compute();
      var dtV = System.nanoTime() - t0;

      var t1 = System.nanoTime();
      var b = computeViaFindAll(doc);
      var dtF = System.nanoTime() - t1;

      if (i >= warmup) {
        visitorTimes.add(dtV);
        findAllTimes.add(dtF);
      }
      if (a.size() != b.size()) {
        throw new IllegalStateException("расхождение размеров");
      }
    }

    printLatency("визитор (RegionSymbolComputer)", visitorTimes);
    printLatency("findAllRuleNodes (один проход) ", findAllTimes);
    log("=".repeat(72));
  }

  /** Регионы через один pre-order проход по узлам regionStart/regionEnd и стек для вложенности. */
  private static List<RegionSymbol> computeViaFindAll(DocumentContext documentContext) {
    var nodes = Trees.findAllRuleNodes(documentContext.getAst(),
      List.of(BSLParser.RULE_regionStart, BSLParser.RULE_regionEnd));

    Deque<BSLParser.RegionStartContext> stack = new ArrayDeque<>();
    List<RegionSymbol> regions = new ArrayList<>();
    for (var node : nodes) {
      if (node instanceof BSLParser.RegionStartContext start) {
        stack.push(start);
      } else if (node instanceof BSLParser.RegionEndContext end && !stack.isEmpty()) {
        var start = stack.pop();
        regions.add(RegionSymbol.builder()
          .owner(documentContext)
          .name(start.regionName().getText().intern())
          .regionNameRange(Ranges.create(start.regionName()))
          .startRange(Ranges.create(start))
          .range(Ranges.create(start, end))
          .endRange(Ranges.create(end))
          .build());
      }
    }
    return regions;
  }

  private static List<String> sortKey(List<RegionSymbol> regions) {
    return regions.stream()
      .map(r -> r.getName() + "|" + r.getRange() + "|" + r.getStartRange() + "|"
        + r.getEndRange() + "|" + r.getRegionNameRange())
      .sorted()
      .toList();
  }

  private static void printLatency(String title, List<Long> nanos) {
    var sorted = new ArrayList<>(nanos);
    sorted.sort(Comparator.naturalOrder());
    var mean = sorted.stream().mapToLong(Long::longValue).average().orElse(0) / 1_000.0;
    log("%s (n=%,d): p50 %7.1f | p90 %7.1f | mean %7.1f  (мкс)".formatted(
      title, sorted.size(), pct(sorted, 50) / 1_000.0, pct(sorted, 90) / 1_000.0, mean));
  }

  private static long pct(List<Long> s, int p) {
    return s.get(Math.max(0, Math.min((int) Math.ceil(p / 100.0 * s.size()) - 1, s.size() - 1)));
  }

  private static void log(String m) {
    System.err.println("[region] " + m);
  }
}
