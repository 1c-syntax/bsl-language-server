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

import com.github._1c_syntax.bsl.languageserver.context.FileType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LanguageScopeTest {

  @Test
  void matchesBslFileTypeOnlyForBslAndBoth() {
    // given
    var bslFile = FileType.BSL;

    // when / then
    assertThat(LanguageScope.BSL.matches(bslFile)).isTrue();
    assertThat(LanguageScope.OS.matches(bslFile)).isFalse();
    assertThat(LanguageScope.BOTH.matches(bslFile)).isTrue();
  }

  @Test
  void matchesOsFileTypeOnlyForOsAndBoth() {
    // given
    var osFile = FileType.OS;

    // when / then
    assertThat(LanguageScope.OS.matches(osFile)).isTrue();
    assertThat(LanguageScope.BSL.matches(osFile)).isFalse();
    assertThat(LanguageScope.BOTH.matches(osFile)).isTrue();
  }

  @Test
  void forFileTypeMapsToProperScope() {
    // given / when / then
    assertThat(LanguageScope.forFileType(FileType.BSL)).isEqualTo(LanguageScope.BSL);
    assertThat(LanguageScope.forFileType(FileType.OS)).isEqualTo(LanguageScope.OS);
  }

  @Test
  void mergeWithSelfReturnsSelf() {
    // given / when / then
    assertThat(LanguageScope.BSL.merge(LanguageScope.BSL)).isEqualTo(LanguageScope.BSL);
    assertThat(LanguageScope.OS.merge(LanguageScope.OS)).isEqualTo(LanguageScope.OS);
    assertThat(LanguageScope.BOTH.merge(LanguageScope.BOTH)).isEqualTo(LanguageScope.BOTH);
  }

  @Test
  void mergeDifferentScopesGivesBoth() {
    // given / when / then
    assertThat(LanguageScope.BSL.merge(LanguageScope.OS)).isEqualTo(LanguageScope.BOTH);
    assertThat(LanguageScope.OS.merge(LanguageScope.BSL)).isEqualTo(LanguageScope.BOTH);
    assertThat(LanguageScope.BSL.merge(LanguageScope.BOTH)).isEqualTo(LanguageScope.BOTH);
  }
}
