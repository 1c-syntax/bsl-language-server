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

import java.util.List;

/**
 * Дескриптор одной сигнатуры (варианта синтаксиса) метода/конструктора.
 *
 * @param parameters             упорядоченный список параметров
 * @param returnTypes            union возвращаемых типов
 * @param bilingualDescription   краткое описание варианта (ru + en)
 * @param syntheticReturnTypeOnly признак синтетической сигнатуры, созданной лишь для
 *                               переноса {@code returnType} метода, у которого в источнике
 *                               не описаны параметры. Пустой {@link #parameters} у такой
 *                               сигнатуры означает «параметры неизвестны», а не «их нет», —
 *                               сигнал для completion не считать метод беспараметровым.
 */
public record SignatureDescriptor(
  List<ParameterDescriptor> parameters,
  TypeSet returnTypes,
  BilingualString bilingualDescription,
  boolean syntheticReturnTypeOnly
) {

  public static final SignatureDescriptor EMPTY = new SignatureDescriptor(
    List.of(), TypeSet.EMPTY, BilingualString.EMPTY);

  public SignatureDescriptor {
    parameters = List.copyOf(parameters);
    if (returnTypes == null) {
      returnTypes = TypeSet.EMPTY;
    }
    if (bilingualDescription == null) {
      bilingualDescription = BilingualString.EMPTY;
    }
  }

  /** Канонический набор полей без флага синтетичности (обычная сигнатура). */
  public SignatureDescriptor(List<ParameterDescriptor> parameters, TypeSet returnTypes,
                             BilingualString bilingualDescription) {
    this(parameters, returnTypes, bilingualDescription, false);
  }

  /** Compat-конструктор: одноязычное {@code description} строкой. */
  public SignatureDescriptor(List<ParameterDescriptor> parameters, TypeSet returnTypes,
                             String description) {
    this(parameters, returnTypes, BilingualString.of(description));
  }

  /** Compat-конструктор: одиночный {@link TypeRef} + одноязычное description. */
  public SignatureDescriptor(List<ParameterDescriptor> parameters, TypeRef returnType,
                             String description) {
    this(parameters, wrapSingle(returnType), BilingualString.of(description));
  }

  /**
   * Синтетическая сигнатура «только returnType»: пустой список параметров не означает,
   * что метод беспараметровый, — параметры в источнике просто не описаны.
   *
   * @see #syntheticReturnTypeOnly()
   */
  public static SignatureDescriptor syntheticReturnType(TypeRef returnType) {
    return new SignatureDescriptor(List.of(), wrapSingle(returnType), BilingualString.EMPTY, true);
  }

  /** Compat-аксессор: primary описание. */
  public String description() {
    return bilingualDescription.primary();
  }

  /** Описание варианта в указанной локали LS (fallback на ru). */
  public String displayDescription(Language language) {
    return bilingualDescription.forLanguage(language);
  }

  /**
   * Удобный аксессор: первый тип из {@link #returnTypes}, либо {@link TypeRef#UNKNOWN}.
   * Используется legacy-кодом, который ожидает один тип возврата на сигнатуру.
   */
  public TypeRef returnType() {
    return returnTypes.refs().stream().findFirst().orElse(TypeRef.UNKNOWN);
  }

  public static SignatureDescriptor of(List<ParameterDescriptor> parameters) {
    return new SignatureDescriptor(parameters, TypeSet.EMPTY, BilingualString.EMPTY);
  }

  private static TypeSet wrapSingle(TypeRef ref) {
    if (ref == null || ref.equals(TypeRef.UNKNOWN)) {
      return TypeSet.EMPTY;
    }
    return TypeSet.of(ref);
  }
}
