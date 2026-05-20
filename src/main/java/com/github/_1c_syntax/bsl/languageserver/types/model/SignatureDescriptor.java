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

/**
 * Дескриптор одной сигнатуры (варианта синтаксиса) метода.
 * Метод 1С может иметь несколько вариантов синтаксиса — для каждого варианта
 * создаётся отдельный {@code SignatureDescriptor}.
 * <p>
 * Возвращаемое значение хранится как {@link TypeSet} ({@link #returnTypes}),
 * что покрывает случаи union'а ({@code Строка | Число}, {@code СправочникОбъект.X | Неопределено}).
 * Удобный аксессор {@link #returnType()} возвращает первый ref для legacy-кода,
 * который рассчитывает на одиночный тип.
 *
 * @param parameters  упорядоченный список параметров
 * @param returnTypes union возвращаемых типов ({@link TypeSet#EMPTY} для процедуры или если неизвестно)
 * @param description краткое описание варианта (может быть пустым)
 */
public record SignatureDescriptor(List<ParameterDescriptor> parameters, TypeSet returnTypes, String description) {

  public static final SignatureDescriptor EMPTY = new SignatureDescriptor(List.of(), TypeSet.EMPTY, "");

  public SignatureDescriptor {
    parameters = List.copyOf(parameters);
    if (returnTypes == null) {
      returnTypes = TypeSet.EMPTY;
    }
  }

  /**
   * Совместимый конструктор с одиночным {@link TypeRef}. {@link TypeRef#UNKNOWN}
   * трактуется как «без возвращаемого значения» ({@link TypeSet#EMPTY}).
   */
  public SignatureDescriptor(List<ParameterDescriptor> parameters, TypeRef returnType, String description) {
    this(parameters, wrapSingle(returnType), description);
  }

  /**
   * Удобный аксессор: первый тип из {@link #returnTypes}, либо {@link TypeRef#UNKNOWN}.
   * Используется legacy-кодом, который ожидает один тип возврата на сигнатуру.
   */
  public TypeRef returnType() {
    return returnTypes.refs().stream().findFirst().orElse(TypeRef.UNKNOWN);
  }

  public static SignatureDescriptor of(List<ParameterDescriptor> parameters) {
    return new SignatureDescriptor(parameters, TypeSet.EMPTY, "");
  }

  private static TypeSet wrapSingle(TypeRef ref) {
    if (ref == null || ref.equals(TypeRef.UNKNOWN)) {
      return TypeSet.EMPTY;
    }
    return TypeSet.of(ref);
  }
}
