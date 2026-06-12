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
package com.github._1c_syntax.bsl.languageserver.types.registry;

import org.junit.jupiter.api.Test;

import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Edge-case покрытие {@link KeywordMetadataLoader}: отсутствующий ресурс,
 * не-JSON-объект на корне, не-список или не-Map записи в {@code keywords}.
 */
class KeywordMetadataLoaderTest {

  private static final Predicate<String> ALLOW_ALL = c -> true;

  @Test
  void missingResourceReturnsEmpty() {
    var result = KeywordMetadataLoader.load(
      "no/such/resource.json", ALLOW_ALL);

    assertThat(result).isSameAs(KeywordMetadata.EMPTY);
  }

  @Test
  void keywordsFieldNotAListIgnored() {

    // given — поле "keywords" в JSON — строка, а не массив.
    var result = KeywordMetadataLoader.load(
      "keyword-fallback/keywords-not-a-list.json", ALLOW_ALL);

    assertThat(result.keywords()).isEmpty();
    assertThat(result.snippets()).isEmpty();
  }

  @Test
  void mixedEntriesFilterOutNonMaps() {

    // given — массив "keywords" содержит строку, число, null и одну валидную
    // запись-Map: должна остаться только последняя.
    var result = KeywordMetadataLoader.load(
      "keyword-fallback/keywords-mixed-entries.json", ALLOW_ALL);

    assertThat(result.keywords()).containsExactly("Если", "If");
    assertThat(result.snippets()).containsOnlyKeys("если", "if");
  }
}
