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

import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Порядок, в котором нужно пересчитывать документы, чтобы каждый разбирался один раз.
 * <p>
 * Документ зависит от другого, если его отложенный метод вызывает отложенный метод оттуда.
 * Взаимные зависимости сворачиваются в компоненты сильной связности алгоритмом Тарьяна и
 * выдаются в обратном топологическом порядке: сначала то, от чего зависят, потом
 * зависящие. Компонента из одного документа считается за один заход; компонента из
 * нескольких — это цикл, и её приходится крутить до неподвижной точки.
 */
final class DocumentDependencies {

  private final Map<URI, List<URI>> edges;
  private final Map<URI, Integer> indexes = new HashMap<>();
  private final Map<URI, Integer> lowLinks = new HashMap<>();
  private final Set<URI> onStack = new HashSet<>();
  private final Deque<URI> stack = new ArrayDeque<>();
  private final List<List<URI>> components = new ArrayList<>();
  private int counter;

  private DocumentDependencies(Map<URI, List<URI>> edges) {
    this.edges = edges;
  }

  /**
   * Строит граф зависимостей между документами.
   *
   * @param documents      документы, которые предстоит пересчитать.
   * @param dependenciesOf документы, на значениях которых построен этот.
   * @return граф, готовый выдать порядок обхода.
   */
  static DocumentDependencies of(
    Collection<URI> documents,
    Function<URI, Collection<URI>> dependenciesOf
  ) {
    var pending = new HashSet<>(documents);
    var edges = new HashMap<URI, List<URI>>();
    for (var uri : documents) {
      var targets = new ArrayList<URI>();
      for (var dependency : dependenciesOf.apply(uri)) {
        // Документы вне пересчёта в порядок не входят: их значения уже окончательны.
        if (pending.contains(dependency) && !dependency.equals(uri) && !targets.contains(dependency)) {
          targets.add(dependency);
        }
      }
      edges.put(uri, targets);
    }
    return new DocumentDependencies(edges);
  }

  /**
   * Компоненты сильной связности в обратном топологическом порядке.
   *
   * @return список компонент; в каждой — документы, которые надо считать вместе.
   */
  List<List<URI>> components() {
    if (components.isEmpty()) {
      edges.keySet().forEach(uri -> {
        if (!indexes.containsKey(uri)) {
          visit(uri);
        }
      });
    }
    return components;
  }

  /**
   * Обход Тарьяна: выдаёт компоненту, когда возвращается в её корень.
   *
   * @param uri документ.
   */
  private void visit(URI uri) {
    indexes.put(uri, counter);
    lowLinks.put(uri, counter);
    counter++;
    stack.push(uri);
    onStack.add(uri);

    for (var next : edges.getOrDefault(uri, List.of())) {
      if (!indexes.containsKey(next)) {
        visit(next);
        lowLinks.put(uri, Math.min(lowLinks.get(uri), lowLinks.get(next)));
      } else if (onStack.contains(next)) {
        lowLinks.put(uri, Math.min(lowLinks.get(uri), indexes.get(next)));
      }
    }

    if (lowLinks.get(uri).equals(indexes.get(uri))) {
      var component = new ArrayList<URI>();
      URI member;
      do {
        member = stack.pop();
        onStack.remove(member);
        component.add(member);
      } while (!member.equals(uri));
      components.add(component);
    }
  }
}
