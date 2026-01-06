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

import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;


class DeletingCollectionItemDiagnosticTest extends AbstractDiagnosticTest<DeletingCollectionItemDiagnostic> {

  DeletingCollectionItemDiagnosticTest() {
    super(DeletingCollectionItemDiagnostic.class);
  }

  @Test
  void test() {
    List<Diagnostic> diagnostics = getDiagnostics();
    assertThat(diagnostics).hasSize(8);
    assertThat(diagnostics, true)
      .hasRange(17, 8, 17, 47)
      .hasRange(23, 4, 23, 21)
      .hasRange(28, 4, 28, 25)
      .hasRange(33, 4, 33, 30)
      .hasRange(39, 8, 39, 34)
      .hasRange(45, 4, 45, 23)
      .hasRange(50, 4, 50, 37)
      .hasRange(55, 4, 55, 39);
  }

  @Test
  void testIncompleteForEachStatement() {
    // Test that incomplete forEach statement doesn't cause NullPointerException
    String module = """
      Процедура Тест()
        Для Каждого\s
      КонецПроцедуры
      """;

    var documentContext = TestUtils.getDocumentContext(module);
    var diagnostics = getDiagnostics(documentContext);

    // Should not throw NullPointerException and should have no diagnostics
    assertThat(diagnostics).hasSize(0);
  }

}