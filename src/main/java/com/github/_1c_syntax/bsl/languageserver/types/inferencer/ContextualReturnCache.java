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
package com.github._1c_syntax.bsl.languageserver.types.inferencer;

import com.github._1c_syntax.bsl.languageserver.types.index.MethodReturnTypeIndexer.ConfigurationGeneration;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Результаты функций в пределах одного запроса вывода и одной версии приближений.
 * <p>
 * Изменение ячеек либо вход/выход их уточняющего расчёта инвалидирует зависимые результаты.
 * Завершённые значения без чтения ячеек сохраняются с проверкой защиты от повторного входа.
 * Вложенные расчёты делят этот кеш; между потоками и запросами он не передаётся.
 * Частичный результат требует совпадения защиты для методов, участвовавших в расчёте,
 * включая отсечённые вызовы, и активности тел, чьи окружения были прочитаны.
 * Состояние общих ячеек и возможность чтения готовых окружений потока также совпадают.
 * Результат, отсечённый ограничением глубины, сохраняет исходный бюджет вычисления.
 */
final class ContextualReturnCache {
  final @Nullable ConfigurationGeneration returnGeneration;
  private final Map<ContextualReturnKey, TypeSet> values = new HashMap<>();
  private final Map<Site, List<Entry>> guarded = new HashMap<>();
  /** Один завершённый результат на метод и режим, если расчёт не обращался к ячейкам. */
  private @Nullable Map<ContextualReturnKey, Entry> withoutCellInputs;
  /** Найденные соответствия полным запросам, чтобы повторный запрос не перебирал совместимые записи. */
  private final Map<Guard, Result> matchedGuards = new HashMap<>();
  /** Непрозрачные ключи приближений: общий индекс не удерживает через них запрос или AST. */
  private final Map<ContextualReturnKey, List<ApproximationInput>> approximationInputs = new HashMap<>();
  /** Связь тела с объявлением постоянна в пределах запроса и не зависит от приближений ячеек. */
  private final Map<BSLParser.CodeBlockContext, Optional<MethodSymbol>> bodyMethods = new HashMap<>();
  private long revision;
  private long cellReads;

  ContextualReturnCache() {
    this(null);
  }

  ContextualReturnCache(@Nullable ConfigurationGeneration returnGeneration) {
    this.returnGeneration = returnGeneration;
    revision = 0;
  }

  @Nullable TypeSet get(ContextualReturnKey key) {
    var result = values.get(key);
    if (result != null) {
      noteCellRead();
    }
    return result;
  }

  long revision() {
    return revision;
  }

  long cellReads() {
    return cellReads;
  }

  void noteCellRead() {
    // Сюда относятся и чтения готовых значений с неизвестным набором входных ячеек.
    cellReads++;
  }

  Object approximationInputs(Guard guard) {
    var entries = approximationInputs.computeIfAbsent(guard.key(), key -> new ArrayList<>());
    for (var entry : entries) {
      if (entry.guard().equals(guard)) {
        return entry.input();
      }
    }
    var input = new Object();
    entries.add(new ApproximationInput(guard, input));
    return input;
  }

  private record ApproximationInput(Guard guard, Object input) { }

  /** Результат, вычисленный через изменение зависимостей, не становится актуальным задним числом. */
  void remember(ContextualReturnKey key, TypeSet value, long startedAtRevision) {
    if (startedAtRevision == revision) {
      values.put(key, value);
    }
  }

  void invalidate() {
    revision++;
    values.clear();
    guarded.clear();
    matchedGuards.clear();
    approximationInputs.clear();
  }

  /** Защита от повторного входа и остаток глубины на входе в расчёт. */
  record Guard(ContextualReturnKey key, Set<SourceDefinedSymbol> visited,
               Set<BSLParser.CodeBlockContext> activeBodies, Set<MethodSymbol> activeMethods,
               Set<BSLParser.CodeBlockContext> otherBodies, int depth, boolean cellsComputing) {
    Guard(ContextualReturnKey key, Set<SourceDefinedSymbol> visited,
          Set<BSLParser.CodeBlockContext> activeBodies, int depth) {
      this(key, visited, activeBodies, Set.of(), activeBodies, depth, false);
    }
  }

  /** Частичный результат сохраняет признаки отсечения и зависимости для вызывающего расчёта. */
  record Result(TypeSet types, Set<MethodSymbol> consulted, Set<URI> dependencies, Set<MethodSymbol> cutMethods,
                Set<BSLParser.CodeBlockContext> flowBodiesRead,
                boolean cycleCut, boolean bodyInFlowCut, boolean depthCut,
                @Nullable Set<MethodSymbol> missingInputs, boolean incompleteCellInput) {
    Result(TypeSet types, Set<MethodSymbol> consulted, Set<URI> dependencies, Set<MethodSymbol> cutMethods,
           Set<BSLParser.CodeBlockContext> flowBodiesRead,
           boolean cycleCut, boolean bodyInFlowCut, boolean depthCut) {
      this(types, consulted, dependencies, cutMethods, flowBodiesRead, cycleCut, bodyInFlowCut, depthCut, Set.of(),
        false);
    }
  }

  private record Site(ContextualReturnKey key, Set<BSLParser.CodeBlockContext> otherBodies,
                      boolean rootFlow, boolean cellsComputing) {
    static Site of(Guard guard) {
      return new Site(guard.key(), guard.otherBodies(), guard.visited().size() <= 1, guard.cellsComputing());
    }
  }

  private record Entry(Guard guard, Result result) {
    boolean matches(Guard current) {
      return (!result.depthCut() || guard.depth() == current.depth())
        && sameVisited(current)
        && sameActiveMethods(current)
        && sameMembership(result.flowBodiesRead(), guard.activeBodies(), current.activeBodies());
    }

    private boolean sameVisited(Guard current) {
      return sameMembership(result.consulted(), guard.visited(), current.visited())
        && sameMembership(result.cutMethods(), guard.visited(), current.visited());
    }

    private boolean sameActiveMethods(Guard current) {
      return sameMembership(result.consulted(), guard.activeMethods(), current.activeMethods())
        && sameMembership(result.cutMethods(), guard.activeMethods(), current.activeMethods());
    }

    private static <T> boolean sameMembership(Set<T> queries, Set<? super T> previous, Set<? super T> current) {
      for (var query : queries) {
        if (previous.contains(query) != current.contains(query)) {
          return false;
        }
      }
      return true;
    }
  }

  Guard guard(ContextualReturnKey key, InferenceContext context) {
    var activeBodies = context.flowSession.activeBodies();
    Set<MethodSymbol> activeMethods = new HashSet<>();
    Set<BSLParser.CodeBlockContext> otherBodies = new HashSet<>();
    for (var body : activeBodies) {
      var method = bodyMethods.computeIfAbsent(body, ignored -> methodOf(body, context));
      if (method.isPresent()) {
        activeMethods.add(method.orElseThrow());
      } else {
        otherBodies.add(body);
      }
    }
    return new Guard(key, Set.copyOf(context.visited), activeBodies, Set.copyOf(activeMethods), Set.copyOf(otherBodies),
      context.depth, context.flowSession.cellsComputing());
  }

  private static Optional<MethodSymbol> methodOf(BSLParser.CodeBlockContext body, InferenceContext context) {
    BSLParser.SubContext sub = Trees.getAncestorByRuleIndex(body, BSLParser.RULE_sub);
    return sub == null ? Optional.empty() : context.documentContext.getSymbolTreeNoLock().getMethodSymbol(sub);
  }

  @Nullable Result get(Guard guard) {
    return get(guard, false);
  }

  @Nullable Result get(Guard guard, boolean allowIndependent) {
    var matched = matchedGuards.get(guard);
    if (matched != null) {
      noteCellRead();
      return matched;
    }
    var site = Site.of(guard);
    var entries = guarded.get(site);
    if (entries != null) {
      for (var entry : entries) {
        if (entry.matches(guard)) {
          matchedGuards.put(guard, entry.result());
          noteCellRead();
          return entry.result();
        }
      }
    }
    var independent = allowIndependent && withoutCellInputs != null ? withoutCellInputs.get(guard.key()) : null;
    if (independent != null && Site.of(independent.guard()).equals(site) && independent.matches(guard)) {
      return independent.result();
    }
    return null;
  }

  void rememberWithoutCellInputs(Guard guard, Result result) {
    if (withoutCellInputs == null) {
      withoutCellInputs = new HashMap<>();
    }
    withoutCellInputs.put(guard.key(), new Entry(guard, result));
  }

  void remember(Guard guard, Result result, long startedAtRevision) {
    if (startedAtRevision == revision) {
      guarded.computeIfAbsent(Site.of(guard), ignored -> new ArrayList<>()).add(new Entry(guard, result));
    }
  }
}
