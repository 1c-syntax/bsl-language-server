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

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class QueryNestedFieldsByDotDiagnosticTest extends AbstractDiagnosticTest<QueryNestedFieldsByDotDiagnostic> {
  QueryNestedFieldsByDotDiagnosticTest() {
    super(QueryNestedFieldsByDotDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(12);
    assertThat(diagnostics, true)
      .hasRange(21, 3, 21, 40) //Ошибка №1
      .hasRange(22, 3, 22, 39) //Ошибка №1
      .hasRange(23, 3, 23, 36) //Ошибка №1
      .hasRange(24, 3, 24, 43) //Ошибка №1
      .hasRange(29, 3, 29, 33) //Ошибка №7
      .hasRange(53, 6, 53, 39) //Ошибка №3
      .hasRange(53, 41, 53, 77) //Ошибка №3
      .hasRange(53, 79, 53, 116) //Ошибка №3
      .hasRange(101, 7, 101, 61) //Ошибка №2
      .hasRange(102, 7, 102, 64) //Ошибка №2
      .hasRange(103, 7, 103, 65) //Ошибка №2
      .hasRange(115, 3, 115, 82); //Ошибка №6
  }
}
