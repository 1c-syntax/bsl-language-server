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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CheckedWordsHolderTest {

  @Autowired
  private CheckedWordsHolder checkedWordsHolder;

  @BeforeAll
  static void cleanupCache() throws IOException {
    var cacheDir = Path.of(".", ".bsl-ls-cache");
    if (Files.exists(cacheDir)) {
      try (var stream = Files.walk(cacheDir)) {
        stream.sorted(Comparator.reverseOrder())
          .forEach(path -> {
            try {
              Files.delete(path);
            } catch (IOException e) {
              // Ignore cleanup errors
            }
          });
      }
    }
  }

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

  @Test
  void testPutIfAbsent() {
    // Test that putIfAbsent behaves like Map.putIfAbsent
    String lang = "en";
    String word1 = "putifabsent1" + System.nanoTime();
    String word2 = "putifabsent2" + System.nanoTime();

    // Put word with HAS_ERROR
    checkedWordsHolder.putWordStatus(lang, word1, true);
    assertThat(checkedWordsHolder.getWordStatus(lang, word1)).isEqualTo(WordStatus.HAS_ERROR);

    // Try to put with NO_ERROR using putIfAbsent - should not change
    checkedWordsHolder.putWordStatusIfAbsent(lang, word1, false);
    assertThat(checkedWordsHolder.getWordStatus(lang, word1)).isEqualTo(WordStatus.HAS_ERROR);

    // For a new word, putIfAbsent should work
    checkedWordsHolder.putWordStatusIfAbsent(lang, word2, false);
    assertThat(checkedWordsHolder.getWordStatus(lang, word2)).isEqualTo(WordStatus.NO_ERROR);
  }
}
