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
package com.github._1c_syntax.bsl.languageserver.reporters;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CodeQualityReportEntryTest {

  @Autowired
  private Map<String, DiagnosticInfo> diagnosticInfosByCode;

  @Test
  void testConstructor() {
    var diagnostic = new Diagnostic(
      Ranges.create(0, 1, 2, 3),
      "message",
      DiagnosticSeverity.Error,
      "test-source",
      "Typo"
    );
    var diagnosticInfo = diagnosticInfosByCode.get("Typo");
    var entry = new CodeQualityReportEntry("file.txt", diagnostic, diagnosticInfo);

    assertThat(entry)
      .hasNoNullFieldsOrProperties()
      .hasFieldOrPropertyWithValue("description", "message")
      .hasFieldOrPropertyWithValue("checkName", "Typo")
      .hasFieldOrPropertyWithValue("severity", CodeQualityReportEntry.Severity.INFO)
      .hasFieldOrPropertyWithValue("location.path", "file.txt")
      .hasFieldOrPropertyWithValue("location.lines.begin", 0)
    ;
  }

}