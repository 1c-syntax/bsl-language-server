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
package com.github._1c_syntax.bsl.languageserver.diagnostics.typo;

import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * Компонент для управления постоянным кэшем проверенных слов для диагностики опечаток.
 * Использует Spring Cache с EhCache для хранения кэша на диске.
 */
@Component
public class CheckedWordsHolder {

  /**
   * Получает статус слова из кэша.
   *
   * @param lang код языка ("en" или "ru")
   * @param word слово, статус которого запрашивается
   * @return WordStatus, указывающий, есть ли у слова ошибка, отсутствует ли ошибка, или слово отсутствует в кэше
   */
  @Cacheable(value = "typoCache", key = "#lang + ':' + #word", cacheManager = "typoCacheManager")
  public WordStatus getWordStatus(String lang, String word) {
    return WordStatus.MISSING;
  }

  /**
   * Помечает слово как содержащее ошибку в кэше.
   *
   * @param lang код языка ("en" или "ru")
   * @param word слово, которое помечается как содержащее ошибку
   * @return сохранённый WordStatus, указывающий на наличие ошибки
   */
  @CachePut(value = "typoCache", key = "#lang + ':' + #word", cacheManager = "typoCacheManager")
  public WordStatus markWordAsError(String lang, String word) {
    return WordStatus.HAS_ERROR;
  }

  /**
   * Помечает слово как не содержащее ошибку в кэше.
   *
   * @param lang код языка ("en" или "ru")
   * @param word слово, которое помечается как не содержащее ошибку
   * @return сохранённый WordStatus, указывающий на отсутствие ошибки
   */
  @CachePut(value = "typoCache", key = "#lang + ':' + #word", cacheManager = "typoCacheManager")
  public WordStatus markWordAsNoError(String lang, String word) {
    return WordStatus.NO_ERROR;
  }
}
