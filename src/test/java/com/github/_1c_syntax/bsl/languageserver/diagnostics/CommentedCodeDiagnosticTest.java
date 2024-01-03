/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2024
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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class CommentedCodeDiagnosticTest extends AbstractDiagnosticTest<CommentedCodeDiagnostic> {

  CommentedCodeDiagnosticTest() {
    super(CommentedCodeDiagnostic.class);
  }

  @Test
  void runTest() {
    List<Diagnostic> diagnostics = getDiagnostics();
    check(diagnostics, false);
  }

  @Test
  void exclusionPrefixesTest(){
    Map<String, Object> configuration = diagnosticInstance.info.getDefaultConfiguration();
    configuration.put("exclusionPrefixes", "<code>");
    diagnosticInstance.configure(configuration);

    var diagnostics = getDiagnostics();
    check(diagnostics, true);
  }

  void check(List<Diagnostic> diagnostics, boolean excludePrefixes){
    int expectedSize = excludePrefixes?11:12;

    assertThat(diagnostics).hasSize(expectedSize);
    assertThat(diagnostics, true)
      .hasRange(0, 0, 6, 81)
      .hasRange(16, 4, 34, 16)
      .hasRange(36, 4, 42, 156)
      .hasRange(44, 4, 49, 16)
      .hasRange(59, 4, 65, 78)
      .hasRange(76, 0, 80, 18)
      .hasRange(82, 0, 82, 23)
      .hasRange(84, 0, 85, 38)
      .hasRange(117, 0, 118, 24)
      .hasRange(203, 0, 203, 32)
      .hasRange(244, 0, 264, 152);

    if(!excludePrefixes){
      assertThat(diagnostics, true).hasRange(268, 4, 270, 22);
    }
  }

  @Test
  void testConfigure() {

    Map<String, Object> configuration = diagnosticInstance.info.getDefaultConfiguration();

    List<Object> thresholdVariants = new ArrayList<>();
    thresholdVariants.add(Float.valueOf(1f));
    thresholdVariants.add(Double.valueOf(1));
    thresholdVariants.add(Integer.valueOf(1));

    for (Object threshold : thresholdVariants){
      configuration.put("threshold", threshold);
      diagnosticInstance.configure(configuration);

      List<Diagnostic> diagnostics = getDiagnostics();
      assertThat(diagnostics).isEmpty();
    }

  }

  @Test
  void testQuickFixRemoveCode() {
    List<Diagnostic> diagnostics = getDiagnostics();
    List<CodeAction> quickFixes = getQuickFixes(diagnostics.get(0));

    assertThat(quickFixes)
      .hasSize(1);

    final CodeAction fix = quickFixes.get(0);
    assertThat(fix).of(diagnosticInstance).in(getDocumentContext()).fixes(diagnostics.get(0));

  }
}