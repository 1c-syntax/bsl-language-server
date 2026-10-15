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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.AbstractMethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Ключ записи о методе.
 * <p>
 * Символ держит всё своё дерево: через родителя — остальные символы документа с их описаниями и
 * параметрами. Документ, перечитанный с тем же текстом, получает новое дерево с равными
 * символами, и запись, ключом которой служил бы сам символ, держала бы прежнее дерево целиком.
 * Поэтому ключ символа {@link AbstractMethodSymbol} — то, по чему такие символы равны, без
 * ссылки на сам символ ({@link Coordinates}); ключ символа другой реализации — он сам
 * ({@link Itself}).
 * <p>
 * Ключи равны тогда и только тогда, когда равны символы, из которых они построены, а хеш ключа —
 * хеш символа: набор ключей обходится в том же порядке, что набор самих символов.
 */
sealed interface MethodKey {

  /**
   * Ключ метода.
   *
   * @param method метод.
   * @return ключ, равный ключам всех символов, равных {@code method}.
   */
  static MethodKey of(MethodSymbol method) {
    if (method instanceof AbstractMethodSymbol) {
      var range = method.getSubNameRange();
      return new Coordinates(method.getClass(), method.getOwner(), method.getName(),
        range.getStart().getLine(), range.getStart().getCharacter(), range.getEnd().getCharacter(),
        method.hashCode());
    }
    return new Itself(method);
  }

  /**
   * То, по чему равны символы {@link AbstractMethodSymbol}: класс символа (символы разных
   * классов не равны, даже с одним именем и местом), документ, имя и место имени.
   *
   * @param type           класс символа.
   * @param owner          документ метода.
   * @param name           имя метода.
   * @param line           строка имени метода.
   * @param startCharacter колонка начала имени метода.
   * @param endCharacter   колонка конца имени метода.
   * @param hash           хеш символа.
   */
  record Coordinates(
    Class<?> type,
    DocumentContext owner,
    String name,
    int line,
    int startCharacter,
    int endCharacter,
    int hash
  ) implements MethodKey {

    @Override
    public boolean equals(@Nullable Object other) {
      return this == other || other instanceof Coordinates key
        && hash == key.hash
        && line == key.line
        && startCharacter == key.startCharacter
        && endCharacter == key.endCharacter
        && type == key.type
        && Objects.equals(name, key.name)
        && Objects.equals(owner, key.owner);
    }

    @Override
    public int hashCode() {
      return hash;
    }
  }

  /**
   * Символ, поля равенства которого ключ не знает: ключом служит он сам.
   *
   * @param method символ.
   */
  record Itself(MethodSymbol method) implements MethodKey {

    @Override
    public int hashCode() {
      return method.hashCode();
    }
  }
}
