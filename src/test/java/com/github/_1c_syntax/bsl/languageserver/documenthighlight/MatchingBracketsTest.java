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

import com.github._1c_syntax.bsl.parser.BSLLexer;
import com.github._1c_syntax.bsl.parser.BSLTokenizer;
import org.antlr.v4.runtime.Token;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MatchingBracketsTest {

  @Test
  void findsPairInBothDirectionsAccountingForNesting() {
    // А = (Б + (В)) * Г[0];
    var tokens = new BSLTokenizer("А = (Б + (В)) * Г[0];").getTokens();
    var outerOpen = indexOf(tokens, BSLLexer.LPAREN, 0);
    var innerOpen = indexOf(tokens, BSLLexer.LPAREN, 1);
    var innerClose = indexOf(tokens, BSLLexer.RPAREN, 0);
    var outerClose = indexOf(tokens, BSLLexer.RPAREN, 1);

    assertThat(find(tokens, outerOpen)).isSameAs(tokens.get(outerClose));
    assertThat(find(tokens, outerClose)).isSameAs(tokens.get(outerOpen));
    assertThat(find(tokens, innerOpen)).isSameAs(tokens.get(innerClose));
    assertThat(find(tokens, innerClose)).isSameAs(tokens.get(innerOpen));
  }

  @Test
  void noPairForUnbalancedBracketOrNonBracket() {
    var tokens = new BSLTokenizer("А = (Б;").getTokens();

    assertThat(find(tokens, indexOf(tokens, BSLLexer.LPAREN, 0))).isNull();
    assertThat(find(tokens, 0)).isNull();
  }

  private static Token find(List<Token> tokens, int index) {
    return MatchingBrackets.find(tokens, index, BSLLexer.LPAREN, BSLLexer.RPAREN);
  }

  private static int indexOf(List<Token> tokens, int type, int occurrence) {
    var seen = 0;
    for (var token : tokens) {
      if (token.getType() == type && seen++ == occurrence) {
        return token.getTokenIndex();
      }
    }
    throw new IllegalArgumentException();
  }
}
