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

import com.github._1c_syntax.bsl.languageserver.configuration.Language;

import java.util.Objects;

/**
 * Дескриптор формального параметра метода.
 *
 * @param bilingualName        двуязычное имя параметра (ru + en)
 * @param types                допустимые типы параметра
 * @param optional             является ли параметр опциональным
 * @param bilingualDescription двуязычное краткое описание параметра
 * @param defaultValue         текстовое представление значения по умолчанию
 * @param variadic             вариадик-хвост: метод/конструктор принимает в этой
 *                             позиции переменное число значений. Имя содержит
 *                             единственную базу ({@code Значение}), которую
 *                             потребитель нумерует по фактическим аргументам.
 */
public record ParameterDescriptor(
  BilingualString bilingualName,
  TypeSet types,
  boolean optional,
  BilingualString bilingualDescription,
  String defaultValue,
  boolean variadic
) {

  public ParameterDescriptor {
    bilingualName = Objects.requireNonNullElse(bilingualName, BilingualString.EMPTY);
    bilingualDescription = Objects.requireNonNullElse(bilingualDescription, BilingualString.EMPTY);
    defaultValue = Objects.requireNonNullElse(defaultValue, "");
  }

  /** Compat-конструктор без флага {@code variadic} (=false). */
  public ParameterDescriptor(BilingualString bilingualName, TypeSet types, boolean optional,
                             BilingualString bilingualDescription, String defaultValue) {
    this(bilingualName, types, optional, bilingualDescription, defaultValue, false);
  }

  /** Копия дескриптора с проставленным флагом {@code variadic}. */
  public ParameterDescriptor withVariadic(boolean isVariadic) {
    return new ParameterDescriptor(bilingualName, types, optional, bilingualDescription,
      defaultValue, isVariadic);
  }

  /** Compat-конструктор с одноязычными name/description и {@code bilingualName}. */
  public ParameterDescriptor(String name, TypeSet types, boolean optional, String description,
                             String defaultValue, BilingualString bilingualName) {
    this(bilingualName.isEmpty() ? BilingualString.of(name) : bilingualName,
      types, optional, BilingualString.of(description), defaultValue);
  }

  /** Compat-конструктор: одноязычный {@code defaultValue}. */
  public ParameterDescriptor(String name, TypeSet types, boolean optional, String description,
                             String defaultValue) {
    this(BilingualString.of(name), types, optional, BilingualString.of(description), defaultValue);
  }

  /** Compat-конструктор без {@code defaultValue}. */
  public ParameterDescriptor(String name, TypeSet types, boolean optional, String description) {
    this(BilingualString.of(name), types, optional, BilingualString.of(description), "");
  }

  public static ParameterDescriptor of(String name) {
    return new ParameterDescriptor(BilingualString.of(name), TypeSet.EMPTY, false,
      BilingualString.EMPTY, "");
  }

  public static ParameterDescriptor of(String name, boolean optional) {
    return new ParameterDescriptor(BilingualString.of(name), TypeSet.EMPTY, optional,
      BilingualString.EMPTY, "");
  }

  /** Compat-аксессор: primary написание имени. */
  public String name() {
    return bilingualName.primary();
  }

  /** Compat-аксессор: primary описание. */
  public String description() {
    return bilingualDescription.primary();
  }

  /**
   * Сравнивает имя параметра с {@code candidate} без учёта регистра —
   * по обоим написаниям из {@link #bilingualName}.
   */
  public boolean matches(String candidate) {
    return bilingualName.matches(candidate);
  }

  /** Имя параметра для отображения в указанной локали LS. */
  public String displayName(Language language) {
    return bilingualName.forLanguage(language);
  }

  /** Описание параметра для отображения в указанной локали LS. */
  public String displayDescription(Language language) {
    return bilingualDescription.forLanguage(language);
  }
}
