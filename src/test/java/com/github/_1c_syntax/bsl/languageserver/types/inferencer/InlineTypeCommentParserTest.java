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
package com.github._1c_syntax.bsl.languageserver.types.inferencer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InlineTypeCommentParserTest {

  @Test
  void parseSingleType() {
    assertThat(InlineTypeCommentParser.parseTypeNames("// Число"))
      .containsExactly("Число");
  }

  @Test
  void parseSingleTypeWithDashDescription() {
    assertThat(InlineTypeCommentParser.parseTypeNames("// Число - сумма"))
      .containsExactly("Число");
  }

  @Test
  void parseSingleTypeWithTrailingDash() {
    assertThat(InlineTypeCommentParser.parseTypeNames("// Число -"))
      .containsExactly("Число");
  }

  @Test
  void parseMultipleTypes() {
    assertThat(InlineTypeCommentParser.parseTypeNames("// Число, Строка"))
      .containsExactly("Число", "Строка");
  }

  @Test
  void parseQualifiedTypeName() {
    assertThat(InlineTypeCommentParser.parseTypeNames("// СправочникСсылка.Товары"))
      .containsExactly("СправочникСсылка.Товары");
  }

  @Test
  void parseHandlesSpacesAndExtraDashes() {
    assertThat(InlineTypeCommentParser.parseTypeNames("//  Число , Строка - какое-то описание"))
      .containsExactly("Число", "Строка");
  }

  @Test
  void parseEmptyOrNull() {
    assertThat(InlineTypeCommentParser.parseTypeNames(null)).isEmpty();
    assertThat(InlineTypeCommentParser.parseTypeNames("")).isEmpty();
    assertThat(InlineTypeCommentParser.parseTypeNames("//")).isEmpty();
    assertThat(InlineTypeCommentParser.parseTypeNames("//   ")).isEmpty();
  }

  @Test
  void parseSeeRefReturnsEmpty() {
    assertThat(InlineTypeCommentParser.parseTypeNames("// См. Модуль.Метод"))
      .isEmpty();
    assertThat(InlineTypeCommentParser.parseTypeNames("// see. Module.Method"))
      .isEmpty();
  }

  @Test
  void parseSkipsNonIdentifierTokens() {
    assertThat(InlineTypeCommentParser.parseTypeNames("// 123, Число, $bad, Строка"))
      .containsExactly("Число", "Строка");
  }

  @Test
  void parseIdentifierWithUnderscore() {
    assertThat(InlineTypeCommentParser.parseTypeNames("// My_Type, Another_One"))
      .containsExactly("My_Type", "Another_One");
  }

  @Test
  void parseRejectsInvalidCharsInIdentifier() {
    assertThat(InlineTypeCommentParser.parseTypeNames("// Bad-Type"))
      .isEmpty();
  }

  @Test
  void parseStopsAtSpaceSurroundedDash() {
    // Тире внутри типа (без пробелов) не должно его обрезать.
    assertThat(InlineTypeCommentParser.parseTypeNames("// Foo-Bar"))
      .isEmpty();  // отклоняется как невалидный identifier
  }
}
