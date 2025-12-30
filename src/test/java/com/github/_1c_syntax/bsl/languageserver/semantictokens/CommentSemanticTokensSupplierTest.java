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
package com.github._1c_syntax.bsl.languageserver.semantictokens;

import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.languageserver.util.SemanticTokensTestHelper;
import com.github._1c_syntax.bsl.languageserver.util.SemanticTokensTestHelper.ExpectedToken;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
@Import(SemanticTokensTestHelper.class)
class CommentSemanticTokensSupplierTest {

  @Autowired
  private CommentSemanticTokensSupplier supplier;

  @Autowired
  private SemanticTokensTestHelper helper;

  @BeforeEach
  void init() {
    supplier.setMultilineTokenSupport(false);
  }

  @Test
  void testRegularComment() {
    // given - comment inside a method is not a description
    String bsl = """
      Процедура Тест()
        // Это комментарий внутри метода
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then
    var expected = List.of(
      new ExpectedToken(1, 2, 32, SemanticTokenTypes.Comment, "// Это комментарий внутри метода")
    );
    helper.assertTokensMatch(decoded, expected);
  }

  @Test
  void testMultipleComments() {
    // given - comments inside method body are not descriptions
    String bsl = """
      Процедура Тест()
        // Первый комментарий
        // Второй комментарий
        // Третий комментарий
      КонецПроцедуры
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then
    var expected = List.of(
      new ExpectedToken(1, 2, 21, SemanticTokenTypes.Comment, "// Первый комментарий"),
      new ExpectedToken(2, 2, 21, SemanticTokenTypes.Comment, "// Второй комментарий"),
      new ExpectedToken(3, 2, 21, SemanticTokenTypes.Comment, "// Третий комментарий")
    );
    helper.assertTokensMatch(decoded, expected);
  }

  @Test
  void testDescriptionCommentsAreExcluded() {
    // given - method with description should not produce Comment tokens (handled by BslDocSemanticTokensSupplier)
    String bsl = """
      // Описание метода
      // Параметры:
      //  Параметр1 - Строка - описание
      Процедура Тест(Параметр1)
      КонецПроцедуры
      
      // Обычный комментарий после метода
      """;

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then - only comment on line 6 (after the procedure)
    var expected = List.of(
      new ExpectedToken(6, 0, 35, SemanticTokenTypes.Comment, "// Обычный комментарий после метода")
    );
    helper.assertTokensMatch(decoded, expected);
  }

  @Test
  void testMultilineCommentTokens() {
    // given - consecutive comments inside method body
    String bsl = """
      Процедура Тест()
        // Первый комментарий
        // Второй комментарий
        // Третий комментарий
      КонецПроцедуры
      """;

    var documentContext = TestUtils.getDocumentContext(bsl);

    // Test without multiline support - should have 3 separate tokens
    supplier.setMultilineTokenSupport(false);
    var tokensWithoutMultiline = helper.decodeFromEntries(supplier.getSemanticTokens(documentContext));

    // Test with multiline support - should have 1 merged token
    supplier.setMultilineTokenSupport(true);
    var tokensWithMultiline = helper.decodeFromEntries(supplier.getSemanticTokens(documentContext));

    // then
    // Without multiline: 3 separate tokens
    assertThat(tokensWithoutMultiline).hasSize(3);

    // With multiline: 1 merged token for consecutive comments
    assertThat(tokensWithMultiline).hasSize(1);

    // The merged token should start on line 1 (0-indexed)
    assertThat(tokensWithMultiline.get(0).line()).isEqualTo(1);
  }

  @Test
  void testNonConsecutiveCommentsNotMerged() {
    // given - non-consecutive comments should not be merged
    String bsl = """
      Процедура Тест()
        // Первый комментарий
        А = 1;
        // Второй комментарий
      КонецПроцедуры
      """;

    supplier.setMultilineTokenSupport(true);

    // when
    var decoded = helper.getDecodedTokens(bsl, supplier);

    // then - non-consecutive comments should still be separate tokens
    var expected = List.of(
      new ExpectedToken(1, 2, 21, SemanticTokenTypes.Comment, "// Первый комментарий"),
      new ExpectedToken(3, 2, 21, SemanticTokenTypes.Comment, "// Второй комментарий")
    );
    helper.assertTokensMatch(decoded, expected);
  }
}
