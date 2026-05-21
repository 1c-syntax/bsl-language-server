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
  void mapsKindToSymbolKind_property() {
    var symbol = new SyntheticSymbol(
      "Справочники", SyntheticKind.PLATFORM_GLOBAL_PROPERTY, "", CATALOG_MANAGER);
    assertThat(symbol.getSymbolKind()).isEqualTo(SymbolKind.Property);
  }

  @Test
  void mapsKindToSymbolKind_memberProperty() {
    var symbol = new SyntheticSymbol(
      "Количество", SyntheticKind.PLATFORM_MEMBER_PROPERTY, "");
    assertThat(symbol.getSymbolKind()).isEqualTo(SymbolKind.Property);
  }

  @Test
  void mapsKindToSymbolKind_enum() {
    var symbol = new SyntheticSymbol(
      "КодировкаТекста", SyntheticKind.PLATFORM_GLOBAL_ENUM, "");
    assertThat(symbol.getSymbolKind()).isEqualTo(SymbolKind.Enum);
  }

  @Test
  void mapsKindToSymbolKind_method() {
    var globalMethod = new SyntheticSymbol(
      "Сообщить", SyntheticKind.PLATFORM_GLOBAL_METHOD, "");
    var memberMethod = new SyntheticSymbol(
      "Добавить", SyntheticKind.PLATFORM_MEMBER_METHOD, "");
    assertThat(globalMethod.getSymbolKind()).isEqualTo(SymbolKind.Method);
    assertThat(memberMethod.getSymbolKind()).isEqualTo(SymbolKind.Method);
  }

  @Test
  void mapsKindToSymbolKind_typeName() {
    var symbol = new SyntheticSymbol(
      "Массив", SyntheticKind.TYPE_NAME, "");
    assertThat(symbol.getSymbolKind()).isEqualTo(SymbolKind.Class);
  }

  @Test
  void mapsKindToSymbolKind_libraryModule() {
    var symbol = new SyntheticSymbol(
      "ФС", SyntheticKind.LIBRARY_MODULE, "");
    assertThat(symbol.getSymbolKind()).isEqualTo(SymbolKind.Module);
  }

  @Test
  void getOwnerSymbolEmptyByDefault() {
    var symbol = new SyntheticSymbol("X", SyntheticKind.TYPE_NAME, "");
    assertThat(symbol.getOwnerSymbol()).isEmpty();
  }

  @Test
  void getSourceSymbolEmptyByDefault() {
    var symbol = new SyntheticSymbol("X", SyntheticKind.LIBRARY_MODULE, "");
    assertThat(symbol.getSourceSymbol()).isEmpty();
  }

  @Test
  void symbolDescriptionWrapsDescriptionString() {
    var symbol = new SyntheticSymbol("X", SyntheticKind.PLATFORM_GLOBAL_METHOD, "описание");
    assertThat(symbol.getSymbolDescription()).isNotNull();
    assertThat(symbol.getSymbolDescription().getPurposeDescription()).isEqualTo("описание");
  }

  @Test
  void valueTypeUnknownInThreeArgConstructor() {
    var symbol = new SyntheticSymbol("X", SyntheticKind.TYPE_NAME, "doc");
    assertThat(symbol.getValueType()).isSameAs(TypeRef.UNKNOWN);
  }

  @Test
  void equalsAndHashCodeByNameAndKind() {
    var a = new SyntheticSymbol("Имя", SyntheticKind.TYPE_NAME, "X");
    var b = new SyntheticSymbol("Имя", SyntheticKind.TYPE_NAME, "Y");
    var different = new SyntheticSymbol("Другое", SyntheticKind.TYPE_NAME, "");
    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    assertThat(a).isNotEqualTo(different);
  }
}
