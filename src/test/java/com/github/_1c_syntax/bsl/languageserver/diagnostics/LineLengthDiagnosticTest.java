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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class LineLengthDiagnosticTest extends AbstractDiagnosticTest<LineLengthDiagnostic> {

  LineLengthDiagnosticTest() {
    super(LineLengthDiagnostic.class);
  }

  @Test
  void test() {
    // when
    List<Diagnostic> diagnostics = getDiagnostics();

    // then
    assertThat(diagnostics).hasSize(13);
    assertThat(diagnostics, true)
      .hasRange(4, 0, 4, 121)
      .hasRange(5, 0, 5, 122)
      .hasRange(8, 0, 8, 127)
      .hasRange(11, 0, 11, 136)
      .hasRange(12, 0, 12, 135)
      .hasRange(36, 0, 36, 127)
      .hasRange(44, 0, 44, 143)
      .hasRange(47, 0, 47, 139)
      .hasRange(49, 0, 49, 138)
      // FIXme
      .hasRange(40, 0, 40, 140)
      .hasRange(52, 0, 52, 177)
      .hasRange(56, 0, 162)
      .hasRange(60, 0, 145)
    ;

  }

  @Test
  void testConfigure() {
    // given
    Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("maxLineLength", 119);
    diagnosticInstance.configure(configuration);

    // when
    List<Diagnostic> diagnostics = getDiagnostics();

    // then
    assertThat(diagnostics).hasSize(14);
    assertThat(diagnostics, true)
      .hasRange(3, 0, 3, 120)
      .hasRange(4, 0, 4, 121)
      .hasRange(5, 0, 5, 122)
      .hasRange(8, 0, 8, 127)
      .hasRange(11, 0, 11, 136)
      .hasRange(12, 0, 12, 135)
      .hasRange(36, 0, 36, 127)
      .hasRange(44, 0, 44, 143)
      .hasRange(47, 0, 47, 139)
      .hasRange(49, 0, 49, 138)
      // FIXme
      .hasRange(40, 0, 40, 140)
      .hasRange(52, 0, 52, 177)
      .hasRange(56, 0, 162)
      .hasRange(60, 0, 145)
    ;
  }

  @Test
  void testConfigureSkipMethodDescription() {
    // given
    Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("maxLineLength", 120);
    configuration.put("checkMethodDescription", false);
    diagnosticInstance.configure(configuration);

    // when
    List<Diagnostic> diagnostics = getDiagnostics();

    // then
    assertThat(diagnostics).hasSize(11);
    assertThat(diagnostics, true)
      .hasRange(4, 0, 4, 121)
      .hasRange(5, 0, 5, 122)
      .hasRange(8, 0, 8, 127)
      .hasRange(11, 0, 11, 136)
      .hasRange(12, 0, 12, 135)
      .hasRange(36, 0, 36, 127)
      .hasRange(44, 0, 44, 143)
      .hasRange(47, 0, 47, 139)
      .hasRange(49, 0, 49, 138)
      // FIXme
      .hasRange(40, 0, 40, 140)
      .hasRange(52, 0, 52, 177)
    ;
  }

  @Test
  void testExcludeTrailingComments() {
    // Test with trailing comments excluded
    Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("maxLineLength", 120);
    configuration.put("excludeTrailingComments", true);
    diagnosticInstance.configure(configuration);
    List<Diagnostic> diagnostics = getDiagnostics();

    // When excluding trailing comments, we should have exactly 12 diagnostics
    assertThat(diagnostics).hasSize(12);
  }

  @Test
  void testExcludeTrailingCommentsWithCheckMethodDescriptionFalse() {
    // Test that excludeTrailingComments works correctly with checkMethodDescription=false
    Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("maxLineLength", 120);
    configuration.put("checkMethodDescription", false);
    configuration.put("excludeTrailingComments", true);
    diagnosticInstance.configure(configuration);
    List<Diagnostic> diagnostics = getDiagnostics();

    // Should have the same count as testConfigureSkipMethodDescription but with trailing comments excluded
    assertThat(diagnostics).hasSize(10);
  }

}
