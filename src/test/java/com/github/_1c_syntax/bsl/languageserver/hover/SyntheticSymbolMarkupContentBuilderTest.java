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
package com.github._1c_syntax.bsl.languageserver.hover;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.symbol.SyntheticKind;
import com.github._1c_syntax.bsl.languageserver.types.symbol.SyntheticSymbol;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.SymbolKind;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-тесты построителя hover-контента для synthetic-символов.
 */
class SyntheticSymbolMarkupContentBuilderTest extends AbstractServerContextAwareTest {

  @Autowired
  private SyntheticSymbolMarkupContentBuilder builder;

  @Test
  void getContentForGlobalPropertyIncludesNameRoleAndDescription() {
    // given
    var valueType = new TypeRef(TypeKind.PLATFORM, "СправочникиМенеджер");
    var symbol = new SyntheticSymbol(
      "Справочники", SyntheticKind.PLATFORM_GLOBAL_PROPERTY,
      "Объект для работы со справочниками.", valueType);

    // when
    var content = builder.getContent(symbol);

    // then
    assertThat(content.getKind()).isEqualTo(MarkupKind.MARKDOWN);
    var value = content.getValue();
    assertThat(value).contains("Справочники");
    assertThat(value).contains(": СправочникиМенеджер");
    assertThat(value).contains("глобальное свойство");
    assertThat(value).contains("Объект для работы со справочниками.");
  }

  @Test
  void getContentSkipsValueTypeWhenUnknown() {
    // given
    var symbol = new SyntheticSymbol(
      "Истина", SyntheticKind.PLATFORM_GLOBAL_PROPERTY, "");

    // when
    var content = builder.getContent(symbol);

    // then
    assertThat(content.getValue()).contains("Истина");
    assertThat(content.getValue()).doesNotContain(":");
  }

  @Test
  void getContentOmitsEmptyDescription() {
    // given
    var symbol = new SyntheticSymbol(
      "Истина", SyntheticKind.PLATFORM_GLOBAL_PROPERTY, "");

    // when
    var content = builder.getContent(symbol);

    // then
    var lines = content.getValue().split("\n");
    assertThat(lines.length).isLessThan(10);
  }

  @Test
  void getSymbolKindReturnsProperty() {
    // given / when
    var kind = builder.getSymbolKind();

    // then
    assertThat(kind).isEqualTo(SymbolKind.Property);
  }

  @Test
  void getSymbolClassReturnsSyntheticSymbol() {
    // given / when
    var symbolClass = builder.getSymbolClass();

    // then
    assertThat(symbolClass).isEqualTo(SyntheticSymbol.class);
  }

  @Test
  void getContentForLibraryModuleShowsRoleAndName() {
    // given
    var symbol = new SyntheticSymbol(
      "ФС", SyntheticKind.LIBRARY_MODULE,
      "OneScript: работа с файловой системой.");

    // when
    var content = builder.getContent(symbol);

    // then
    var value = content.getValue();
    assertThat(value).contains("ФС");
    assertThat(value).contains("модуль библиотеки");
    assertThat(value).contains("OneScript:");
  }

  @Test
  void getContentForTypeNameMarksAsTypeName() {
    // given
    var symbol = new SyntheticSymbol("Массив", SyntheticKind.TYPE_NAME, "Динамический массив.");

    // when
    var content = builder.getContent(symbol);

    // then
    var value = content.getValue();
    assertThat(value).contains("Массив");
    assertThat(value).contains("имя типа");
  }
}
