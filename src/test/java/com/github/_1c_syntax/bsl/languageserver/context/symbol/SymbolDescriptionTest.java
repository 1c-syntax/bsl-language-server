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
package com.github._1c_syntax.bsl.languageserver.context.symbol;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SymbolDescriptionTest {

  @Test
  void emptyForNullOrBlankDescriptionUsesSingleton() {
    // given / when / then
    assertThat(SymbolDescription.of(null)).isSameAs(SymbolDescription.EMPTY);
    assertThat(SymbolDescription.of("")).isSameAs(SymbolDescription.EMPTY);
    assertThat(SymbolDescription.of("   ")).isSameAs(SymbolDescription.EMPTY);
  }

  @Test
  void emptyInstanceReturnsEmptyStringsAndIsNotDeprecated() {
    // given
    var empty = SymbolDescription.EMPTY;

    // when / then
    assertThat(empty.getPurposeDescription()).isEmpty();
    assertThat(empty.getDeprecationInfo()).isEmpty();
    assertThat(empty.isDeprecated()).isFalse();
    assertThat(empty.isEmpty()).isTrue();
  }

  @Test
  void simpleFactoryStoresDescriptionAndIsNotDeprecated() {
    // given / when
    var description = SymbolDescription.of("текст описания");

    // then
    assertThat(description.getPurposeDescription()).isEqualTo("текст описания");
    assertThat(description.isDeprecated()).isFalse();
    assertThat(description.getDeprecationInfo()).isEmpty();
    assertThat(description.isEmpty()).isFalse();
  }

  @Test
  void deprecatedFactoryStoresFlagAndInfo() {
    // given / when
    var description = SymbolDescription.of("description", true, "use Y instead");

    // then
    assertThat(description.getPurposeDescription()).isEqualTo("description");
    assertThat(description.isDeprecated()).isTrue();
    assertThat(description.getDeprecationInfo()).isEqualTo("use Y instead");
    assertThat(description.isEmpty()).isFalse();
  }

  @Test
  void deprecatedFactoryNormalizesNullsToEmpty() {
    // given / when
    var description = SymbolDescription.of(null, true, null);

    // then
    assertThat(description.getPurposeDescription()).isEmpty();
    assertThat(description.getDeprecationInfo()).isEmpty();
    assertThat(description.isDeprecated()).isTrue();
    assertThat(description.isEmpty()).isFalse();
  }

  @Test
  void deprecatedFactoryReturnsEmptyWhenAllArgsBlankAndNotDeprecated() {
    // given / when / then
    assertThat(SymbolDescription.of("", false, "")).isSameAs(SymbolDescription.EMPTY);
    assertThat(SymbolDescription.of(null, false, null)).isSameAs(SymbolDescription.EMPTY);
  }

  @Test
  void isEmptyHonorsDeprecationFlagEvenWithoutText() {
    // given / when
    var description = SymbolDescription.of("", true, "");

    // then
    assertThat(description.isEmpty()).isFalse();
  }
}
