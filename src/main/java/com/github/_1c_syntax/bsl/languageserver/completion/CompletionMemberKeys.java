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
package com.github._1c_syntax.bsl.languageserver.completion;

import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicReferenceArray;

/** Ключи имён с различением вида члена и регистром по {@link Locale#ROOT}. */
public final class CompletionMemberKeys {
  private record Entry(MemberKind kind, String name, String value) { }

  private static final AtomicReferenceArray<Entry> KEYS = new AtomicReferenceArray<>(8192);

  private CompletionMemberKeys() { }

  /**
   * Построить ключ вида члена и имени без учёта регистра.
   * <p>
   * Ограниченный кеш удерживает только строки и значения enum. Длинные имена дают тот же
   * результат без сохранения в кеше. Коллизия заменяет одну запись; совпадение имени и вида
   * проверяется полностью. Ни один ключ не связан с состоянием рабочей области.
   *
   * @param kind вид члена.
   * @param name исходное имя.
   * @return вид члена, двоеточие и имя в нижнем регистре по {@link Locale#ROOT}.
   */
  public static String of(MemberKind kind, String name) {
    if (name.length() > 256) {
      return normalize(kind, name);
    }
    var hash = 31 * name.hashCode() + (kind == null ? 0 : kind.ordinal());
    var index = (hash ^ (hash >>> 16)) & (KEYS.length() - 1);
    var cached = KEYS.get(index);
    if (cached != null && cached.kind() == kind && cached.name().equals(name)) {
      return cached.value();
    }
    var normalized = normalize(kind, name);
    KEYS.set(index, new Entry(kind, name, normalized));
    return normalized;
  }

  private static String normalize(MemberKind kind, String name) {
    return kind + ":" + name.toLowerCase(Locale.ROOT);
  }
}
