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
package com.github._1c_syntax.bsl.languageserver.utils;

import com.github._1c_syntax.bsl.parser.BSLLexer;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.IncrementalTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TreesTest {

  @Test
  void testGetTokensWithValidTree() {
    // given
    String input = """
      Процедура Тест()
      КонецПроцедуры""";
    var lexer = new BSLLexer(CharStreams.fromString(input));
    var tokenStream = new IncrementalTokenStream(lexer);
    var parser = new BSLParser(tokenStream);
    ParseTree tree = parser.file();

    // when
    List<Token> tokens = Trees.getTokens(tree);

    // then
    assertThat(tokens)
      .isNotEmpty()
      .hasSize(6);
  }

  @Test
  void testGetTokensWithNullTree() {
    // when
    var tokens = Trees.getTokens(null);

    // then
    assertThat(tokens).isEmpty();
  }

  @Test
  void testGetTokensWithEmptyTree() {
    // given
    String input = "";
    var lexer = new BSLLexer(CharStreams.fromString(input));
    var tokenStream = new IncrementalTokenStream(lexer);
    var parser = new BSLParser(tokenStream);
    ParseTree tree = parser.file();

    // when
    var tokens = Trees.getTokens(tree);

    // then
    assertThat(tokens).hasSize(1); // Only EOF token
  }

  @Test
  void testGetTokensReturnsCorrectTokenTypes() {
    // given
    String input = """
      Если Истина Тогда
      Возврат;
      КонецЕсли;""";
    var lexer = new BSLLexer(CharStreams.fromString(input));
    var tokenStream = new IncrementalTokenStream(lexer);
    var parser = new BSLParser(tokenStream);
    ParseTree tree = parser.file();

    // when
    var tokens = Trees.getTokens(tree);

    // then
    assertThat(tokens).hasSize(8);
    assertThat(tokens.get(0).getType()).isEqualTo(BSLLexer.IF_KEYWORD);
    assertThat(tokens.get(1).getType()).isEqualTo(BSLLexer.TRUE);
    assertThat(tokens.get(2).getType()).isEqualTo(BSLLexer.THEN_KEYWORD);
  }
}
