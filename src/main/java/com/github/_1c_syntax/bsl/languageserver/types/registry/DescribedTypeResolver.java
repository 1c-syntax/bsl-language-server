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

import com.github._1c_syntax.bsl.languageserver.context.symbol.ParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.parser.description.TypeDescription;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Разрешает типы, объявленные в документирующем комментарии метода: типы параметров
 * (секция {@code // Параметры:}) и тип возвращаемого значения
 * (секция {@code // Возвращаемое значение:}).
 * <p>
 * Имя типа берётся до первого разделителя, а всё, что за ним, считается пояснением:
 * {@code Массив из Строка}, {@code Массив<Строка>} и {@code Массив[Строка]} — это
 * {@code Массив}.
 */
@Component
@RequiredArgsConstructor
public class DescribedTypeResolver {

  /** Начало пояснения после имени типа: «Массив из …», «Массив&lt;…&gt;», «Массив[…]». */
  private static final Pattern TYPE_NAME_TAIL = Pattern.compile("[\\s<\\[]");

  /** Делим имя надвое: само имя и всё, что за ним. */
  private static final int TYPE_NAME_AND_TAIL = 2;

  private final TypeRegistry typeRegistry;

  /**
   * Типы параметра, объявленные в описании метода.
   *
   * @param parameter параметр метода.
   * @return объявленные типы параметра; {@link TypeSet#EMPTY}, если их не объявлено.
   */
  public TypeSet parameterTypes(ParameterDefinition parameter) {
    return parameter.getDescription()
      .map(description -> description.types().stream()
        .map(this::declaredType)
        .reduce(TypeSet.EMPTY, TypeSet::union))
      .orElse(TypeSet.EMPTY);
  }

  /**
   * Тип возвращаемого значения по первой записи секции «Возвращаемое значение».
   *
   * @param returnedValue записи секции «Возвращаемое значение» описания метода.
   * @return тип по имени первой записи; {@link TypeRef#UNKNOWN}, если записей нет
   *     либо имя не резолвится.
   */
  public TypeRef returnType(List<TypeDescription> returnedValue) {
    if (returnedValue.isEmpty()) {
      return TypeRef.UNKNOWN;
    }
    return headName(returnedValue.get(0).name());
  }

  /**
   * Разрешает одно объявление типа.
   *
   * @param type объявление типа из описания.
   * @return типы объявления; {@link TypeSet#EMPTY}, если имя не резолвится.
   */
  private TypeSet declaredType(TypeDescription type) {
    var ref = headName(type.name());
    return ref.kind() == TypeKind.UNKNOWN ? TypeSet.EMPTY : TypeSet.of(ref);
  }

  /**
   * Разрешает имя типа, отбрасывая пояснения после него.
   *
   * @param raw имя типа из описания.
   * @return тип по имени; {@link TypeRef#UNKNOWN}, если имя пустое или не резолвится.
   */
  private TypeRef headName(String raw) {
    if (raw.isBlank()) {
      return TypeRef.UNKNOWN;
    }
    var head = TYPE_NAME_TAIL.split(raw.trim(), TYPE_NAME_AND_TAIL)[0];
    return typeRegistry.resolve(head).orElse(TypeRef.UNKNOWN);
  }
}
