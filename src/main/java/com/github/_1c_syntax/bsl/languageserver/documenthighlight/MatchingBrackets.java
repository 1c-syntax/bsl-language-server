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
package com.github._1c_syntax.bsl.languageserver.documenthighlight;

import lombok.experimental.UtilityClass;
import org.antlr.v4.runtime.Token;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Поиск парной скобки в потоке токенов с учётом вложенности.
 */
@UtilityClass
class MatchingBrackets {

  /**
   * Парная скобка для скобки с индексом {@code bracketIndex}: для открывающей — закрывающая
   * впереди, для закрывающей — открывающая позади; скобки между ними учитываются по вложенности.
   *
   * @param tokens       поток токенов; индекс в нём совпадает с {@link Token#getTokenIndex()}
   * @param bracketIndex индекс скобки в потоке
   * @param openType     тип токена открывающей скобки
   * @param closeType    тип токена закрывающей скобки
   * @return парная скобка; {@code null}, если её нет или по индексу стоит не скобка этого вида
   */
  @Nullable
  Token find(List<? extends Token> tokens, int bracketIndex, int openType, int closeType) {
    var bracketType = tokens.get(bracketIndex).getType();
    int step;
    if (bracketType == openType) {
      step = 1;
    } else if (bracketType == closeType) {
      step = -1;
    } else {
      return null;
    }

    var depth = 0;
    for (var i = bracketIndex; i >= 0 && i < tokens.size(); i += step) {
      var token = tokens.get(i);
      var type = token.getType();
      if (type == bracketType) {
        depth++;
      } else if (type == openType || type == closeType) {
        depth--;
        if (depth == 0) {
          return token;
        }
      }
    }
    return null;
  }
}
