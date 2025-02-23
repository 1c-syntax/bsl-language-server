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

import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TSLintReportEntryTest {

  @Test
  void testConstructor() {
    Diagnostic diagnostic = new Diagnostic(
      Ranges.create(0, 1, 2, 3),
      "message",
      DiagnosticSeverity.Error,
      "test-source",
      "test"
    );
    TSLintReportEntry entry = new TSLintReportEntry("file.txt", diagnostic);

    assertThat(entry)
      .hasNoNullFieldsOrProperties()
      .hasFieldOrPropertyWithValue("failure", "message")
      .hasFieldOrPropertyWithValue("name", "file.txt")
      .hasFieldOrPropertyWithValue("ruleName", "test")
      .hasFieldOrPropertyWithValue("ruleSeverity", "error");

    assertThat(entry.getStartPosition())
      .hasFieldOrPropertyWithValue("line", 0)
      .hasFieldOrPropertyWithValue("character", 1)
      .hasFieldOrPropertyWithValue("position", 1);

    assertThat(entry.getEndPosition())
      .hasFieldOrPropertyWithValue("line", 2)
      .hasFieldOrPropertyWithValue("character", 3)
      .hasFieldOrPropertyWithValue("position", 3);
  }

}