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
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.model.Availability;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.ParameterDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.PlatformMetadata;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.configuration.Resources;
import org.eclipse.lsp4j.MarkupKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConstructorHoverBuilderTest {

  private static final TypeRef STRUCTURE = new TypeRef(TypeKind.PLATFORM, "Структура");
  private static final TypeRef NUMBER = new TypeRef(TypeKind.PRIMITIVE, "Число");
  private static final TypeRef STRING = new TypeRef(TypeKind.PRIMITIVE, "Строка");

  @Mock
  private TypeService typeService;
  @Mock
  private CollectionHoverHints collectionHoverHints;
  @Mock
  private Resources resources;
  @Mock
  private LanguageServerConfiguration configuration;

  private ConstructorHoverBuilder builder;

  @BeforeEach
  void setUp() {
    builder = new ConstructorHoverBuilder(
      typeService, collectionHoverHints, resources, configuration,
      new PlatformMetadataRenderer(resources, configuration));
    when(configuration.getLanguage()).thenReturn(Language.RU);
    when(typeService.displayName(any(TypeRef.class), any(Language.class)))
      .thenAnswer(inv -> ((TypeRef) inv.getArgument(0)).qualifiedName());
    when(typeService.getDescription(any(TypeRef.class), any(Language.class), any(FileType.class))).thenReturn("");
    when(typeService.getTypeMetadata(any(TypeRef.class), any(FileType.class)))
      .thenReturn(PlatformMetadata.EMPTY);
    // Стаб покрывает и сам билдер, и вынесенный PlatformMetadataRenderer.
    when(resources.getResourceString(any(Class.class), any(String.class)))
      .thenAnswer(inv -> "[" + inv.getArgument(1) + "]");
  }

  @Test
  void buildWithChosenSignatureRendersParameters() {
    // given
    var param = new ParameterDescriptor(
      BilingualString.of("Ключи"), TypeSet.of(STRING), false,
      BilingualString.of("список ключей"), "");
    var chosen = new SignatureDescriptor(List.of(param), TypeSet.EMPTY, "");

    // when
    var content = builder.build("Структура", STRUCTURE, chosen, List.of(chosen), false, "", FileType.BSL);

    // then
    assertThat(content.getKind()).isEqualTo(MarkupKind.MARKDOWN);
    var value = content.getValue();
    assertThat(value)
      .contains("[newKeyword]").contains("Структура")
      .contains("[constructorOf]")
      .contains("[parameters]")
      .contains("`Ключи`")
      .contains("Строка")
      .contains("— список ключей");
  }

  @Test
  void buildWithoutChosenSignatureOmitsParameters() {
    // given / when
    var content = builder.build("Структура", STRUCTURE, null, List.of(), false, "", FileType.BSL);

    // then
    var value = content.getValue();
    assertThat(value).contains("Структура").doesNotContain("[parameters]");
  }

  @Test
  void buildWithMultipleConstructorsListsAllVariants() {
    // given
    var sig1 = new SignatureDescriptor(List.of(), TypeSet.EMPTY, "пусто");
    var paramKeys = new ParameterDescriptor(
      BilingualString.of("Ключи"), TypeSet.of(STRING), false, BilingualString.EMPTY, "");
    var sig2 = new SignatureDescriptor(List.of(paramKeys), TypeSet.EMPTY, "с ключами");

    // when
    var content = builder.build("Структура", STRUCTURE, sig1, List.of(sig1, sig2), false, "", FileType.BSL);

    // then
    var value = content.getValue();
    assertThat(value)
      .contains("[allConstructorVariants]")
      .contains("— пусто")
      .contains("— с ключами");
  }

  @Test
  void buildWithDisclaimAddsNoMatchingNote() {
    // given
    var paramX = new ParameterDescriptor(
      BilingualString.of("X"), TypeSet.of(NUMBER), false, BilingualString.EMPTY, "");
    var sig = new SignatureDescriptor(List.of(paramX), TypeSet.EMPTY, "");

    // when
    var content = builder.build("Структура", STRUCTURE, sig, List.of(sig), true, "", FileType.BSL);

    // then
    assertThat(content.getValue()).contains("[noMatchingConstructor]");
  }

  @Test
  void buildFallsBackToClassDescriptionWhenTypeServiceEmpty() {
    // given
    when(typeService.getDescription(eq(STRUCTURE), any(Language.class), any(FileType.class))).thenReturn("");

    // when
    var content = builder.build("Структура", STRUCTURE, null, List.of(),
      false, "fallback-описание", FileType.BSL);

    // then
    assertThat(content.getValue()).contains("fallback-описание");
  }

  @Test
  void buildPrefersTypeServiceDescriptionOverFallback() {
    // given
    when(typeService.getDescription(eq(STRUCTURE), any(Language.class), any(FileType.class)))
      .thenReturn("из реестра");

    // when
    var content = builder.build("Структура", STRUCTURE, null, List.of(),
      false, "fallback", FileType.BSL);

    // then
    assertThat(content.getValue())
      .contains("из реестра")
      .doesNotContain("fallback");
  }

  @Test
  void buildRendersOptionalParameterWithDefaultValue() {
    // given
    var param = new ParameterDescriptor(
      BilingualString.of("Опц"), TypeSet.of(NUMBER), true,
      BilingualString.EMPTY, "42");
    var sig = new SignatureDescriptor(List.of(param), TypeSet.EMPTY, "");

    // when
    var content = builder.build("X", STRUCTURE, sig, List.of(sig), false, "", FileType.BSL);

    // then
    var value = content.getValue();
    assertThat(value)
      .contains("`Опц`: Число?")
      .doesNotContain("[optionalParameter]")
      .contains("= 42");
  }

  @Test
  void buildUsesProvidedTypeNameWhenRegistryReturnsBlank() {
    // given
    when(typeService.displayName(eq(STRUCTURE), any(Language.class))).thenReturn("");

    // when
    var content = builder.build("CustomName", STRUCTURE, null, List.of(), false, "", FileType.BSL);

    // then
    assertThat(content.getValue()).contains("CustomName");
  }

  @Test
  void buildRendersTypePageMetadata() {
    // given — «страничные» метаданные самого типа из синтакс-помощника.
    when(typeService.getTypeMetadata(eq(STRUCTURE), any(FileType.class))).thenReturn(
      new PlatformMetadata(
        "8.0", "8.2", List.of("ТаблицаФормы"),
        Set.of(Availability.THICK_CLIENT), null,
        BilingualString.EMPTY, BilingualString.of("замечание типа"),
        List.of(BilingualString.of("Пример типа")),
        List.of(BilingualString.of("См.ТаблицаФормы"))));

    // when
    var content = builder.build("Структура", STRUCTURE, null, List.of(), false, "", FileType.BSL);

    // then
    var value = content.getValue();
    assertThat(value)
      .contains("[sinceVersion]").contains("8.0")
      .contains("[deprecatedSince]").contains("8.2")
      .contains("[recommendedReplacements]").contains("`ТаблицаФормы`")
      .contains("[availabilities]").contains("[availability.THICK_CLIENT]")
      .contains("[notes]").contains("замечание типа")
      .contains("Пример типа")
      .contains("См.ТаблицаФормы");
  }

  @Test
  void buildRendersConstructorMetadata() {
    // given — у самого конструктора своя страница СП: версии, примеры, «См. также».
    var ctorMetadata = new PlatformMetadata(
      "8.3.10", "", List.of(),
      Set.of(), null,
      BilingualString.EMPTY, BilingualString.EMPTY,
      List.of(BilingualString.of("Стр = Новый Структура();")),
      List.of());
    var sig = new SignatureDescriptor(List.of(), TypeSet.EMPTY, BilingualString.EMPTY, ctorMetadata);

    // when
    var content = builder.build("Структура", STRUCTURE, sig, List.of(sig), false, "", FileType.BSL);

    // then
    assertThat(content.getValue())
      .contains("[sinceVersion]").contains("8.3.10")
      .contains("Стр = Новый Структура();");
  }
}
