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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Неизменяемый, hash-stable union типов.
 * <p>
 * Используется как результат запроса типа выражения/символа: одно выражение
 * может иметь несколько возможных типов (например, переменная присваивается
 * в нескольких ветках разными значениями).
 *
 * @param refs упорядоченное множество {@link TypeRef}
 */
public record TypeSet(Set<TypeRef> refs) {

  public static final TypeSet EMPTY = new TypeSet(Collections.emptySet());

  public TypeSet {
    refs = Collections.unmodifiableSet(new LinkedHashSet<>(refs));
  }

  public static TypeSet of(TypeRef... refs) {
    if (refs.length == 0) {
      return EMPTY;
    }
    return new TypeSet(new LinkedHashSet<>(Arrays.asList(refs)));
  }

  public static TypeSet of(Collection<TypeRef> refs) {
    if (refs.isEmpty()) {
      return EMPTY;
    }
    return new TypeSet(new LinkedHashSet<>(refs));
  }

  public boolean isEmpty() {
    return refs.isEmpty();
  }

  public int size() {
    return refs.size();
  }

  /**
   * @return объединение двух множеств типов
   */
  public TypeSet union(TypeSet other) {
    if (other.isEmpty()) {
      return this;
    }
    if (this.isEmpty()) {
      return other;
    }
    var merged = new LinkedHashSet<>(this.refs);
    merged.addAll(other.refs);
    return new TypeSet(merged);
  }

  public TypeSet add(TypeRef ref) {
    var merged = new LinkedHashSet<>(this.refs);
    merged.add(ref);
    return new TypeSet(merged);
  }
}
