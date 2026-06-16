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
package com.github._1c_syntax.bsl.languageserver.mcp.dto;

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.types.model.ParameterDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Параметр сигнатуры метода или конструктора.
 *
 * @param name Имя параметра.
 * @param types Допустимые типы значения параметра (полные имена).
 * @param optional Признак необязательного параметра.
 * @param variadic Признак variadic-параметра (произвольное число значений в хвосте сигнатуры).
 * @param defaultValue Значение по умолчанию; {@code null}, если не задано.
 * @param description Описание параметра; {@code null}, если отсутствует.
 */
public record TypeParameterDto(
  String name,
  List<String> types,
  boolean optional,
  boolean variadic,
  @Nullable String defaultValue,
  @Nullable String description
) {

  public static TypeParameterDto from(ParameterDescriptor parameter, Language language) {
    var defaultValue = parameter.defaultValue();
    var description = parameter.displayDescription(language);
    return new TypeParameterDto(
      parameter.displayName(language),
      parameter.types().refs().stream().map(TypeRef::qualifiedName).sorted().toList(),
      parameter.optional(),
      parameter.variadic(),
      defaultValue.isBlank() ? null : defaultValue,
      description.isBlank() ? null : description
    );
  }
}
