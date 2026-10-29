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

import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;

import java.util.Arrays;

/**
 * Тип каждой переменной тела в одной его точке.
 * <p>
 * Неизменяемо, поэтому соседние точки, между которыми ничего не поменялось, ссылаются на
 * одно окружение. За счёт этого хранение растёт по числу мест, где тип меняется, а не по
 * числу операторов, помноженному на число переменных.
 *
 * @param types типы переменных; переменных, о которых ничего не известно, в карте нет.
 */
record FlowEnvironment(TypeSet[] types) {

  /**
   * Пустое окружение нулевой длины — обращение к любой переменной даёт пустой набор.
   * Годится как начальное значение для тела любого размера.
   */
  static final FlowEnvironment EMPTY = new FlowEnvironment(new TypeSet[0]);

  /**
   * Окружение, в котором о переменных ничего не известно.
   *
   * @param size число переменных тела.
   * @return окружение нужного размера.
   */
  static FlowEnvironment blank(int size) {
    var types = new TypeSet[size];
    Arrays.fill(types, TypeSet.EMPTY);
    return new FlowEnvironment(types);
  }

  /** Тип переменной по её номеру; пустой набор, если про неё здесь ничего не известно. */
  TypeSet get(int index) {
    return index < types.length ? types[index] : TypeSet.EMPTY;
  }

  /**
   * Окружение с изменённым типом одной переменной.
   * <p>
   * Если тип не поменялся, возвращается это же окружение: так соседние точки, между
   * которыми ничего не произошло, ссылаются на один объект.
   */
  FlowEnvironment with(int index, TypeSet type, int size) {
    if (type.equals(get(index))) {
      return this;
    }
    var changed = resized(size);
    changed[index] = type;
    return new FlowEnvironment(changed);
  }

  /**
   * Объединение с другим окружением: тип каждой переменной — объединение её типов.
   * Так сходятся пути в точке слияния.
   */
  FlowEnvironment union(FlowEnvironment other) {
    if (types.length == 0) {
      return other;
    }
    if (other.types.length == 0) {
      return this;
    }
    TypeSet[] merged = null;
    for (var i = 0; i < types.length; i++) {
      var united = types[i].union(other.types[i]);
      if (merged == null && !united.equals(types[i])) {
        merged = types.clone();
      }
      if (merged != null) {
        merged[i] = united;
      }
    }
    return merged == null ? this : new FlowEnvironment(merged);
  }

  /** Копия массива нужной длины: пустое окружение растягивается до размера тела. */
  private TypeSet[] resized(int size) {
    if (types.length == size) {
      return types.clone();
    }
    var grown = new TypeSet[size];
    Arrays.fill(grown, TypeSet.EMPTY);
    System.arraycopy(types, 0, grown, 0, types.length);
    return grown;
  }

  @Override
  public boolean equals(Object other) {
    return this == other || (other instanceof FlowEnvironment that && Arrays.equals(types, that.types));
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(types);
  }

  /** По содержимому, а не по ссылке на массив: иначе в отладке смысла не видно. */
  @Override
  public String toString() {
    return "Environment" + Arrays.toString(types);
  }
}
