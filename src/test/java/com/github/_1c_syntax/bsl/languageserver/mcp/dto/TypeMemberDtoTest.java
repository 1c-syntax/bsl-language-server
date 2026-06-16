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
import com.github._1c_syntax.bsl.languageserver.types.model.AccessMode;
import com.github._1c_syntax.bsl.languageserver.types.model.Availability;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.ParameterDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.PlatformMetadata;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TypeMemberDtoTest {

  private static final TypeRef STRING_TYPE = new TypeRef(TypeKind.PRIMITIVE, "Строка");
  private static final TypeRef NUMBER_TYPE = new TypeRef(TypeKind.PRIMITIVE, "Число");

  @Test
  void mapsMethodWithSignatures() {
    var signature = new SignatureDescriptor(
      List.of(new ParameterDescriptor("Шаблон", TypeSet.of(STRING_TYPE), false, "Шаблон")),
      TypeSet.of(STRING_TYPE),
      "Соединить параметры по шаблону"
    );
    var method = new MemberDescriptor(
      "СтрШаблон",
      MemberKind.METHOD,
      "Возвращает строку",
      TypeSet.of(STRING_TYPE),
      List.of(signature),
      null,
      false,
      PlatformMetadata.EMPTY
    );

    var dto = TypeMemberDto.from(method, Language.RU);

    assertThat(dto.name()).isEqualTo("СтрШаблон");
    assertThat(dto.kind()).isEqualTo("METHOD");
    assertThat(dto.types()).containsExactly("Строка");
    assertThat(dto.description()).isEqualTo("Возвращает строку");
    assertThat(dto.signatures()).hasSize(1);
    assertThat(dto.signatures().get(0).parameters()).hasSize(1);
    assertThat(dto.async()).isFalse();
    assertThat(dto.metadata()).isNull();
  }

  @Test
  void propertyHasEmptySignaturesList() {
    var property = MemberDescriptor.property("Количество", NUMBER_TYPE);

    var dto = TypeMemberDto.from(property, Language.RU);

    assertThat(dto.kind()).isEqualTo("PROPERTY");
    assertThat(dto.signatures()).isEmpty();
    assertThat(dto.types()).containsExactly("Число");
  }

  @Test
  void eventKeepsSignaturesFromMember() {
    var signature = new SignatureDescriptor(
      List.of(new ParameterDescriptor("Отказ", TypeSet.EMPTY, false, "")),
      TypeSet.EMPTY,
      ""
    );
    var event = new MemberDescriptor(
      "ПередЗаписью",
      MemberKind.EVENT,
      "Срабатывает перед записью",
      TypeSet.EMPTY,
      List.of(signature),
      null,
      false,
      PlatformMetadata.EMPTY
    );

    var dto = TypeMemberDto.from(event, Language.RU);

    assertThat(dto.kind()).isEqualTo("EVENT");
    assertThat(dto.signatures()).hasSize(1);
    assertThat(dto.signatures().get(0).parameters()).extracting("name").containsExactly("Отказ");
  }

  @Test
  void carriesAsyncFlag() {
    var asyncMethod = MemberDescriptor.method("ВопросАсинх").withAsync(true);

    var dto = TypeMemberDto.from(asyncMethod, Language.RU);

    assertThat(dto.async()).isTrue();
  }

  @Test
  void platformMetadataIsExposedWhenNotEmpty() {
    var metadata = new PlatformMetadata(
      "8.3.20",
      "",
      List.of(),
      Set.of(Availability.SERVER),
      AccessMode.READ,
      BilingualString.EMPTY,
      BilingualString.EMPTY,
      List.of(),
      List.of()
    );
    var member = new MemberDescriptor(
      "Свойство",
      MemberKind.PROPERTY,
      "",
      TypeSet.of(STRING_TYPE),
      List.of(),
      null,
      false,
      metadata
    );

    var dto = TypeMemberDto.from(member, Language.RU);

    assertThat(dto.metadata()).isNotNull();
    assertThat(dto.metadata().sinceVersion()).isEqualTo("8.3.20");
    assertThat(dto.metadata().availabilities()).containsExactly("SERVER");
    assertThat(dto.metadata().accessMode()).isEqualTo("READ");
  }

  @Test
  void picksEnglishLocaleWhenRequested() {
    var member = new MemberDescriptor(
      BilingualString.of("Добавить", "Add"),
      MemberKind.METHOD,
      BilingualString.of("Добавить элемент", "Adds an item"),
      TypeSet.EMPTY,
      List.of(),
      null,
      false,
      PlatformMetadata.EMPTY
    );

    var dto = TypeMemberDto.from(member, Language.EN);

    assertThat(dto.name()).isEqualTo("Add");
    assertThat(dto.description()).isEqualTo("Adds an item");
  }

  @Test
  void blankDescriptionBecomesNull() {
    var member = new MemberDescriptor("Метод", MemberKind.METHOD, "", TypeSet.EMPTY, List.of(),
      null, false, PlatformMetadata.EMPTY);

    var dto = TypeMemberDto.from(member, Language.RU);

    assertThat(dto.description()).isNull();
  }
}
