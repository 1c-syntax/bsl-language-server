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
package com.github._1c_syntax.bsl.languageserver.types.symbol;

import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import org.eclipse.lsp4j.SymbolKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SyntheticSymbolTest {

  private static final TypeRef CATALOG_MANAGER =
    new TypeRef(TypeKind.PLATFORM, "СправочникиМенеджер");

  @Test
  void mapsKindToSymbolKindProperty() {
    // given
    var symbol = new SyntheticSymbol(
      "Справочники", SyntheticKind.PLATFORM_GLOBAL_PROPERTY, "", CATALOG_MANAGER);

    // when
    var kind = symbol.getSymbolKind();

    // then
    assertThat(kind).isEqualTo(SymbolKind.Property);
  }

  @Test
  void mapsKindToSymbolKindMemberProperty() {
    // given
    var symbol = new SyntheticSymbol(
      "Количество", SyntheticKind.PLATFORM_MEMBER_PROPERTY, "");

    // when
    var kind = symbol.getSymbolKind();

    // then
    assertThat(kind).isEqualTo(SymbolKind.Property);
  }

  @Test
  void mapsKindToSymbolKindEnum() {
    // given
    var symbol = new SyntheticSymbol(
      "КодировкаТекста", SyntheticKind.PLATFORM_GLOBAL_ENUM, "");

    // when
    var kind = symbol.getSymbolKind();

    // then
    assertThat(kind).isEqualTo(SymbolKind.Enum);
  }

  @Test
  void mapsKindToSymbolKindMethod() {
    // given
    var globalMethod = new SyntheticSymbol(
      "Сообщить", SyntheticKind.PLATFORM_GLOBAL_METHOD, "");
    var memberMethod = new SyntheticSymbol(
      "Добавить", SyntheticKind.PLATFORM_MEMBER_METHOD, "");

    // when / then
    assertThat(globalMethod.getSymbolKind()).isEqualTo(SymbolKind.Method);
    assertThat(memberMethod.getSymbolKind()).isEqualTo(SymbolKind.Method);
  }

  @Test
  void mapsKindToSymbolKindTypeName() {
    // given
    var symbol = new SyntheticSymbol("Массив", SyntheticKind.TYPE_NAME, "");

    // when
    var kind = symbol.getSymbolKind();

    // then
    assertThat(kind).isEqualTo(SymbolKind.Class);
  }

  @Test
  void mapsKindToSymbolKindLibraryModule() {
    // given
    var symbol = new SyntheticSymbol("ФС", SyntheticKind.LIBRARY_MODULE, "");

    // when
    var kind = symbol.getSymbolKind();

    // then
    assertThat(kind).isEqualTo(SymbolKind.Module);
  }

  @Test
  void getOwnerSymbolEmptyByDefault() {
    // given
    var symbol = new SyntheticSymbol("X", SyntheticKind.TYPE_NAME, "");

    // when
    var owner = symbol.getOwnerSymbol();

    // then
    assertThat(owner).isEmpty();
  }

  @Test
  void getSourceSymbolEmptyByDefault() {
    // given
    var symbol = new SyntheticSymbol("X", SyntheticKind.LIBRARY_MODULE, "");

    // when
    var source = symbol.getSourceSymbol();

    // then
    assertThat(source).isEmpty();
  }

  @Test
  void symbolDescriptionWrapsDescriptionString() {
    // given
    var symbol = new SyntheticSymbol("X", SyntheticKind.PLATFORM_GLOBAL_METHOD, "описание");

    // when
    var description = symbol.getSymbolDescription();

    // then
    assertThat(description).isNotNull();
    assertThat(description.getPurposeDescription()).isEqualTo("описание");
  }

  @Test
  void valueTypeUnknownInThreeArgConstructor() {
    // given
    var symbol = new SyntheticSymbol("X", SyntheticKind.TYPE_NAME, "doc");

    // when
    var value = symbol.getValueType();

    // then
    assertThat(value).isSameAs(TypeRef.UNKNOWN);
  }

  @Test
  void equalsAndHashCodeByNameAndKind() {
    // given
    var a = new SyntheticSymbol("Имя", SyntheticKind.TYPE_NAME, "X");
    var b = new SyntheticSymbol("Имя", SyntheticKind.TYPE_NAME, "Y");
    var different = new SyntheticSymbol("Другое", SyntheticKind.TYPE_NAME, "");

    // when / then
    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    assertThat(a).isNotEqualTo(different);
  }

  @Test
  void constructorWithOwnerSetsOwnerSymbol() {
    // given — конструктор с явным owner
    var owner = new SyntheticSymbol("Owner", SyntheticKind.TYPE_NAME, "");
    var symbol = new SyntheticSymbol("Член", SyntheticKind.PLATFORM_MEMBER_PROPERTY,
      "описание", TypeRef.UNKNOWN, owner);

    // when / then
    assertThat(symbol.getOwnerSymbol()).contains(owner);
  }

  @Test
  void acceptVisitorDoesNothing() {
    // given
    var symbol = new SyntheticSymbol("X", SyntheticKind.PLATFORM_GLOBAL_PROPERTY, "");
    // Mock visitor — accept должен ничего не вызвать на нём.
    var visitor = org.mockito.Mockito.mock(
      com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTreeVisitor.class);

    // when
    symbol.accept(visitor);

    // then — visitor не получает никаких вызовов.
    org.mockito.Mockito.verifyNoInteractions(visitor);
  }
}
