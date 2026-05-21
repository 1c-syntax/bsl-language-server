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
    assertThat(SymbolDescription.of(null)).isSameAs(SymbolDescription.EMPTY);
    assertThat(SymbolDescription.of("")).isSameAs(SymbolDescription.EMPTY);
    assertThat(SymbolDescription.of("   ")).isSameAs(SymbolDescription.EMPTY);
  }

  @Test
  void emptyInstanceReturnsEmptyStringsAndIsNotDeprecated() {
    var empty = SymbolDescription.EMPTY;
    assertThat(empty.getPurposeDescription()).isEmpty();
    assertThat(empty.getDeprecationInfo()).isEmpty();
    assertThat(empty.isDeprecated()).isFalse();
    assertThat(empty.isEmpty()).isTrue();
  }

  @Test
  void simpleFactoryStoresDescriptionAndIsNotDeprecated() {
    var d = SymbolDescription.of("текст описания");
    assertThat(d.getPurposeDescription()).isEqualTo("текст описания");
    assertThat(d.isDeprecated()).isFalse();
    assertThat(d.getDeprecationInfo()).isEmpty();
    assertThat(d.isEmpty()).isFalse();
  }

  @Test
  void deprecatedFactoryStoresFlagAndInfo() {
    var d = SymbolDescription.of("description", true, "use Y instead");
    assertThat(d.getPurposeDescription()).isEqualTo("description");
    assertThat(d.isDeprecated()).isTrue();
    assertThat(d.getDeprecationInfo()).isEqualTo("use Y instead");
    assertThat(d.isEmpty()).isFalse();
  }

  @Test
  void deprecatedFactoryNormalizesNullsToEmpty() {
    var d = SymbolDescription.of(null, true, null);
    assertThat(d.getPurposeDescription()).isEmpty();
    assertThat(d.getDeprecationInfo()).isEmpty();
    assertThat(d.isDeprecated()).isTrue();
    assertThat(d.isEmpty()).isFalse();
  }

  @Test
  void deprecatedFactoryReturnsEmptyWhenAllArgsBlankAndNotDeprecated() {
    assertThat(SymbolDescription.of("", false, "")).isSameAs(SymbolDescription.EMPTY);
    assertThat(SymbolDescription.of(null, false, null)).isSameAs(SymbolDescription.EMPTY);
  }

  @Test
  void isEmptyHonorsDeprecationFlagEvenWithoutText() {
    assertThat(SymbolDescription.of("", true, "").isEmpty()).isFalse();
  }
}
