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

import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * Component for managing persistent cache of checked words for typo diagnostic.
 * Uses Spring Cache with EhCache for persistent disk storage.
 */
@Component
public class CheckedWordsHolder {

  /**
   * Get the status of a word from cache.
   *
   * @param lang language code ("en" or "ru")
   * @param word the word to get status for
   * @return WordStatus indicating if the word has an error, no error, or is missing from cache
   */
  @Cacheable(value = "typoCache", key = "#lang + ':' + #word", cacheManager = "typoCacheManager")
  public WordStatus getWordStatus(String lang, String word) {
    return WordStatus.MISSING;
  }

  /**
   * Store the status of a word in the cache.
   *
   * @param lang language code ("en" or "ru")
   * @param word the word to store status for
   * @param hasError true if the word has a typo, false otherwise
   * @return the stored WordStatus
   */
  @CachePut(value = "typoCache", key = "#lang + ':' + #word", cacheManager = "typoCacheManager")
  public WordStatus putWordStatus(String lang, String word, boolean hasError) {
    return hasError ? WordStatus.HAS_ERROR : WordStatus.NO_ERROR;
  }
}
