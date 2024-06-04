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

import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class DuplicateStringLiteralDiagnosticTest extends AbstractDiagnosticTest<DuplicateStringLiteralDiagnostic> {
  DuplicateStringLiteralDiagnosticTest() {
    super(DuplicateStringLiteralDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(2);
    assertThat(diagnostics, true)
      .anyMatch(diagnostic ->
        diagnostic.getRange().equals(Ranges.create(1, 8, 1, 17))
          && diagnostic.getRelatedInformation().size() == 4
          && diagnostic.getMessage()
          .equals("Необходимо избавиться от многократного использования строкового литерала \"Строка2\"")
      )
      .anyMatch(diagnostic ->
        diagnostic.getRange().equals(Ranges.create(10, 9, 10, 19))
          && diagnostic.getRelatedInformation().size() == 4
          && diagnostic.getMessage()
          .equals("Необходимо избавиться от многократного использования строкового литерала \"Строка22\"")
          && diagnostic.getRelatedInformation().get(3).getMessage()
          .equals("\"СтрОкА22\"")
      )
    ;

  }

  @Test
  void testConfigureAllowedNumbers() {

    Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("allowedNumberCopies", 0);
    diagnosticInstance.configure(configuration);

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(2);

    configuration.put("allowedNumberCopies", 4);
    diagnosticInstance.configure(configuration);

    diagnostics = getDiagnostics();

    assertThat(diagnostics).isEmpty();
  }

  @Test
  void testConfigureAllFile() {

    Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("analyzeFile", true);
    diagnosticInstance.configure(configuration);

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(4);
    assertThat(diagnostics, true)
      .anyMatch(diagnostic ->
        diagnostic.getRange().equals(Ranges.create(1, 8, 1, 17))
          && diagnostic.getRelatedInformation().size() == 4
          && diagnostic.getMessage()
          .equals("Необходимо избавиться от многократного использования строкового литерала \"Строка2\"")
      )
      .anyMatch(diagnostic ->
        diagnostic.getRange().equals(Ranges.create(10, 9, 10, 19))
          && diagnostic.getRelatedInformation().size() == 6
          && diagnostic.getMessage()
          .equals("Необходимо избавиться от многократного использования строкового литерала \"Строка22\"")
      )
      .anyMatch(diagnostic ->
        diagnostic.getRange().equals(Ranges.create(3, 35, 3, 44))
          && diagnostic.getRelatedInformation().size() == 3
          && diagnostic.getMessage()
          .equals("Необходимо избавиться от многократного использования строкового литерала \"Строка3\"")
      )
      .anyMatch(diagnostic ->
        diagnostic.getRange().equals(Ranges.create(19, 12, 19, 34))
          && diagnostic.getRelatedInformation().size() == 3
          && diagnostic.getMessage()
          .equals("Необходимо избавиться от многократного использования строкового литерала \"Выбрать Первые 1 Из \"")
      )
    ;
  }

  @Test
  void testConfigureCase() {

    Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("caseSensitive", true);
    diagnosticInstance.configure(configuration);

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true)
      .anyMatch(diagnostic ->
        diagnostic.getRange().equals(Ranges.create(1, 8, 1, 17))
          && diagnostic.getRelatedInformation().size() == 4
          && diagnostic.getMessage()
          .equals("Необходимо избавиться от многократного использования строкового литерала \"Строка2\"")
      )
    ;

  }

  @Test
  void testConfigureAllowedNumberCopies() {

    Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("allowedNumberCopies", 5);
    configuration.put("analyzeFile", true);
    diagnosticInstance.configure(configuration);

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true)
      .anyMatch(diagnostic ->
        diagnostic.getRange().equals(Ranges.create(10, 9, 10, 19))
          && diagnostic.getRelatedInformation().size() == 6
          && diagnostic.getMessage()
          .equals("Необходимо избавиться от многократного использования строкового литерала \"Строка22\"")
      )
    ;
  }

  @Test
  void testConfigureMinTextLength() {

    Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("minTextLength", 10);
    diagnosticInstance.configure(configuration);

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true)
      .anyMatch(diagnostic ->
        diagnostic.getRange().equals(Ranges.create(10, 9, 10, 19))
          && diagnostic.getRelatedInformation().size() == 4
          && diagnostic.getMessage()
          .equals("Необходимо избавиться от многократного использования строкового литерала \"Строка22\"")
          && diagnostic.getRelatedInformation().get(3).getMessage()
          .equals("\"СтрОкА22\"")
      )
    ;

  }
}
