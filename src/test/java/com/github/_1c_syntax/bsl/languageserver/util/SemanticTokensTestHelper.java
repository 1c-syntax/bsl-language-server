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
package com.github._1c_syntax.bsl.languageserver.util;

import com.github._1c_syntax.bsl.languageserver.semantictokens.SemanticTokenEntry;
import com.github._1c_syntax.bsl.languageserver.semantictokens.SemanticTokensSupplier;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.springframework.boot.test.context.TestComponent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Helper class for testing semantic tokens.
 * Provides types and methods for decoding and asserting semantic tokens.
 */
@TestComponent
@RequiredArgsConstructor
public class SemanticTokensTestHelper {

  private final SemanticTokensLegend legend;

  /**
   * Represents expected semantic token for assertion.
   *
   * @param line           0-based line number
   * @param startChar      0-based start character
   * @param length         token length
   * @param tokenType      LSP token type (e.g., SemanticTokenTypes.Keyword)
   * @param tokenModifiers set of LSP modifiers (e.g., SemanticTokenModifiers.Declaration)
   * @param lexeme         optional lexeme for documentation (not used in comparison)
   */
  public record ExpectedToken(
    int line,
    int startChar,
    int length,
    String tokenType,
    Set<String> tokenModifiers,
    String lexeme
  ) {
    public ExpectedToken(int line, int startChar, int length, String tokenType, String lexeme) {
      this(line, startChar, length, tokenType, Set.of(), lexeme);
    }

    public ExpectedToken(int line, int startChar, int length, String tokenType, String modifier, String lexeme) {
      this(line, startChar, length, tokenType, Set.of(modifier), lexeme);
    }
  }

  /**
   * Represents decoded semantic token with absolute positions.
   */
  public record DecodedToken(int line, int start, int length, int type, int modifiers)
    implements Comparable<DecodedToken> {

    @Override
    public int compareTo(DecodedToken other) {
      return Comparator.comparing(DecodedToken::line)
        .thenComparing(DecodedToken::start)
        .thenComparing(DecodedToken::length)
        .thenComparing(DecodedToken::type)
        .thenComparing(DecodedToken::modifiers)
        .compare(this, other);
    }
  }

  /**
   * Decode LSP-encoded semantic tokens data (List&lt;Integer&gt;) into absolute positions.
   *
   * @param data the encoded data from SemanticTokens.getData()
   * @return list of decoded tokens with absolute positions
   */
  public List<DecodedToken> decode(List<Integer> data) {
    List<DecodedToken> out = new ArrayList<>();
    int line = 0;
    int start = 0;
    for (int i = 0; i + 4 < data.size(); i += 5) {
      int dLine = data.get(i);
      int dStart = data.get(i + 1);
      int length = data.get(i + 2);
      int type = data.get(i + 3);
      int mods = data.get(i + 4);
      line = line + dLine;
      start = (dLine == 0) ? start + dStart : dStart;
      out.add(new DecodedToken(line, start, length, type, mods));
    }
    return out;
  }

  /**
   * Decode SemanticTokenEntry list into DecodedToken list.
   *
   * @param tokens the tokens from a SemanticTokensSupplier
   * @return list of decoded tokens
   */
  public List<DecodedToken> decodeFromEntries(List<SemanticTokenEntry> tokens) {
    return tokens.stream()
      .map(t -> new DecodedToken(t.line(), t.start(), t.length(), t.type(), t.modifiers()))
      .toList();
  }

  /**
   * Get decoded tokens from BSL code using the specified supplier.
   *
   * @param bsl      the BSL source code
   * @param supplier the semantic tokens supplier to use
   * @return list of decoded tokens
   */
  public List<DecodedToken> getDecodedTokens(String bsl, SemanticTokensSupplier supplier) {
    var documentContext = TestUtils.getDocumentContext(bsl);
    return decodeFromEntries(supplier.getSemanticTokens(documentContext));
  }

  /**
   * Assert that actual tokens exactly match expected tokens.
   *
   * @param actual   decoded tokens
   * @param expected expected tokens
   */
  public void assertTokensMatch(List<DecodedToken> actual, List<ExpectedToken> expected) {
    assertThat(actual)
      .as("Number of tokens")
      .hasSameSizeAs(expected);

    for (int i = 0; i < expected.size(); i++) {
      var exp = expected.get(i);
      var act = actual.get(i);

      int expectedTypeIdx = legend.getTokenTypes().indexOf(exp.tokenType);
      int expectedModifiersMask = computeModifiersMask(exp.tokenModifiers);

      assertThat(act.line)
        .as("Token %d (%s): line", i, exp.lexeme)
        .isEqualTo(exp.line);
      assertThat(act.start)
        .as("Token %d (%s): start", i, exp.lexeme)
        .isEqualTo(exp.startChar);
      assertThat(act.length)
        .as("Token %d (%s): length", i, exp.lexeme)
        .isEqualTo(exp.length);
      assertThat(act.type)
        .as("Token %d (%s): type (expected %s)", i, exp.lexeme, exp.tokenType)
        .isEqualTo(expectedTypeIdx);
      assertThat(act.modifiers)
        .as("Token %d (%s): modifiers", i, exp.lexeme)
        .isEqualTo(expectedModifiersMask);
    }
  }

  /**
   * Assert that actual tokens contain expected tokens (order-independent, non-exact match).
   *
   * @param actual   decoded tokens
   * @param expected expected tokens to find
   */
  public void assertContainsTokens(List<DecodedToken> actual, List<ExpectedToken> expected) {
    for (var exp : expected) {
      int expectedTypeIdx = legend.getTokenTypes().indexOf(exp.tokenType);
      int expectedModifiersMask = computeModifiersMask(exp.tokenModifiers);

      var found = actual.stream()
        .filter(t -> t.line == exp.line
          && t.start == exp.startChar
          && t.length == exp.length
          && t.type == expectedTypeIdx
          && t.modifiers == expectedModifiersMask)
        .findFirst();

      assertThat(actual)
        .as("Expected token: %s at [%d:%d], length=%d, type=%s, modifiers=%s",
          exp.lexeme, exp.line, exp.startChar, exp.length, exp.tokenType, exp.tokenModifiers)
        .contains(new DecodedToken(exp.line, exp.startChar, exp.length, expectedTypeIdx, expectedModifiersMask));
    }
  }

  /**
   * Compute bitmask for modifiers based on legend.
   *
   * @param modifiers set of modifier names
   * @return bitmask
   */
  private int computeModifiersMask(Set<String> modifiers) {
    int mask = 0;
    for (String mod : modifiers) {
      int idx = legend.getTokenModifiers().indexOf(mod);
      if (idx >= 0) {
        mask |= (1 << idx);
      }
    }
    return mask;
  }
}

