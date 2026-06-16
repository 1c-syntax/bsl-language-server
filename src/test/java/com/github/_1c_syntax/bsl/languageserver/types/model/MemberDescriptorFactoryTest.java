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
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MemberDescriptorFactoryTest {

  private static final TypeRef NUMBER = new TypeRef(TypeKind.PRIMITIVE, "Число");
  private static final TypeRef STRING = new TypeRef(TypeKind.PRIMITIVE, "Строка");

  @Test
  void methodFactoryNameOnly() {
    // given / when
    var m = MemberDescriptor.method("Сообщить");

    // then
    assertThat(m.name()).isEqualTo("Сообщить");
    assertThat(m.kind()).isEqualTo(MemberKind.METHOD);
    assertThat(m.signatures()).isEmpty();
    assertThat(m.returnTypes()).isSameAs(TypeSet.EMPTY);
  }

  @Test
  void methodFactoryWithSignaturesInferReturnTypes() {
    // given
    var sig = new SignatureDescriptor(List.of(), NUMBER, "");

    // when
    var m = MemberDescriptor.method("F", List.of(sig));

    // then
    assertThat(m.signatures()).containsExactly(sig);
    assertThat(m.returnTypes().refs()).containsExactly(NUMBER);
  }

  @Test
  void methodFactoryWithDescriptionAndSignatures() {
    // given
    var sig = new SignatureDescriptor(List.of(), STRING, "");

    // when
    var m = MemberDescriptor.method("F", "описание", List.of(sig));

    // then
    assertThat(m.description()).isEqualTo("описание");
    assertThat(m.returnTypes().refs()).containsExactly(STRING);
  }

  @Test
  void propertyFactoriesNameAndType() {
    // given / when
    var nameOnly = MemberDescriptor.property("X");
    var withType = MemberDescriptor.property("Y", NUMBER);
    var withDesc = MemberDescriptor.property("Z", NUMBER, "Z-описание");

    // then
    assertThat(nameOnly.kind()).isEqualTo(MemberKind.PROPERTY);
    assertThat(nameOnly.returnTypes()).isSameAs(TypeSet.EMPTY);
    assertThat(withType.returnType()).isEqualTo(NUMBER);
    assertThat(withDesc.description()).isEqualTo("Z-описание");
  }

  @Test
  void compositePropertyFactoryWithTypeSet() {
    // given
    var union = TypeSet.of(NUMBER, STRING);

    // when
    var m = MemberDescriptor.property("X", union, "");

    // then
    assertThat(m.returnTypes().refs()).containsExactlyInAnyOrder(NUMBER, STRING);
  }

  @Test
  void genericPropertyMarksGenericFlag() {
    // given / when
    var m = MemberDescriptor.genericProperty("<Имя>", NUMBER, "");

    // then
    assertThat(m.generic()).isTrue();
    assertThat(m.name()).isEqualTo("<Имя>");
  }

  @Test
  void withMetadataReplacesMetadataOnly() {
    // given
    var base = MemberDescriptor.property("X", NUMBER);
    var newMeta = new PlatformMetadata(
      "8.3.10", "", List.of(), java.util.Set.of(), null,
      BilingualString.EMPTY, BilingualString.EMPTY, List.of(), List.of()
    );

    // when
    var updated = base.withMetadata(newMeta);

    // then
    assertThat(updated.metadata().sinceVersion()).isEqualTo("8.3.10");
    assertThat(updated.name()).isEqualTo("X");
    assertThat(updated.returnType()).isEqualTo(NUMBER);
  }

  @Test
  void withBilingualNameReplacesNameOnly() {
    // given
    var base = MemberDescriptor.property("X", NUMBER);
    var name = BilingualString.of("Имя", "Name");

    // when
    var updated = base.withBilingualName(name);

    // then
    assertThat(updated.bilingualName()).isSameAs(name);
    assertThat(updated.returnType()).isEqualTo(NUMBER);
  }

  @Test
  void withBilingualDescriptionReplacesDescriptionOnly() {
    // given
    var base = MemberDescriptor.property("X", NUMBER, "old");
    var newDesc = BilingualString.of("ru", "en");

    // when
    var updated = base.withBilingualDescription(newDesc);

    // then
    assertThat(updated.bilingualDescription()).isSameAs(newDesc);
    assertThat(updated.displayDescription(Language.RU)).isEqualTo("ru");
    assertThat(updated.displayDescription(Language.EN)).isEqualTo("en");
  }

  @Test
  void withBilingualNameAcceptsRuAndEnAtOnce() {
    // given
    var base = MemberDescriptor.method("F");

    // when
    var updated = base.withBilingualName(BilingualString.of("F-ru", "F-en"));

    // then
    assertThat(updated.displayName(Language.RU)).isEqualTo("F-ru");
    assertThat(updated.displayName(Language.EN)).isEqualTo("F-en");
  }

  @Test
  void matchesIsCaseInsensitiveBilingual() {
    // given
    var m = MemberDescriptor.method("F")
      .withBilingualName(BilingualString.of("Имя", "Name"));

    // when / then
    assertThat(m.matches("ИМЯ")).isTrue();
    assertThat(m.matches("name")).isTrue();
    assertThat(m.matches("other")).isFalse();
  }

  @Test
  void returnTypeReturnsFirstFromUnionOrUnknownWhenEmpty() {
    // given
    var multi = MemberDescriptor.property("X", TypeSet.of(NUMBER, STRING), "");
    var empty = MemberDescriptor.property("Y");

    // when / then
    assertThat(multi.returnType()).isEqualTo(NUMBER);
    assertThat(empty.returnType()).isSameAs(TypeRef.UNKNOWN);
  }

  @Test
  void compatConstructorMonolingualNameAndDescription() {
    // given / when
    var m = new MemberDescriptor("Имя", MemberKind.METHOD, "описание",
      TypeSet.of(NUMBER), List.of(), null, false, PlatformMetadata.EMPTY);

    // then
    assertThat(m.name()).isEqualTo("Имя");
    assertThat(m.description()).isEqualTo("описание");
    assertThat(m.returnType()).isEqualTo(NUMBER);
  }

  @Test
  void compatConstructorBilingualNameOverridesMonolingual() {
    // given
    var bilingual = BilingualString.of("ИмяRu", "NameEn");

    // when
    var m = new MemberDescriptor("legacy", MemberKind.PROPERTY, "д",
      TypeSet.of(NUMBER), List.of(), null, false, PlatformMetadata.EMPTY, bilingual);

    // then
    assertThat(m.displayName(Language.RU)).isEqualTo("ИмяRu");
    assertThat(m.displayName(Language.EN)).isEqualTo("NameEn");
  }

  @Test
  void compatConstructorBilingualEmptyFallsBackToMonolingual() {
    // given
    var emptyBilingual = BilingualString.EMPTY;

    // when
    var m = new MemberDescriptor("Имя", MemberKind.PROPERTY, "д",
      TypeSet.of(NUMBER), List.of(), null, false, PlatformMetadata.EMPTY, emptyBilingual);

    // then — пустое bilingual → используется monolingual name.
    assertThat(m.name()).isEqualTo("Имя");
  }

  @Test
  void getSourceSymbolEmptyWhenAbsent() {
    // given
    var m = MemberDescriptor.method("X");

    // when / then
    assertThat(m.getSourceSymbol()).isEmpty();
  }

  @Test
  void withSourceSymbolAttachesSymbol() {
    // given
    var m = MemberDescriptor.method("X");
    var symbol = org.mockito.Mockito.mock(
      com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol.class);

    // when
    var updated = m.withSourceSymbol(symbol);

    // then
    assertThat(updated.getSourceSymbol()).contains(symbol);
    assertThat(updated.name()).isEqualTo("X");
  }

  @Test
  void specializeNoBindingsReturnsSameInstance() {
    // given
    var m = MemberDescriptor.method("X");

    // when
    var noOp = m.specialize(java.util.Map.of());
    var nullOp = m.specialize(null);

    // then
    assertThat(noOp).isSameAs(m);
    assertThat(nullOp).isSameAs(m);
  }

  @Test
  void canonicalConstructorNormalizesAllNullFields() {
    // given / when — все опциональные поля переданы null.
    var m = new MemberDescriptor(
      (BilingualString) null, // bilingualName
      MemberKind.METHOD,
      (BilingualString) null, // bilingualDescription
      null,                   // returnTypes
      List.of(),
      null,                   // sourceSymbol
      false,
      null                    // metadata
    );

    // then — все null'ы заменены на EMPTY-singleton'ы.
    assertThat(m.bilingualName()).isSameAs(BilingualString.EMPTY);
    assertThat(m.bilingualDescription()).isSameAs(BilingualString.EMPTY);
    assertThat(m.returnTypes()).isSameAs(TypeSet.EMPTY);
    assertThat(m.metadata()).isSameAs(PlatformMetadata.EMPTY);
  }
}
