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
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jol.info.GraphLayout;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.TimeUnit;

/**
 * Бенчмарк структур reference-индекса ({@link SymbolOccurrenceRepository}) и компараторов
 * {@link Symbol}/{@link SymbolOccurrence}, которые в профиле набора текста дают основной
 * «не-ANTLR» оверхед при перестроении документа на каждый keystroke.
 * <p>
 * Моделируется горячая операция {@code ReferenceIndexFiller.fill}: для редактируемого модуля
 * на каждый keystroke индекс обращений очищается ({@code deleteAll}) и наполняется заново
 * ({@code save}) поверх глобальной (workspace-wide) карты, уже заполненной обращениями из
 * остальных модулей. Дополнительно меряется путь чтения {@code getAllBySymbol} (find-references),
 * где для hash-варианта результат сортируется на чтении.
 * <p>
 * Четыре варианта по двум независимым осям:
 * <ul>
 *   <li><b>BASELINE</b> — как в проде: {@link ConcurrentSkipListMap}/{@link ConcurrentSkipListSet}
 *       + natural ordering (компаратор {@code compareTo} пересобирается на каждый вызов);</li>
 *   <li><b>FIX1_HOISTED_CMP</b> — те же skip-list структуры, но с вынесенными в константу
 *       компараторами (#1);</li>
 *   <li><b>FIX2_HASH</b> — {@link ConcurrentHashMap} + {@code newKeySet}, сравнение только на
 *       чтении через natural ordering (#2);</li>
 *   <li><b>FIX12_BOTH</b> — hash-структуры + вынесенные компараторы (#1+#2).</li>
 * </ul>
 * Скорость — JMH AverageTime; аллокации — {@code -PjmhProfilers=gc} (gc.alloc.rate.norm);
 * retained-память структур — JOL ({@link GraphLayout}) печатается из {@link #setup()}.
 * <p>
 * Запуск только этого бенчмарка с профайлером аллокаций:
 * <pre>{@code
 * ./gradlew jmh -PjmhInclude=SymbolOccurrenceRepositoryBenchmark -PjmhProfilers=gc
 * }</pre>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(value = 1, jvmArgsAppend = {"-Djol.magicFieldOffset=true"})
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
public class SymbolOccurrenceRepositoryBenchmark {

  public enum Variant {
    BASELINE,                  // skiplist/skiplist + natural ordering (per-call cmp) — как в проде
    FIX1_HOISTED_CMP,          // skiplist/skiplist + вынесенные компараторы (#1)
    FIX2_HASH,                 // hash/newKeySet, сортировка на чтении per-call (#2)
    FIX12_BOTH,                // hash/newKeySet, сортировка на чтении вынес. (#1+#2)
    HYBRID_HASH_SLINNER,       // hash снаружи + skiplist-множество внутри (per-call inner cmp)
    HYBRID_HASH_SLINNER_FIX1   // hash снаружи + skiplist-множество внутри + вынесенный inner cmp
  }

  @Param({"BASELINE", "FIX1_HOISTED_CMP", "FIX2_HASH", "FIX12_BOTH",
    "HYBRID_HASH_SLINNER", "HYBRID_HASH_SLINNER_FIX1"})
  private Variant variant;

  /** Число обращений в редактируемом модуле (чистится+наполняется на каждый keystroke). */
  @Param({"2000"})
  private int docOccurrences;

  /** Фон: обращения остальных модулей workspace — задаёт реалистичную глубину глобальной карты. */
  @Param({"20000"})
  private int backgroundOccurrences;

  // --- Вынесенные (hoisted) компараторы — реализация #1. Строятся один раз. ---

  private static final Comparator<Symbol> SYMBOL_CMP = Comparator
    .comparing(Symbol::mdoRef)
    .thenComparing(Symbol::moduleType)
    .thenComparing(Symbol::scopeName)
    .thenComparing(Symbol::symbolKind)
    .thenComparing(Symbol::symbolName);

  private static final Comparator<SymbolOccurrence> OCCURRENCE_CMP = Comparator
    .comparing(SymbolOccurrence::location, Comparator
      .comparing(Location::uri)
      .thenComparing((Location l1, Location l2) -> Ranges.compare(
        l1.startLine(), l1.startCharacter(), l1.endLine(), l1.endCharacter(),
        l2.startLine(), l2.startCharacter(), l2.endLine(), l2.endCharacter())))
    .thenComparing(SymbolOccurrence::occurrenceType)
    .thenComparing(SymbolOccurrence::symbol, SYMBOL_CMP);

  private Repo repo;
  private List<SymbolOccurrence> docOccs;
  private List<Symbol> sampleSymbols;

  @Setup(Level.Trial)
  public void setup() {
    var random = new Random(42);

    var moduleCount = 60;
    var docSymbolCount = Math.max(1, docOccurrences / 4);
    var bgSymbolCount = Math.max(1, backgroundOccurrences / 4);

    var docSymbols = buildSymbols(docSymbolCount, 0, moduleCount);
    var bgSymbols = buildSymbols(bgSymbolCount, docSymbolCount, moduleCount);

    var docUri = URI.create("file:///ws/src/cf/CommonModules/UpravlenieDostupom/Ext/Module.bsl");
    docOccs = buildOccurrences(docOccurrences, docSymbols, docUri, random);

    var bgOccs = new ArrayList<SymbolOccurrence>(backgroundOccurrences);
    for (int i = 0; i < backgroundOccurrences; i++) {
      var uri = URI.create("file:///ws/src/cf/CommonModules/Module" + (i % moduleCount) + "/Ext/Module.bsl");
      var symbol = bgSymbols.get(random.nextInt(bgSymbols.size()));
      bgOccs.add(occurrence(symbol, uri, random.nextInt(50_000)));
    }

    repo = newRepo(variant);
    // Стартовое состояние = «индекс уже наполнен»: фон + обращения редактируемого модуля.
    bgOccs.forEach(repo::save);
    docOccs.forEach(repo::save);

    // Подвыборка символов с обращениями для бенчмарка чтения (find-references).
    sampleSymbols = docSymbols.subList(0, Math.min(200, docSymbols.size()));

    printFootprint(bgOccs);
  }

  /**
   * Горячая операция: переиндексация обращений редактируемого модуля на один keystroke —
   * {@code clearReferences} (deleteAll) + повторное наполнение (save). После операции
   * состояние индекса идентично исходному, поэтому замер устойчив между итерациями.
   */
  @Benchmark
  public int refillDocumentIndex() {
    repo.deleteAll(docOccs);
    for (var occ : docOccs) {
      repo.save(occ);
    }
    return repo.distinctSymbols();
  }

  /**
   * Путь чтения find-references: получить отсортированный список обращений по символу.
   * Для skip-list данные уже отсортированы; для hash сортировка выполняется на чтении.
   */
  @Benchmark
  public long findReferencesSorted() {
    var sum = 0L;
    for (var symbol : sampleSymbols) {
      sum += repo.getAllSorted(symbol).size();
    }
    return sum;
  }

  private List<Symbol> buildSymbols(int count, int idOffset, int moduleCount) {
    var moduleTypes = new ModuleType[]{
      ModuleType.CommonModule, ModuleType.ManagerModule, ModuleType.ObjectModule, ModuleType.FormModule
    };
    var symbols = new ArrayList<Symbol>(count);
    for (int i = 0; i < count; i++) {
      var id = idOffset + i;
      symbols.add(Symbol.builder()
        .mdoRef("CommonModule.Module" + (id % moduleCount))
        .moduleType(moduleTypes[id % moduleTypes.length])
        .scopeName("")
        .symbolKind((id % 2 == 0) ? SymbolKind.Method : SymbolKind.Variable)
        .symbolName("Symbol_" + id)
        .build());
    }
    return symbols;
  }

  private List<SymbolOccurrence> buildOccurrences(int count, List<Symbol> symbols, URI uri, Random random) {
    var occs = new ArrayList<SymbolOccurrence>(count);
    for (int i = 0; i < count; i++) {
      var symbol = symbols.get(random.nextInt(symbols.size()));
      occs.add(occurrence(symbol, uri, i));
    }
    return occs;
  }

  private static SymbolOccurrence occurrence(Symbol symbol, URI uri, int line) {
    return SymbolOccurrence.builder()
      .occurrenceType((line % 3 == 0) ? OccurrenceType.DEFINITION : OccurrenceType.REFERENCE)
      .symbol(symbol)
      .location(new Location(uri, line, 0, line, 10))
      .build();
  }

  private static Repo newRepo(Variant variant) {
    return switch (variant) {
      case BASELINE -> new SkipListRepo(null, null);                 // natural ordering (per-call)
      case FIX1_HOISTED_CMP -> new SkipListRepo(SYMBOL_CMP, OCCURRENCE_CMP);
      case FIX2_HASH -> new HashRepo(null);                          // sort on read, per-call
      case FIX12_BOTH -> new HashRepo(OCCURRENCE_CMP);               // sort on read, hoisted
      case HYBRID_HASH_SLINNER -> new HashSkipListInnerRepo(null);   // hash outer + skiplist inner (per-call)
      case HYBRID_HASH_SLINNER_FIX1 -> new HashSkipListInnerRepo(OCCURRENCE_CMP); // + hoisted inner cmp
    };
  }

  private void printFootprint(List<SymbolOccurrence> bgOccs) {
    try {
      measureFootprint(bgOccs);
    } catch (Throwable t) {
      // Замер памяти не должен валить замер скорости.
      System.out.printf("%n[footprint] variant=%-16s SKIPPED: %s%n", variant, t);
    }
  }

  private void measureFootprint(List<SymbolOccurrence> bgOccs) {
    var totalSize = GraphLayout.parseInstance(repo.backing()).totalSize();
    // Полезная нагрузка (сами объекты обращений/символов + их строки/URI) одинакова для всех
    // вариантов — её считаем как граф, достижимый от самих обращений. Разница
    // total − payload = накладные расходы контейнера (узлы карты/множеств).
    var allOccs = new ArrayList<SymbolOccurrence>(docOccs.size() + bgOccs.size());
    allOccs.addAll(docOccs);
    allOccs.addAll(bgOccs);
    var payloadSize = GraphLayout.parseInstance(allOccs.toArray()).totalSize();
    System.out.printf(
      "%n[footprint] variant=%-16s docOcc=%d bgOcc=%d distinctSymbols=%d  total=%,d B  container=%,d B  payload=%,d B%n",
      variant, docOccurrences, backgroundOccurrences, repo.distinctSymbols(),
      totalSize, totalSize - payloadSize, payloadSize);
  }

  // ---------- Repository abstraction ----------

  private interface Repo {
    void save(SymbolOccurrence occurrence);

    void deleteAll(Collection<SymbolOccurrence> occurrences);

    List<SymbolOccurrence> getAllSorted(Symbol symbol);

    int distinctSymbols();

    Object backing();
  }

  /** BASELINE / FIX1: отсортированная карта, как в проде. */
  private static final class SkipListRepo implements Repo {
    private final ConcurrentSkipListMap<Symbol, Set<SymbolOccurrence>> map;
    private final Comparator<SymbolOccurrence> innerComparator;

    SkipListRepo(Comparator<Symbol> symbolComparator, Comparator<SymbolOccurrence> occurrenceComparator) {
      this.map = symbolComparator == null
        ? new ConcurrentSkipListMap<>()
        : new ConcurrentSkipListMap<>(symbolComparator);
      this.innerComparator = occurrenceComparator;
    }

    @Override
    public void save(SymbolOccurrence occurrence) {
      map.computeIfAbsent(occurrence.symbol(), s -> innerComparator == null
        ? new ConcurrentSkipListSet<>()
        : new ConcurrentSkipListSet<>(innerComparator)).add(occurrence);
    }

    @Override
    public void deleteAll(Collection<SymbolOccurrence> occurrences) {
      occurrences.forEach(occurrence ->
        map.computeIfPresent(occurrence.symbol(), (s, set) -> {
          set.remove(occurrence);
          return set.isEmpty() ? null : set;
        }));
    }

    @Override
    public List<SymbolOccurrence> getAllSorted(Symbol symbol) {
      return new ArrayList<>(map.getOrDefault(symbol, Collections.emptySet())); // already sorted
    }

    @Override
    public int distinctSymbols() {
      return map.size();
    }

    @Override
    public Object backing() {
      return map;
    }
  }

  /** FIX2 / FIX12: hash-карта; сортировка только на чтении. */
  private static final class HashRepo implements Repo {
    private final ConcurrentHashMap<Symbol, Set<SymbolOccurrence>> map = new ConcurrentHashMap<>();
    private final Comparator<SymbolOccurrence> readComparator;

    HashRepo(Comparator<SymbolOccurrence> readComparator) {
      this.readComparator = readComparator;
    }

    @Override
    public void save(SymbolOccurrence occurrence) {
      map.computeIfAbsent(occurrence.symbol(), s -> ConcurrentHashMap.newKeySet()).add(occurrence);
    }

    @Override
    public void deleteAll(Collection<SymbolOccurrence> occurrences) {
      occurrences.forEach(occurrence ->
        map.computeIfPresent(occurrence.symbol(), (s, set) -> {
          set.remove(occurrence);
          return set.isEmpty() ? null : set;
        }));
    }

    @Override
    public List<SymbolOccurrence> getAllSorted(Symbol symbol) {
      var list = new ArrayList<>(map.getOrDefault(symbol, Collections.emptySet()));
      list.sort(readComparator); // null => natural ordering (per-call compareTo)
      return list;
    }

    @Override
    public int distinctSymbols() {
      return map.size();
    }

    @Override
    public Object backing() {
      return map;
    }
  }

  /**
   * HYBRID: hash снаружи (быстрый O(1) lookup ключа-символа — основной выигрыш по скорости),
   * сортированное {@link ConcurrentSkipListSet} внутри (порядок обращений сохраняется, чтение
   * не сортирует, память легче newKeySet на мелких множествах).
   */
  private static final class HashSkipListInnerRepo implements Repo {
    private final ConcurrentHashMap<Symbol, Set<SymbolOccurrence>> map = new ConcurrentHashMap<>();
    private final Comparator<SymbolOccurrence> innerComparator;

    HashSkipListInnerRepo(Comparator<SymbolOccurrence> innerComparator) {
      this.innerComparator = innerComparator;
    }

    @Override
    public void save(SymbolOccurrence occurrence) {
      map.computeIfAbsent(occurrence.symbol(), s -> innerComparator == null
        ? new ConcurrentSkipListSet<>()
        : new ConcurrentSkipListSet<>(innerComparator)).add(occurrence);
    }

    @Override
    public void deleteAll(Collection<SymbolOccurrence> occurrences) {
      occurrences.forEach(occurrence ->
        map.computeIfPresent(occurrence.symbol(), (s, set) -> {
          set.remove(occurrence);
          return set.isEmpty() ? null : set;
        }));
    }

    @Override
    public List<SymbolOccurrence> getAllSorted(Symbol symbol) {
      return new ArrayList<>(map.getOrDefault(symbol, Collections.emptySet())); // already sorted
    }

    @Override
    public int distinctSymbols() {
      return map.size();
    }

    @Override
    public Object backing() {
      return map;
    }
  }
}
