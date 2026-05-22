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

import static org.assertj.core.api.Assertions.assertThat;

class BilingualStringTest {

  @Test
  void canonicalConstructorNormalizesNullsToEmptyStrings() {
    // given / when
    var bs = new BilingualString(null, null);

    // then
    assertThat(bs.ru()).isEmpty();
    assertThat(bs.en()).isEmpty();
    assertThat(bs.isEmpty()).isTrue();
  }

  @Test
  void emptySingletonIsReusedFromBothNullsAndEmpties() {
    // given / when / then
    assertThat(BilingualString.of((String) null)).isSameAs(BilingualString.EMPTY);
    assertThat(BilingualString.of("")).isSameAs(BilingualString.EMPTY);
    assertThat(BilingualString.of(null, null)).isSameAs(BilingualString.EMPTY);
    assertThat(BilingualString.of("", "")).isSameAs(BilingualString.EMPTY);
  }

  @Test
  void singleLocaleOfPutsValueIntoRuAndKeepsEnEmpty() {
    // given / when
    var bs = BilingualString.of("Истина");

    // then
    assertThat(bs.ru()).isEqualTo("Истина");
    assertThat(bs.en()).isEmpty();
    assertThat(bs.isEmpty()).isFalse();
  }

  @Test
  void forLanguageReturnsRequestedLocaleWhenBothAreNonEmpty() {
    // given
    var bs = BilingualString.of("Истина", "True");

    // when / then
    assertThat(bs.forLanguage(Language.RU)).isEqualTo("Истина");
    assertThat(bs.forLanguage(Language.EN)).isEqualTo("True");
  }

  @Test
  void forLanguageFallsBackToOtherLocaleWhenRequestedIsEmpty() {
    // given
    var onlyRu = BilingualString.of("Истина", "");
    var onlyEn = BilingualString.of("", "True");

    // when / then
    assertThat(onlyRu.forLanguage(Language.EN)).isEqualTo("Истина");
    assertThat(onlyEn.forLanguage(Language.RU)).isEqualTo("True");
  }

  @Test
  void forLanguageReturnsEmptyWhenBothLocalesAreEmpty() {
    // given / when / then
    assertThat(BilingualString.EMPTY.forLanguage(Language.RU)).isEmpty();
    assertThat(BilingualString.EMPTY.forLanguage(Language.EN)).isEmpty();
  }

  @Test
  void primaryPrefersRuOverEn() {
    // given / when / then
    assertThat(BilingualString.of("Истина", "True").primary()).isEqualTo("Истина");
    assertThat(BilingualString.of("", "True").primary()).isEqualTo("True");
    assertThat(BilingualString.EMPTY.primary()).isEmpty();
  }

  @Test
  void matchesIsCaseInsensitiveAcrossBothLocales() {
    // given
    var bs = BilingualString.of("Истина", "True");

    // when / then
    assertThat(bs.matches("истина")).isTrue();
    assertThat(bs.matches("ИСТИНА")).isTrue();
    assertThat(bs.matches("true")).isTrue();
    assertThat(bs.matches("TRUE")).isTrue();
    assertThat(bs.matches("False")).isFalse();
  }

  @Test
  void matchesReturnsFalseForNullOrEmptyCandidate() {
    // given
    var bs = BilingualString.of("Истина", "True");

    // when / then
    assertThat(bs.matches(null)).isFalse();
    assertThat(bs.matches("")).isFalse();
  }

  @Test
  void matchesIgnoresEmptyLocaleSide() {
    // given
    var onlyRu = BilingualString.of("Истина", "");

    // when / then
    assertThat(onlyRu.matches("")).isFalse();
    assertThat(onlyRu.matches("Истина")).isTrue();
  }
}
