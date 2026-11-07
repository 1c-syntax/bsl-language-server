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
 * Изменение ячеек либо вход/выход их уточняющего расчёта инвалидирует результаты.
 * Вложенные расчёты делят этот кеш; между потоками и запросами он не передаётся.
 * Частичный результат требует совпадения защиты для методов, участвовавших в расчёте,
 * включая отсечённые вызовы, и активности тел, чьи окружения были прочитаны.
 * Состояние общих ячеек и возможность чтения готовых окружений потока также совпадают.
 * Результат, отсечённый ограничением глубины, сохраняет исходный бюджет вычисления.
 */
final class ContextualReturnCache {
  private final Map<ContextualReturnKey, TypeSet> values = new HashMap<>();
  private final Map<Site, List<Entry>> guarded = new HashMap<>();
  /** Найденные соответствия полным запросам, чтобы повторный запрос не перебирал совместимые записи. */
  private final Map<Guard, Result> matchedGuards = new HashMap<>();
  /** Связь тела с объявлением постоянна в пределах запроса и не зависит от приближений ячеек. */
  private final Map<BSLParser.CodeBlockContext, Optional<MethodSymbol>> bodyMethods = new HashMap<>();
  private long revision;

  @Nullable TypeSet get(ContextualReturnKey key) {
    return values.get(key);
  }

  long revision() {
    return revision;
  }

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
                boolean cycleCut, boolean bodyInFlowCut, boolean depthCut) { }

  private record Site(ContextualReturnKey key, Set<BSLParser.CodeBlockContext> otherBodies,
                      boolean rootFlow, boolean cellsComputing) {
    static Site of(Guard guard) {
      return new Site(guard.key(), guard.otherBodies(), guard.visited().size() <= 1, guard.cellsComputing());
    }
  }

  private record Entry(Guard guard, Result result) {
    boolean matches(Guard current) {
      return (!result.depthCut() || guard.depth() == current.depth())
        && sameMembership(result.consulted(), guard.visited(), current.visited())
        && sameMembership(result.cutMethods(), guard.visited(), current.visited())
        && sameMembership(result.consulted(), guard.activeMethods(), current.activeMethods())
        && sameMembership(result.cutMethods(), guard.activeMethods(), current.activeMethods())
        && sameMembership(result.flowBodiesRead(), guard.activeBodies(), current.activeBodies());
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
    var matched = matchedGuards.get(guard);
    if (matched != null) {
      return matched;
    }
    var entries = guarded.get(Site.of(guard));
    if (entries != null) {
      for (var entry : entries) {
        if (entry.matches(guard)) {
          matchedGuards.put(guard, entry.result());
          return entry.result();
        }
      }
    }
    return null;
  }

  void remember(Guard guard, Result result, long startedAtRevision) {
    if (startedAtRevision == revision) {
      guarded.computeIfAbsent(Site.of(guard), ignored -> new ArrayList<>()).add(new Entry(guard, result));
    }
  }
}
