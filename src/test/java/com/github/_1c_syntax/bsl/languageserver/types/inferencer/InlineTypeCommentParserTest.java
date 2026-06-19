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
    // given
    var comment = "// Число";

    // when
    var result = InlineTypeCommentParser.parseTypeNames(comment);

    // then
    assertThat(result).containsExactly("Число");
  }

  @Test
  void parseSingleTypeWithDashDescription() {
    // given
    var comment = "// Число - сумма";

    // when
    var result = InlineTypeCommentParser.parseTypeNames(comment);

    // then
    assertThat(result).containsExactly("Число");
  }

  @Test
  void parseSingleTypeWithTrailingDash() {
    // given
    var comment = "// Число -";

    // when
    var result = InlineTypeCommentParser.parseTypeNames(comment);

    // then
    assertThat(result).containsExactly("Число");
  }

  @Test
  void parseMultipleTypes() {
    // given
    var comment = "// Число, Строка";

    // when
    var result = InlineTypeCommentParser.parseTypeNames(comment);

    // then
    assertThat(result).containsExactly("Число", "Строка");
  }

  @Test
  void parseQualifiedTypeName() {
    // given
    var comment = "// СправочникСсылка.Товары";

    // when
    var result = InlineTypeCommentParser.parseTypeNames(comment);

    // then
    assertThat(result).containsExactly("СправочникСсылка.Товары");
  }

  @Test
  void parseHandlesSpacesAndExtraDashes() {
    // given
    var comment = "//  Число , Строка - какое-то описание";

    // when
    var result = InlineTypeCommentParser.parseTypeNames(comment);

    // then
    assertThat(result).containsExactly("Число", "Строка");
  }

  @Test
  void parseEmptyOrNull() {
    // given / when / then
    assertThat(InlineTypeCommentParser.parseTypeNames(null)).isEmpty();
    assertThat(InlineTypeCommentParser.parseTypeNames("")).isEmpty();
    assertThat(InlineTypeCommentParser.parseTypeNames("//")).isEmpty();
    assertThat(InlineTypeCommentParser.parseTypeNames("//   ")).isEmpty();
  }

  @Test
  void parseSeeRefReturnsEmpty() {
    // given / when / then
    assertThat(InlineTypeCommentParser.parseTypeNames("// См. Модуль.Метод")).isEmpty();
    assertThat(InlineTypeCommentParser.parseTypeNames("// see. Module.Method")).isEmpty();
  }

  @Test
  void parseSkipsNonIdentifierTokens() {
    // given
    var comment = "// 123, Число, $bad, Строка";

    // when
    var result = InlineTypeCommentParser.parseTypeNames(comment);

    // then
    assertThat(result).containsExactly("Число", "Строка");
  }

  @Test
  void parseIdentifierWithUnderscore() {
    // given
    var comment = "// My_Type, Another_One";

    // when
    var result = InlineTypeCommentParser.parseTypeNames(comment);

    // then
    assertThat(result).containsExactly("My_Type", "Another_One");
  }

  @Test
  void parseRejectsInvalidCharsInIdentifier() {
    // given
    var comment = "// Bad-Type";

    // when
    var result = InlineTypeCommentParser.parseTypeNames(comment);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void parseCollectionNotationKeepsHeadType() {
    // нотация «Тип из ЭлементТип» — берём коллекционный тип (голову)
    assertThat(InlineTypeCommentParser.parseTypeNames("// Массив из Число"))
      .containsExactly("Массив");
    assertThat(InlineTypeCommentParser.parseTypeNames("// Соответствие из КлючИЗначение"))
      .containsExactly("Соответствие");
    assertThat(InlineTypeCommentParser.parseTypeNames("// Array of Number"))
      .containsExactly("Array");
  }

  @Test
  void parseFreeformMultiwordCommentReturnsEmpty() {
    // свободный многословный комментарий без разделителя «из»/«of» — не тип
    assertThat(InlineTypeCommentParser.parseTypeNames("// просто заметка про поле")).isEmpty();
  }
}
