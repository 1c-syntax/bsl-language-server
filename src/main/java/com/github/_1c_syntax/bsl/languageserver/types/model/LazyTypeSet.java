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

import java.util.List;
import java.util.function.Supplier;

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
 * Объект <b>неизменяем</b> и <b>не мемоизирует</b> результат: {@link #get()}
 * каждый раз обращается к актуальному кэшу (никакой устаревшей информации и
 * разделяемого мутабельного состояния). {@code equals}/{@code hashCode} — по
 * {@code key} (значение-идентификатор источника), а не по результату форса,
 * поэтому участие в {@code equals} {@link TypeSet} конечно и стабильно.
 */
public final class LazyTypeSet {

  private final Object key;
  private final Supplier<TypeSet> resolver;

  public LazyTypeSet(Object key, Supplier<TypeSet> resolver) {
    this.key = key;
    this.resolver = resolver;
  }

  /**
   * @return разрешённый тип (один уровень — его собственные вложенные {@code см.}
   *         остаются ленивыми); {@link TypeSet#EMPTY}, если источник не разрешается.
   */
  public TypeSet get() {
    var resolved = resolver.get();
    return resolved == null ? TypeSet.EMPTY : resolved;
  }

  public Object key() {
    return key;
  }

  /**
   * Объединить две ленивые ссылки (при union {@link TypeSet} по одному и тому же
   * ключу-владельцу). Одинаковые источники схлопываются; разные — оборачиваются в
   * ленивую ссылку, форсящую обе.
   */
  public static LazyTypeSet combine(LazyTypeSet a, LazyTypeSet b) {
    if (a.equals(b)) {
      return a;
    }
    return new LazyTypeSet(List.of(a.key, b.key), () -> a.get().union(b.get()));
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof LazyTypeSet other && key.equals(other.key);
  }

  @Override
  public int hashCode() {
    return key.hashCode();
  }

  @Override
  public String toString() {
    return "Lazy(" + key + ")";
  }
}
