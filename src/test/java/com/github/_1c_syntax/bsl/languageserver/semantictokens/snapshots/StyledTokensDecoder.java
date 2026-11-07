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
package com.github._1c_syntax.bsl.languageserver.semantictokens.snapshots;

import lombok.experimental.UtilityClass;
import org.eclipse.lsp4j.SemanticTokensLegend;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Декодирует массив {@code data} семантических токенов LSP в токены с абсолютными позициями
 * и расшифрованными по легенде типом и модификаторами.
 */
@UtilityClass
public class StyledTokensDecoder {

  /**
   * @param data   массив {@code SemanticTokens.data} в относительной кодировке LSP
   * @param legend легенда, по которой закодированы типы и модификаторы
   * @return токены в порядке следования в {@code data}
   */
  public static List<StyledToken> decode(List<Integer> data, SemanticTokensLegend legend) {
    var tokens = new ArrayList<StyledToken>(data.size() / 5);
    int line = 0;
    int start = 0;
    for (int i = 0; i + 4 < data.size(); i += 5) {
      int deltaLine = data.get(i);
      int deltaStart = data.get(i + 1);
      line += deltaLine;
      start = deltaLine == 0 ? start + deltaStart : deltaStart;
      tokens.add(new StyledToken(
        line,
        start,
        data.get(i + 2),
        legend.getTokenTypes().get(data.get(i + 3)),
        modifiers(data.get(i + 4), legend.getTokenModifiers())
      ));
    }
    return tokens;
  }

  private static Set<String> modifiers(int bitmask, List<String> legendModifiers) {
    var modifiers = new HashSet<String>();
    for (int bit = 0; bit < legendModifiers.size(); bit++) {
      if ((bitmask & (1 << bit)) != 0) {
        modifiers.add(legendModifiers.get(bit));
      }
    }
    return Set.copyOf(modifiers);
  }
}
