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
  void testPutAndGetWordStatus() {
    // given
    String lang = "ru";
    String wordWithError = "ошибк" + System.nanoTime();
    String wordWithoutError = "правильно" + System.nanoTime();

    // when
    checkedWordsHolder.putWordStatus(lang, wordWithError, true);
    checkedWordsHolder.putWordStatus(lang, wordWithoutError, false);

    // then
    assertThat(checkedWordsHolder.getWordStatus(lang, wordWithError)).isEqualTo(WordStatus.HAS_ERROR);
    assertThat(checkedWordsHolder.getWordStatus(lang, wordWithoutError)).isEqualTo(WordStatus.NO_ERROR);
  }

  @Test
  void testGetWordStatusNotFound() {
    // given
    String lang = "en";
    String unknownWord = "unknownword" + System.nanoTime();

    // when
    WordStatus status = checkedWordsHolder.getWordStatus(lang, unknownWord);

    // then
    assertThat(status).isEqualTo(WordStatus.MISSING);
  }

  @Test
  void testLanguageSeparation() {
    // given
    String word = "test" + System.nanoTime();

    // when
    checkedWordsHolder.putWordStatus("en", word, true);
    checkedWordsHolder.putWordStatus("ru", word, false);

    // then
    assertThat(checkedWordsHolder.getWordStatus("en", word)).isEqualTo(WordStatus.HAS_ERROR);
    assertThat(checkedWordsHolder.getWordStatus("ru", word)).isEqualTo(WordStatus.NO_ERROR);
  }

  @Test
  void testCacheAnnotations() {
    // Test that Spring Cache annotations work
    // given
    String lang = "en";
    String word = "cachetest" + System.nanoTime();

    // when - first check MISSING, then put, then get
    WordStatus beforePut = checkedWordsHolder.getWordStatus(lang, word);
    checkedWordsHolder.putWordStatus(lang, word, true);
    WordStatus afterPut = checkedWordsHolder.getWordStatus(lang, word);

    // then
    assertThat(beforePut).isEqualTo(WordStatus.MISSING);
    assertThat(afterPut).isEqualTo(WordStatus.HAS_ERROR);
  }
}
