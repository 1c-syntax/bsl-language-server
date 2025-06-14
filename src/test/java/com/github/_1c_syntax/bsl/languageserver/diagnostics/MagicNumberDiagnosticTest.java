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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;


class MagicNumberDiagnosticTest extends AbstractDiagnosticTest<MagicNumberDiagnostic> {

  MagicNumberDiagnosticTest() {
    super(MagicNumberDiagnostic.class);
  }

  @Test
  void runTest() {
    // when
    List<Diagnostic> diagnostics = getDiagnostics();

    // then
    assertThat(diagnostics).hasSize(12);
    assertThat(diagnostics, true)
      .hasRange(3, 18, 20)
      .hasRange(3, 23, 25)
      .hasRange(7, 31, 33)
      .hasRange(11, 20, 21)
      .hasRange(20, 21, 23)
      .hasRange(23, 24, 26)
      .hasRange(27, 34, 35)
      .hasRange(33, 37, 38)
      .hasRange(34, 37, 38)
      .hasRange(36, 87, 88)
      .hasRange(37, 55, 56)
      .hasRange(41, 16, 19);
  }

  @Test
  void testConfigure() {
    // conf
    Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("authorizedNumbers", "-1,0,1,60,7");
    diagnosticInstance.configure(configuration);

    // when
    List<Diagnostic> diagnostics = getDiagnostics();

    // then
    assertThat(diagnostics).hasSize(9);
    assertThat(diagnostics, true)
      .hasRange(7, 31, 33)
      .hasRange(11, 20, 21)
      .hasRange(20, 21, 23)
      .hasRange(23, 24, 26)
      .hasRange(33, 37, 38)
      .hasRange(34, 37, 38)
      .hasRange(36, 87, 88)
      .hasRange(37, 55, 56)
      .hasRange(41, 16, 19);
  }

  @Test
  void testIndexes() {
    // conf
    Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("allowMagicIndexes", false);
    diagnosticInstance.configure(configuration);

    // when
    List<Diagnostic> diagnostics = getDiagnostics();

    // then
    assertThat(diagnostics).hasSize(14);
    assertThat(diagnostics, true)
      .hasRange(3, 18, 20)
      .hasRange(3, 23, 25)
      .hasRange(7, 31, 33)
      .hasRange(11, 20, 21)
      .hasRange(20, 21, 23)
      .hasRange(23, 24, 26)
      .hasRange(27, 34, 35)
      .hasRange(33, 37, 38)
      .hasRange(34, 37, 38)
      .hasRange(36, 87, 88)
      .hasRange(37, 55, 56)
      .hasRange(41, 16, 19)
      .hasRange(52, 32, 34)
      .hasRange(53, 18, 20);
  }

  @Test
  void testConfigureWithArrayListShouldNotThrowClassCastException() {
    // This test reproduces the issue described in the bug report
    // When configuration contains ArrayList instead of String for authorizedNumbers
    
    // conf - simulate the configuration that causes ClassCastException
    Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    // Put an ArrayList instead of String to trigger the ClassCastException
    configuration.put("authorizedNumbers", new ArrayList<>(Arrays.asList("-1", "0", "1", "60", "7")));
    
    // This should not throw ClassCastException after fix
    diagnosticInstance.configure(configuration);

    // when
    List<Diagnostic> diagnostics = getDiagnostics();

    // then - should work the same as when configured with a String
    assertThat(diagnostics).hasSize(9);
    assertThat(diagnostics, true)
      .hasRange(7, 31, 33)
      .hasRange(11, 20, 21)
      .hasRange(20, 21, 23)
      .hasRange(23, 24, 26)
      .hasRange(33, 37, 38)
      .hasRange(34, 37, 38)
      .hasRange(36, 87, 88)
      .hasRange(37, 55, 56)
      .hasRange(41, 16, 19);
  }

  @Test
  void testConfigureWithInvalidTypeShouldUseDefaults() {
    // Test that invalid configuration types fall back to defaults gracefully
    
    // conf - provide an invalid type (Integer) instead of String or List
    Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("authorizedNumbers", 123); // Invalid type
    
    // This should not throw exception and should fall back to defaults
    diagnosticInstance.configure(configuration);

    // when
    List<Diagnostic> diagnostics = getDiagnostics();

    // then - should work the same as default configuration (12 diagnostics)
    assertThat(diagnostics).hasSize(12);
    assertThat(diagnostics, true)
      .hasRange(3, 18, 20)
      .hasRange(3, 23, 25)
      .hasRange(7, 31, 33)
      .hasRange(11, 20, 21)
      .hasRange(20, 21, 23)
      .hasRange(23, 24, 26)
      .hasRange(27, 34, 35)
      .hasRange(33, 37, 38)
      .hasRange(34, 37, 38)
      .hasRange(36, 87, 88)
      .hasRange(37, 55, 56)
      .hasRange(41, 16, 19);
  }

}
