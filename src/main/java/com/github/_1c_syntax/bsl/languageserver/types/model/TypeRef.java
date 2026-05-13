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

import java.util.Locale;

/**
 * Лёгкая ссылка-ключ на тип.
 * <p>
 * Идентичность типа определяется парой {@code (kind, qualifiedName)}.
 * Создаётся через {@link com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry#intern(TypeKind, String)}
 * для канонических (интернированных) инстансов — это позволяет хранить
 * информацию о типах в индексе как одну ссылку на JVM.
 *
 * @param kind          категория типа
 * @param qualifiedName каноническое полное имя (например, {@code "Массив"},
 *                      {@code "Справочники.Контрагенты"}, mdoRef для общего модуля)
 */
public record TypeRef(TypeKind kind, String qualifiedName) {

  public static final TypeRef UNKNOWN = new TypeRef(TypeKind.UNKNOWN, "Unknown");
  public static final TypeRef ANY = new TypeRef(TypeKind.ANY, "Any");

  /**
   * Каноническое (регистронезависимое) представление имени, используемое для
   * сравнения в {@link com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry}.
   *
   * @return имя в нижнем регистре по {@link Locale#ROOT}
   */
  public String canonicalKey() {
    return qualifiedName.toLowerCase(Locale.ROOT);
  }

  /**
   * Краткое имя без квалификатора (часть после последней точки), удобно для
   * отображения в hover/completion.
   *
   * @return простое имя
   */
  public String simpleName() {
    var dot = qualifiedName.lastIndexOf('.');
    return dot < 0 ? qualifiedName : qualifiedName.substring(dot + 1);
  }
}
