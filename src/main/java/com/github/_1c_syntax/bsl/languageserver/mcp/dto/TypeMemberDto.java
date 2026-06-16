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
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Член типа — метод, свойство или событие.
 *
 * @param name Имя члена.
 * @param kind Вид члена: {@code METHOD}, {@code PROPERTY} или {@code EVENT}.
 * @param types Типы значения свойства или возвращаемого значения метода (полные имена).
 * @param description Описание; {@code null}, если отсутствует.
 * @param signatures Сигнатуры (для метода/события); для свойства — пустой список.
 * @param async Признак асинхронного метода ({@code Асинх}/{@code Async}); для свойств всегда {@code false}.
 * @param metadata Платформенная метаинформация (версии, контексты, примеры и т.п.);
 *   {@code null}, если метаинформация отсутствует.
 */
public record TypeMemberDto(
  String name,
  String kind,
  List<String> types,
  @Nullable String description,
  List<TypeSignatureDto> signatures,
  boolean async,
  @Nullable TypeMemberMetadataDto metadata
) {

  public static TypeMemberDto from(MemberDescriptor member, Language language) {
    var signatures = member.kind() == MemberKind.PROPERTY
      ? List.<TypeSignatureDto>of()
      : member.signatures().stream().map(signature -> TypeSignatureDto.from(signature, language)).toList();
    var description = member.displayDescription(language);
    var metadata = TypeMemberMetadataDto.from(member.metadata(), language);
    return new TypeMemberDto(
      member.displayName(language),
      member.kind().name(),
      member.returnTypes().refs().stream().map(TypeRef::qualifiedName).sorted().toList(),
      description == null || description.isBlank() ? null : description,
      signatures,
      member.async(),
      metadata.isEmpty() ? null : metadata
    );
  }
}
