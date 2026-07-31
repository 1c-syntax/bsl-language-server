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
package com.github._1c_syntax.bsl.languageserver.types.registry;

import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.types.ValueType;
import com.github._1c_syntax.bsl.types.ValueTypeDescription;
import lombok.experimental.UtilityClass;

/**
 * Перевод описания типа значения из mdclasses ({@link ValueTypeDescription}) в
 * {@link TypeSet} реестра. Общий код для всех источников конфигурационных членов:
 * реквизиты объектов, общие реквизиты, реквизиты формы.
 */
@UtilityClass
public class ValueTypes {

  /**
   * Набор {@link TypeRef} для описания типа значения (union для composite-типа
   * из нескольких {@code v8:Type}). Нерезолвящиеся типы отбрасываются;
   * пустой результат — {@link TypeSet#EMPTY}.
   *
   * @param typeRegistry реестр типов, в котором ищутся имена.
   * @param valueType    описание типа значения из метаданных.
   * @return типы значения.
   */
  public static TypeSet resolve(TypeRegistry typeRegistry, ValueTypeDescription valueType) {
    if (valueType.isEmpty()) {
      return TypeSet.EMPTY;
    }
    var result = TypeSet.EMPTY;
    for (var type : valueType.getTypes()) {
      result = result.union(typeRegistry.resolveSet(nameOf(type)));
    }
    return result;
  }

  /**
   * Имя типа значения, под которым он известен реестру. Примитивы, V8-типы
   * ({@code ДинамическийСписок}, {@code ХранилищеЗначения}) и конфигурационные
   * ссылки ({@code СправочникСсылка.X}) именованы там так же, как в метаданных;
   * за именем определяемого типа стоит его состав.
   *
   * @param valueType тип значения из метаданных.
   * @return имя для поиска в реестре.
   */
  private static String nameOf(ValueType valueType) {
    return valueType.fullName().getRu();
  }
}
