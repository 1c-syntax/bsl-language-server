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
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Сигнатура метода или конструктора.
 *
 * @param parameters Параметры (в порядке объявления).
 * @param returnTypes Типы возвращаемого значения (полные имена); пусто для процедур.
 * @param description Описание сигнатуры; {@code null}, если отсутствует.
 */
public record TypeSignatureDto(
  List<TypeParameterDto> parameters,
  List<String> returnTypes,
  @Nullable String description
) {

  public static TypeSignatureDto from(SignatureDescriptor signature, Language language) {
    var description = signature.displayDescription(language);
    return new TypeSignatureDto(
      signature.parameters().stream().map(parameter -> TypeParameterDto.from(parameter, language)).toList(),
      signature.returnTypes().refs().stream().map(TypeRef::qualifiedName).sorted().toList(),
      description == null || description.isBlank() ? null : description
    );
  }
}
