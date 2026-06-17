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
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.utils.Resources;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CollectionHoverHintsTest {

  private static final TypeRef ARRAY = new TypeRef(TypeKind.PLATFORM, "Массив");
  private static final FileType FT = FileType.BSL;

  @Mock
  private Resources resources;
  @Mock
  private LanguageServerConfiguration configuration;
  @Mock
  private TypeService typeService;

  private CollectionHoverHints hints;

  @BeforeEach
  void setUp() {
    hints = new CollectionHoverHints(resources, configuration);
    when(configuration.getLanguage()).thenReturn(Language.RU);
    when(resources.getResourceString(eq(CollectionHoverHints.class), any(String.class)))
      .thenAnswer(inv -> "[" + inv.getArgument(1) + "]");
  }

  @Test
  void appendNoBlocksWhenNeitherForEachNorIndexSupported() {
    // given
    when(typeService.supportsForEach(ARRAY, FT)).thenReturn(false);
    when(typeService.supportsIndexAccess(ARRAY, FT)).thenReturn(false);
    var sb = new StringBuilder("PREFIX");

    // when
    hints.append(sb, ARRAY, FT, typeService);

    // then
    assertThat(sb.toString()).isEqualTo("PREFIX");
  }

  @Test
  void appendForEachBlockWithRegistryDescription() {
    // given
    when(typeService.supportsForEach(ARRAY, FT)).thenReturn(true);
    when(typeService.supportsIndexAccess(ARRAY, FT)).thenReturn(false);
    when(typeService.getForEachDescription(ARRAY, FT, Language.RU)).thenReturn("обход массива");
    var sb = new StringBuilder();

    // when
    hints.append(sb, ARRAY, FT, typeService);

    // then
    assertThat(sb.toString())
      .contains("[forEachLabel]")
      .contains("обход массива")
      .doesNotContain("[indexAccessLabel]");
  }

  @Test
  void appendForEachBlockWithFallbackWhenRegistryEmpty() {
    // given
    when(typeService.supportsForEach(ARRAY, FT)).thenReturn(true);
    when(typeService.supportsIndexAccess(ARRAY, FT)).thenReturn(false);
    when(typeService.getForEachDescription(ARRAY, FT, Language.RU)).thenReturn("");
    var sb = new StringBuilder();

    // when
    hints.append(sb, ARRAY, FT, typeService);

    // then
    assertThat(sb.toString())
      .contains("[forEachLabel]")
      .contains("[forEachFallback]");
  }

  @Test
  void appendIndexAccessBlockWithRegistryDescription() {
    // given
    when(typeService.supportsForEach(ARRAY, FT)).thenReturn(false);
    when(typeService.supportsIndexAccess(ARRAY, FT)).thenReturn(true);
    when(typeService.getIndexAccessDescription(ARRAY, FT, Language.RU)).thenReturn("по индексу");
    var sb = new StringBuilder();

    // when
    hints.append(sb, ARRAY, FT, typeService);

    // then
    assertThat(sb.toString())
      .contains("[indexAccessLabel]")
      .contains("по индексу")
      .doesNotContain("[forEachLabel]");
  }

  @Test
  void appendIndexAccessBlockWithFallbackWhenRegistryEmpty() {
    // given
    when(typeService.supportsForEach(ARRAY, FT)).thenReturn(false);
    when(typeService.supportsIndexAccess(ARRAY, FT)).thenReturn(true);
    when(typeService.getIndexAccessDescription(ARRAY, FT, Language.RU)).thenReturn("");
    var sb = new StringBuilder();

    // when
    hints.append(sb, ARRAY, FT, typeService);

    // then
    assertThat(sb.toString())
      .contains("[indexAccessLabel]")
      .contains("[indexAccessFallback]");
  }

  @Test
  void appendBothBlocksWhenBothSupported() {
    // given
    when(typeService.supportsForEach(ARRAY, FT)).thenReturn(true);
    when(typeService.supportsIndexAccess(ARRAY, FT)).thenReturn(true);
    when(typeService.getForEachDescription(ARRAY, FT, Language.RU)).thenReturn("forEach-text");
    when(typeService.getIndexAccessDescription(ARRAY, FT, Language.RU)).thenReturn("index-text");
    var sb = new StringBuilder();

    // when
    hints.append(sb, ARRAY, FT, typeService);

    // then
    assertThat(sb.toString())
      .contains("[forEachLabel]").contains("forEach-text")
      .contains("[indexAccessLabel]").contains("index-text");
  }

  @Test
  void appendIsNoOpForNullArgs() {
    // given
    var sb = new StringBuilder("X");

    // when
    hints.append(null, ARRAY, FT, typeService);
    hints.append(sb, null, FT, typeService);
    hints.append(sb, ARRAY, FT, null);

    // then
    assertThat(sb.toString()).isEqualTo("X");
  }
}
