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
package com.github._1c_syntax.bsl.languageserver.utils.bsl;

import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.parser.BSLLexer;
import org.antlr.v4.runtime.Token;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class BlockKeywordMatcherTest {

  @Test
  void returnsNullForNegativeIndex() {
    var tokens = tokensOf("Если Истина Тогда\nКонецЕсли;");
    assertThat(BlockKeywordMatcher.findMatchingOpener(tokens, -1)).isNull();
  }

  @Test
  void returnsNullForOutOfBoundsIndex() {
    var tokens = tokensOf("Если Истина Тогда\nКонецЕсли;");
    assertThat(BlockKeywordMatcher.findMatchingOpener(tokens, tokens.size())).isNull();
    assertThat(BlockKeywordMatcher.findMatchingOpener(tokens, tokens.size() + 10)).isNull();
  }

  @Test
  void returnsNullForNonClosingToken() {
    var tokens = tokensOf("А = 1;");
    var assignIndex = indexOfType(tokens, BSLLexer.ASSIGN);
    assertThat(BlockKeywordMatcher.findMatchingOpener(tokens, assignIndex)).isNull();
  }

  @Test
  void findsOpenerForSimpleEndIf() {
    var tokens = tokensOf("Если Истина Тогда\nКонецЕсли;");
    var endifIndex = indexOfType(tokens, BSLLexer.ENDIF_KEYWORD);
    var opener = BlockKeywordMatcher.findMatchingOpener(tokens, endifIndex);
    assertThat(opener).isNotNull();
    assertThat(opener.getType()).isEqualTo(BSLLexer.IF_KEYWORD);
    assertThat(opener.getLine()).isEqualTo(1);
  }

  @Test
  void findsOuterOpenerThroughNestedBlock() {
    var source = "Если А Тогда\n  Если Б Тогда\n    Возврат;\n  КонецЕсли;\nКонецЕсли;";
    var tokens = tokensOf(source);
    var outerEndifIndex = lastIndexOfType(tokens, BSLLexer.ENDIF_KEYWORD);
    var opener = BlockKeywordMatcher.findMatchingOpener(tokens, outerEndifIndex);
    assertThat(opener).isNotNull();
    assertThat(opener.getType()).isEqualTo(BSLLexer.IF_KEYWORD);
    // должно быть внешнее `Если` на первой строке, а не вложенное со второй
    assertThat(opener.getLine()).isEqualTo(1);
  }

  @Test
  void findsContainingTryForExceptKeyword() {
    var tokens = tokensOf("Попытка\n  Возврат;\nИсключение\nКонецПопытки;");
    var exceptIndex = indexOfType(tokens, BSLLexer.EXCEPT_KEYWORD);
    var opener = BlockKeywordMatcher.findMatchingOpener(tokens, exceptIndex);
    assertThat(opener).isNotNull();
    assertThat(opener.getType()).isEqualTo(BSLLexer.TRY_KEYWORD);
  }

  @Test
  void findsContainingLoopForEnddo() {
    var tokens = tokensOf("Для Сч = 1 По 3 Цикл\n  Возврат;\nКонецЦикла;");
    var enddoIndex = indexOfType(tokens, BSLLexer.ENDDO_KEYWORD);
    var opener = BlockKeywordMatcher.findMatchingOpener(tokens, enddoIndex);
    assertThat(opener).isNotNull();
    assertThat(opener.getType()).isEqualTo(BSLLexer.FOR_KEYWORD);
  }

  @Test
  void returnsNullWhenOpenerMissing() {
    // несбалансированный КонецЕсли без парного Если
    var tokens = tokensOf("КонецЕсли;");
    var endifIndex = indexOfType(tokens, BSLLexer.ENDIF_KEYWORD);
    assertThat(BlockKeywordMatcher.findMatchingOpener(tokens, endifIndex)).isNull();
  }

  private static List<Token> tokensOf(String source) {
    return TestUtils.getDocumentContext(source).getTokens();
  }

  private static int indexOfType(List<Token> tokens, int type) {
    for (var i = 0; i < tokens.size(); i++) {
      if (tokens.get(i).getType() == type) {
        return i;
      }
    }
    throw new AssertionError("token of type " + type + " not found");
  }

  private static int lastIndexOfType(List<Token> tokens, int type) {
    for (var i = tokens.size() - 1; i >= 0; i--) {
      if (tokens.get(i).getType() == type) {
        return i;
      }
    }
    throw new AssertionError("token of type " + type + " not found");
  }
}
