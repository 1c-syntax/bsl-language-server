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
package com.github._1c_syntax.bsl.languageserver.types.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * Отложенный («ленивый») тип-декорация для {@link TypeSet}: тип элемента коллекции
 * или поля структуры, заданный через {@code см. Метод}-ссылку на локальную функцию.
 * <p>
 * Зачем: самоссылающиеся структуры ({@code Узел: Массив из см. Узел}) образуют
 * бесконечный тип, который нельзя материализовать в конечное eager-значение.
 * Вместо разворота поддерева хранится ссылка на функцию-источник; реальный тип
 * берётся из её закэшированного возвращаемого значения <b>на момент чтения</b>
 * ({@link #get()}). Авто-комплит разыменовывает выражение под курсором —
 * конечной глубины, — поэтому каждый шаг форсит ровно один уровень, а глубина
 * рекурсии естественно ограничена выражением.
 * <p>
 * Ссылка может объединять несколько источников ({@link #combine}); они хранятся
 * плоским набором по ключам, без вложенности. Поэтому повторное объединение с уже
 * вошедшим источником ничего не меняет, а чтение обращается к каждому источнику
 * один раз, сколько бы раз и в каком порядке их ни объединяли.
 * <p>
 * Объект <b>неизменяем</b> и <b>не мемоизирует</b> результат: {@link #get()}
 * каждый раз обращается к актуальному кэшу (никакой устаревшей информации и
 * разделяемого мутабельного состояния). {@code equals}/{@code hashCode} — по
 * набору ключей (значений-идентификаторов источников), а не по результату форса,
 * поэтому участие в {@code equals} {@link TypeSet} конечно и стабильно.
 */
public final class LazyTypeSet {

  private final Map<Object, Supplier<TypeSet>> sources;

  public LazyTypeSet(Object key, Supplier<TypeSet> resolver) {
    this(Map.of(key, resolver));
  }

  private LazyTypeSet(Map<Object, Supplier<TypeSet>> sources) {
    this.sources = sources;
  }

  /**
   * @return объединение разрешённых типов всех источников (один уровень — их
   *         собственные вложенные {@code см.} остаются ленивыми); {@link TypeSet#EMPTY},
   *         если ни один источник не разрешается.
   */
  public TypeSet get() {
    var acc = TypeSet.EMPTY;
    for (var resolver : sources.values()) {
      var resolved = resolver.get();
      if (resolved != null) {
        acc = acc.union(resolved);
      }
    }
    return acc;
  }

  /**
   * @return ключи источников ссылки (у объединённой — каждого из объединённых).
   */
  public Set<Object> keys() {
    return Collections.unmodifiableSet(sources.keySet());
  }

  /**
   * Применить преобразование к тому, что вернёт каждый источник при чтении. Ключи
   * не меняются, поэтому результат равен исходной ссылке.
   *
   * @param mapper преобразование разрешённого типа.
   * @return ссылка с теми же ключами и преобразованными источниками.
   */
  public LazyTypeSet map(UnaryOperator<TypeSet> mapper) {
    var mapped = new LinkedHashMap<Object, Supplier<TypeSet>>();
    sources.forEach((key, resolver) -> mapped.put(key, () -> {
      var resolved = resolver.get();
      return resolved == null ? null : mapper.apply(resolved);
    }));
    return new LazyTypeSet(Collections.unmodifiableMap(mapped));
  }

  /**
   * Объединить две ленивые ссылки (при union {@link TypeSet} по одному и тому же
   * ключу-владельцу): получается ссылка на источники обеих. Если источники одной
   * уже входят в другую, возвращается та, что их содержит.
   */
  public static LazyTypeSet combine(LazyTypeSet a, LazyTypeSet b) {
    if (a.sources.keySet().containsAll(b.sources.keySet())) {
      return a;
    }
    if (b.sources.keySet().containsAll(a.sources.keySet())) {
      return b;
    }
    var merged = new LinkedHashMap<>(a.sources);
    b.sources.forEach(merged::putIfAbsent);
    return new LazyTypeSet(Collections.unmodifiableMap(merged));
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof LazyTypeSet other && sources.keySet().equals(other.sources.keySet());
  }

  @Override
  public int hashCode() {
    return sources.keySet().hashCode();
  }

  @Override
  public String toString() {
    return sources.keySet().stream()
      .map(String::valueOf)
      .collect(Collectors.joining(", ", "Lazy(", ")"));
  }
}
