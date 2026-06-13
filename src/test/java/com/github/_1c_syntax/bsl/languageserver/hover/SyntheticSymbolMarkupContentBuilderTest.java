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

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.types.symbol.SyntheticKind;
import com.github._1c_syntax.bsl.languageserver.types.symbol.SyntheticSymbol;
import com.github._1c_syntax.bsl.languageserver.utils.Resources;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SyntheticSymbolMarkupContentBuilderTest {

  @Mock
  private TypeRegistry typeRegistry;
  @Mock
  private CollectionHoverHints collectionHoverHints;
  @Mock
  private Resources resources;
  @Mock
  private LanguageServerConfiguration configuration;

  private SyntheticSymbolMarkupContentBuilder builder;

  @BeforeEach
  void setUp() {
    builder = new SyntheticSymbolMarkupContentBuilder(typeRegistry, collectionHoverHints, resources, configuration);
    when(resources.getResourceString(eq(SyntheticSymbolMarkupContentBuilder.class), any(String.class)))
      .thenAnswer(inv -> "[" + inv.getArgument(1) + "]");
    when(configuration.getLanguage()).thenReturn(Language.RU);
    // displayName в реальном реестре отдаёт qualifiedName, если нет двуязычного имени.
    when(typeRegistry.displayName(any(TypeRef.class), any()))
      .thenAnswer(inv -> ((TypeRef) inv.getArgument(0)).qualifiedName());
  }

  @Test
  void getContentForGlobalPropertyIncludesNameRoleAndDescription() {
    // given
    var valueType = new TypeRef(TypeKind.PLATFORM, "СправочникиМенеджер");
    var symbol = new SyntheticSymbol(
      "Справочники", SyntheticKind.PLATFORM_GLOBAL_PROPERTY,
      "Объект для работы со справочниками.", valueType);

    // when
    var content = builder.getContent(referenceTo(symbol));

    // then
    assertThat(content.getKind()).isEqualTo(MarkupKind.MARKDOWN);
    var value = content.getValue();
    assertThat(value)
      .contains("Справочники")
      .contains(": СправочникиМенеджер")
      .contains("[role.PLATFORM_GLOBAL_PROPERTY]")
      .contains("Объект для работы со справочниками.");
  }

  @Test
  void getContentSkipsValueTypeWhenUnknown() {
    // given
    var symbol = new SyntheticSymbol(
      "Истина", SyntheticKind.PLATFORM_GLOBAL_PROPERTY, "");

    // when
    var content = builder.getContent(referenceTo(symbol));

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
    var content = builder.getContent(referenceTo(symbol));

    // then
    var lines = content.getValue().split("\n");
    assertThat(lines.length).isLessThan(10);
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
    var content = builder.getContent(referenceTo(symbol));

    // then
    var value = content.getValue();
    assertThat(value)
      .contains("ФС")
      .contains("[role.LIBRARY_MODULE]")
      .contains("OneScript:");
  }

  @Test
  void getContentForTypeNameMarksAsTypeName() {
    // given
    var symbol = new SyntheticSymbol("Массив", SyntheticKind.TYPE_NAME, "Динамический массив.");

    // when
    var content = builder.getContent(referenceTo(symbol));

    // then
    var value = content.getValue();
    assertThat(value)
      .contains("Массив")
      .contains("[role.TYPE_NAME]");
  }

  private static Reference referenceTo(Symbol symbol) {
    return Reference.of(Mockito.mock(SourceDefinedSymbol.class), symbol,
      new Location("file:///t", new Range(new Position(0, 0), new Position(0, 0))));
  }
}
