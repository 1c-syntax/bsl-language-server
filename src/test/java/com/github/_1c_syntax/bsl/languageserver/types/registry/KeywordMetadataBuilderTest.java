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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Edge-case покрытие {@link KeywordMetadataBuilder}: пустые/мусорные записи
 * не должны попадать в {@link KeywordMetadata}, валидные — попадают по обоим
 * написаниям.
 */
class KeywordMetadataBuilderTest {

  private static final Predicate<String> ALLOW_ALL = c -> true;
  private static final Predicate<String> DENY_ALL = c -> false;

  @Test
  void emptyBuilderProducesEmptyMetadata() {
    var builder = new KeywordMetadataBuilder(ALLOW_ALL);

    var result = builder.build();

    assertThat(result.keywords()).isEmpty();
    assertThat(result.snippets()).isEmpty();
    assertThat(result.descriptions()).isEmpty();
  }

  @Test
  void blankNameSkipped() {
    var builder = new KeywordMetadataBuilder(ALLOW_ALL);

    builder.add(Map.of("name", ""));
    builder.add(Map.of("alias", "OnlyEn"));

    assertThat(builder.build().keywords()).isEmpty();
  }

  @Test
  void categoryFilterExcludesKeywordFromCompletionButIndexesDescription() {

    // given — фильтр пропускает только LITERAL, наша запись STATEMENT,
    // плюс есть description: имя не попадает в completion, описание — в индекс.
    Predicate<String> onlyLiteral = "LITERAL"::equals;
    var builder = new KeywordMetadataBuilder(onlyLiteral);

    builder.add(Map.of(
      "name", "Пока",
      "alias", "While",
      "category", "STATEMENT",
      "description", "Цикл по условию"));

    var result = builder.build();
    assertThat(result.keywords()).isEmpty();
    assertThat(result.descriptions()).containsKey("пока").containsKey("while");
  }

  @Test
  void entryWithoutAliasIndexedByRuOnly() {
    var builder = new KeywordMetadataBuilder(ALLOW_ALL);

    builder.add(Map.of(
      "name", "ТолькоРу",
      "category", "STATEMENT",
      "snippet", Map.of("ru", "ТолькоРу <?>;")));

    var result = builder.build();
    assertThat(result.keywords()).containsExactly("ТолькоРу");
    assertThat(result.snippets()).containsOnlyKeys("толькору");
  }

  @Test
  void emptySnippetIsNotIndexed() {
    var builder = new KeywordMetadataBuilder(ALLOW_ALL);

    builder.add(Map.of(
      "name", "Если",
      "category", "STATEMENT",
      "snippet", Map.of("ru", "", "en", "")));

    assertThat(builder.build().snippets()).isEmpty();
  }

  @Test
  void emptyDescriptionIsNotIndexed() {
    var builder = new KeywordMetadataBuilder(ALLOW_ALL);

    builder.add(Map.of("name", "Безописания", "category", "STATEMENT"));

    assertThat(builder.build().descriptions()).isEmpty();
  }

  @Test
  void descriptionByParentIndexedBilingually() {
    var builder = new KeywordMetadataBuilder(DENY_ALL);

    var byParent = new LinkedHashMap<String, Map<String, String>>();
    byParent.put("Процедура", Map.of("ru", "в процедуре", "en", "in procedure"));
    byParent.put("Функция", Map.of("ru", "в функции", "en", "in function"));
    builder.add(Map.of(
      "name", "Возврат",
      "alias", "Return",
      "category", "STATEMENT",
      "descriptionByParent", byParent));

    var result = builder.build();
    var description = result.descriptions().get("возврат");
    assertThat(description).isNotNull();
    assertThat(description.forContext("Процедура").ru()).isEqualTo("в процедуре");
    assertThat(description.forContext("Функция").ru()).isEqualTo("в функции");
    // Тот же descriptor доступен по en-имени
    assertThat(result.descriptions().get("return")).isSameAs(description);
  }

  @Test
  void descriptionByParentSkipsBlankAndMalformedEntries() {
    var builder = new KeywordMetadataBuilder(ALLOW_ALL);

    var byParent = new LinkedHashMap<String, Object>();
    byParent.put("Процедура", Map.of("ru", "в процедуре"));
    byParent.put("Функция", Map.of("ru", "", "en", ""));   // оба пусты — пропускается
    byParent.put("Поток", "не-map");                                // не Map — пропускается
    builder.add(Map.of(
      "name", "Возврат",
      "category", "STATEMENT",
      "descriptionByParent", byParent));

    var description = builder.build().descriptions().get("возврат");
    assertThat(description).isNotNull();
    assertThat(description.forContext("Процедура").ru()).isEqualTo("в процедуре");
    assertThat(description.byParent()).containsOnlyKeys("Процедура");
  }

  @Test
  void duplicateNameDeduplicates() {
    var builder = new KeywordMetadataBuilder(ALLOW_ALL);

    builder.add(Map.of("name", "Если", "alias", "If", "category", "STATEMENT"));
    builder.add(Map.of("name", "Если", "alias", "If", "category", "PREPROCESSOR_INSTRUCTION"));

    assertThat(builder.build().keywords()).containsExactly("Если", "If");
  }

  @Test
  void snippetAsNonMapTreatedAsAbsent() {

    // given — поле "snippet" не Map, а строка → snippet не индексируется.
    var builder = new KeywordMetadataBuilder(ALLOW_ALL);

    builder.add(Map.of(
      "name", "Если",
      "category", "STATEMENT",
      "snippet", "не-Map"));

    assertThat(builder.build().snippets()).isEmpty();
  }

  @Test
  void snippetMapWithNonStringValuesIgnored() {

    // given — внутри "snippet" значения — числа, а не строки. asStringMap
    // отфильтрует их, итог — пустая мапа, snippet считается отсутствующим.
    var builder = new KeywordMetadataBuilder(ALLOW_ALL);

    Map<String, Object> snippet = new LinkedHashMap<>();
    snippet.put("ru", 42);
    snippet.put("en", true);
    builder.add(Map.of("name", "Если", "category", "STATEMENT", "snippet", snippet));

    assertThat(builder.build().snippets()).isEmpty();
  }

  @Test
  void descriptionByParentNotAMapIgnored() {

    // given — поле "descriptionByParent" — строка, не Map → пропускается.
    var builder = new KeywordMetadataBuilder(ALLOW_ALL);

    builder.add(Map.of(
      "name", "Возврат",
      "category", "STATEMENT",
      "description", "primary",
      "descriptionByParent", "не-Map"));

    var description = builder.build().descriptions().get("возврат");
    assertThat(description).isNotNull();
    assertThat(description.byParent()).isEmpty();
  }
}
