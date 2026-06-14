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
package com.github._1c_syntax.bsl.languageserver.rename;

import com.github._1c_syntax.bsl.parser.BSLLexer;
import com.github._1c_syntax.bsl.parser.BSLTokenizer;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Проверяет, является ли новое имя символа допустимым идентификатором BSL.
 * <p>
 * Инкапсулирует лексический разбор имени токенизатором BSL, чтобы провайдер переименования
 * не зависел напрямую от классов лексера и токенизатора. Допустимым считается имя, разбираемое
 * ровно в один токен-идентификатор (плюс служебный токен конца потока), текст которого совпадает
 * с исходным именем целиком.
 */
@Component
public class NewNameValidator {

  /**
   * Проверяет, является ли переданное имя допустимым идентификатором BSL.
   * <p>
   * Имя должно быть непустым и при лексическом разборе давать ровно один токен-идентификатор,
   * текст которого полностью совпадает с исходным именем (без хвостовых символов, не вошедших
   * в идентификатор).
   *
   * @param newName Проверяемое новое имя символа; {@code null} и пустая строка считаются
   *                недопустимыми.
   * @return {@code true}, если имя является допустимым идентификатором BSL.
   */
  public boolean isValidIdentifier(@Nullable String newName) {
    if (newName == null || newName.isEmpty()) {
      return false;
    }

    var tokens = new BSLTokenizer(newName).getTokens();

    return tokens.size() == 2
      && tokens.get(0).getType() == BSLLexer.IDENTIFIER
      && newName.equals(tokens.get(0).getText());
  }

}
