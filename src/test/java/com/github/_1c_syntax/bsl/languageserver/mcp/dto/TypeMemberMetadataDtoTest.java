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
import com.github._1c_syntax.bsl.languageserver.types.model.PlatformMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TypeMemberMetadataDtoTest {

  @Test
  void emptyMetadataIsEmpty() {
    assertThat(TypeMemberMetadataDto.EMPTY.isEmpty()).isTrue();
    assertThat(TypeMemberMetadataDto.from(PlatformMetadata.EMPTY, Language.RU).isEmpty()).isTrue();
  }

  @Test
  void mapsAllStringAndCollectionFields() {
    var metadata = new PlatformMetadata(
      "8.3.20",
      "8.3.27",
      List.of("ОбработкаОшибок.ПодробноеПредставлениеОшибки"),
      Set.of(Availability.SERVER, Availability.THIN_CLIENT),
      AccessMode.READ,
      BilingualString.of("Возвращает число", "Returns a number"),
      BilingualString.of("Замечание", "Note"),
      List.of(BilingualString.of("Пример1", "Example1")),
      List.of(BilingualString.of("См. Сообщить", "See Message"))
    );

    var dto = TypeMemberMetadataDto.from(metadata, Language.RU);

    assertThat(dto.sinceVersion()).isEqualTo("8.3.20");
    assertThat(dto.deprecatedSinceVersion()).isEqualTo("8.3.27");
    assertThat(dto.recommendedReplacements()).containsExactly("ОбработкаОшибок.ПодробноеПредставлениеОшибки");
    assertThat(dto.availabilities()).containsExactly("SERVER", "THIN_CLIENT");
    assertThat(dto.accessMode()).isEqualTo("READ");
    assertThat(dto.returnValueDescription()).isEqualTo("Возвращает число");
    assertThat(dto.notes()).isEqualTo("Замечание");
    assertThat(dto.examples()).containsExactly("Пример1");
    assertThat(dto.seeAlso()).containsExactly("См. Сообщить");
    assertThat(dto.isEmpty()).isFalse();
  }

  @Test
  void picksEnglishLocaleForBilingualFields() {
    var metadata = new PlatformMetadata(
      "",
      "",
      List.of(),
      Set.of(),
      null,
      BilingualString.of("Возвращает число", "Returns a number"),
      BilingualString.of("Замечание", "Note"),
      List.of(BilingualString.of("Пример1", "Example1")),
      List.of(BilingualString.of("См. Сообщить", "See Message"))
    );

    var dto = TypeMemberMetadataDto.from(metadata, Language.EN);

    assertThat(dto.returnValueDescription()).isEqualTo("Returns a number");
    assertThat(dto.notes()).isEqualTo("Note");
    assertThat(dto.examples()).containsExactly("Example1");
    assertThat(dto.seeAlso()).containsExactly("See Message");
  }

  @Test
  void nullsOutBlankFields() {
    var metadata = new PlatformMetadata(
      "  ",
      "",
      List.of(),
      Set.of(),
      null,
      BilingualString.of(" ", " "),
      BilingualString.EMPTY,
      List.of(),
      List.of()
    );

    var dto = TypeMemberMetadataDto.from(metadata, Language.RU);

    assertThat(dto.sinceVersion()).isNull();
    assertThat(dto.deprecatedSinceVersion()).isNull();
    assertThat(dto.accessMode()).isNull();
    assertThat(dto.returnValueDescription()).isNull();
    assertThat(dto.notes()).isNull();
    assertThat(dto.isEmpty()).isTrue();
  }

  @Test
  void availabilitiesAreSortedAlphabetically() {
    var metadata = new PlatformMetadata(
      "", "", List.of(),
      Set.of(Availability.WEB_CLIENT, Availability.SERVER, Availability.THIN_CLIENT),
      null,
      BilingualString.EMPTY, BilingualString.EMPTY,
      List.of(), List.of()
    );

    var dto = TypeMemberMetadataDto.from(metadata, Language.RU);

    assertThat(dto.availabilities()).containsExactly("SERVER", "THIN_CLIENT", "WEB_CLIENT");
  }

  @Test
  void blankBilingualEntriesAreDropped() {
    var metadata = new PlatformMetadata(
      "", "", List.of(), Set.of(), null,
      BilingualString.EMPTY,
      BilingualString.EMPTY,
      List.of(
        BilingualString.of("Пример1", "Example1"),
        BilingualString.of("", ""),
        BilingualString.of(" ", " ")
      ),
      List.of()
    );

    var dto = TypeMemberMetadataDto.from(metadata, Language.RU);

    assertThat(dto.examples()).containsExactly("Пример1");
  }
}
