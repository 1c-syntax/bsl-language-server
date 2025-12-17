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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceIndexFiller;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class SemanticTokensProviderIntegrationTest {

  @Autowired
  private SemanticTokensProvider provider;

  @Autowired
  private SemanticTokensLegend legend;

  @Autowired
  private ReferenceIndexFiller referenceIndexFiller;

  @Autowired
  private com.github._1c_syntax.bsl.languageserver.references.ReferenceIndex referenceIndex;

  @BeforeEach
  void init() {
    provider.setMultilineTokenSupport(false);
  }

  @Test
  void parameterAndVariableUsages() {
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/providers/SemanticTokensProviderParameterTest.bsl"
    );
    referenceIndexFiller.fill(documentContext);

    TextDocumentIdentifier textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());
    SemanticTokens tokens = provider.getSemanticTokensFull(documentContext, new SemanticTokensParams(textDocumentIdentifier));

    int paramIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Parameter);
    int varIdx = legend.getTokenTypes().indexOf(SemanticTokenTypes.Variable);
    assertThat(paramIdx).isGreaterThanOrEqualTo(0);
    assertThat(varIdx).isGreaterThanOrEqualTo(0);

    List<DecodedToken> decoded = decode(tokens.getData());

    long paramsLine0 = decoded.stream()
      .filter(t -> t.line == 0 && t.type == paramIdx)
      .count();
    assertThat(paramsLine0).as("Parameters in signature (line 0)").isEqualTo(2);

    long varsLine1 = decoded.stream()
      .filter(t -> t.line == 1 && t.type == varIdx)
      .count();
    assertThat(varsLine1).as("Local variable declaration (line 1)").isEqualTo(1);

    long varsLine3 = decoded.stream()
      .filter(t -> t.line == 3 && t.type == varIdx)
      .count();
    assertThat(varsLine3).as("Variable usage on left side (line 3)").isEqualTo(1);

    long paramsLine3 = decoded.stream()
      .filter(t -> t.line == 3 && t.type == paramIdx)
      .count();
    assertThat(paramsLine3).as("Parameter usage on right side (line 3)").isEqualTo(1);

    long varsLine4 = decoded.stream()
      .filter(t -> t.line == 4 && t.type == varIdx)
      .count();
    assertThat(varsLine4).as("Variable usage (line 4)").isEqualTo(1);

    long paramsLine4 = decoded.stream()
      .filter(t -> t.line == 4 && t.type == paramIdx)
      .count();
    assertThat(paramsLine4).as("Parameter usages (line 4)").isEqualTo(2);

    long paramsLine6 = decoded.stream()
      .filter(t -> t.line == 6 && t.type == paramIdx)
      .count();
    assertThat(paramsLine6).as("Parameter in condition (line 6)").isEqualTo(1);

    long paramsLine7 = decoded.stream()
      .filter(t -> t.line == 7 && t.type == paramIdx)
      .count();
    assertThat(paramsLine7).as("Parameter in Сообщить (line 7)").isEqualTo(1);

    long varsLine8 = decoded.stream()
      .filter(t -> t.line == 8 && t.type == varIdx)
      .count();
    assertThat(varsLine8).as("Variable assignment (line 8)").isEqualTo(1);

    long paramsLine8 = decoded.stream()
      .filter(t -> t.line == 8 && t.type == paramIdx)
      .count();
    assertThat(paramsLine8).as("Parameters in expression (line 8)").isEqualTo(2);

    long varsLine11 = decoded.stream()
      .filter(t -> t.line == 11 && t.type == varIdx)
      .count();
    assertThat(varsLine11).as("For loop variable (line 11)").isEqualTo(1);

    long paramsLine11 = decoded.stream()
      .filter(t -> t.line == 11 && t.type == paramIdx)
      .count();
    assertThat(paramsLine11).as("Parameter in loop bound (line 11)").isEqualTo(1);

    long varsLine12 = decoded.stream()
      .filter(t -> t.line == 12 && t.type == varIdx)
      .count();
    assertThat(varsLine12).as("Loop variable usage (line 12)").isEqualTo(1);

    long totalParams = decoded.stream()
      .filter(t -> t.type == paramIdx)
      .count();
    assertThat(totalParams).as("Total parameter tokens").isGreaterThanOrEqualTo(10);

    long totalVars = decoded.stream()
      .filter(t -> t.type == varIdx)
      .count();
    assertThat(totalVars).as("Total variable tokens").isGreaterThanOrEqualTo(6);
  }

  private record DecodedToken(int line, int start, int length, int type, int modifiers) {}

  private List<DecodedToken> decode(List<Integer> data) {
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
}
