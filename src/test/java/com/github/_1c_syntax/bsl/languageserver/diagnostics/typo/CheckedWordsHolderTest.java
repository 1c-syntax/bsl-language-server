/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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
package com.github._1c_syntax.bsl.languageserver.diagnostics.typo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CheckedWordsHolderTest {

  @Autowired
  private CheckedWordsHolder checkedWordsHolder;

  @Test
  void testContainsWord() {
    // given
    String lang = "en";
    String word = "testword";

    // when - word not yet added
    boolean containsBefore = checkedWordsHolder.containsWord(lang, word);

    // then
    assertThat(containsBefore).isFalse();

    // when - add word
    checkedWordsHolder.putWordStatus(lang, word, true);
    boolean containsAfter = checkedWordsHolder.containsWord(lang, word);

    // then
    assertThat(containsAfter).isTrue();
  }

  @Test
  void testPutAndGetWordStatus() {
    // given
    String lang = "ru";
    String wordWithError = "ошибк";
    String wordWithoutError = "правильно";

    // when
    checkedWordsHolder.putWordStatus(lang, wordWithError, true);
    checkedWordsHolder.putWordStatus(lang, wordWithoutError, false);

    // then
    assertThat(checkedWordsHolder.getWordStatus(lang, wordWithError)).isTrue();
    assertThat(checkedWordsHolder.getWordStatus(lang, wordWithoutError)).isFalse();
  }

  @Test
  void testGetWordStatusNotFound() {
    // given
    String lang = "en";
    String unknownWord = "unknownword123";

    // when
    Boolean status = checkedWordsHolder.getWordStatus(lang, unknownWord);

    // then
    assertThat(status).isNull();
  }

  @Test
  void testLanguageSeparation() {
    // given
    String word = "test";

    // when
    checkedWordsHolder.putWordStatus("en", word, true);
    checkedWordsHolder.putWordStatus("ru", word, false);

    // then
    assertThat(checkedWordsHolder.getWordStatus("en", word)).isTrue();
    assertThat(checkedWordsHolder.getWordStatus("ru", word)).isFalse();
  }

  @Test
  void testCacheAnnotations() {
    // Test that Spring Cache annotations work
    // given
    String lang = "en";
    String word = "cachetest";

    // when - first call should cache the result
    checkedWordsHolder.putWordStatus(lang, word, true);
    Boolean firstGet = checkedWordsHolder.getWordStatus(lang, word);
    Boolean secondGet = checkedWordsHolder.getWordStatus(lang, word);

    // then - both calls should return the same result
    assertThat(firstGet).isTrue();
    assertThat(secondGet).isTrue();
  }
}
