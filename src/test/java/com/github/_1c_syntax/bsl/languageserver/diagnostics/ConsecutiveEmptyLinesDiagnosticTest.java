/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class ConsecutiveEmptyLinesDiagnosticTest extends AbstractDiagnosticTest<ConsecutiveEmptyLinesDiagnostic> {
  ConsecutiveEmptyLinesDiagnosticTest() {
    super(ConsecutiveEmptyLinesDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(8);
    assertThat(diagnostics, true)
      .hasRange(2, 0, 3, 0)
      .hasRange(6, 0, 7, 0)
      .hasRange(14, 0, 15, 0)
      .hasRange(21, 0, 22, 0)
      .hasRange(25, 0, 26, 0)
      .hasRange(35, 0, 39,0)
      .hasRange(48, 0, 49, 0)
      .hasRange(53, 0, 54, 0) // 1 line miss
    ;

  }

  @Test
  void testConfigure() {

    Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("maxEmptyLineCount", 3);
    diagnosticInstance.configure(configuration);

    List<Diagnostic> diagnostics = getDiagnostics();
    assertThat(diagnostics).hasSize(4);
    assertThat(diagnostics, true)
      .hasRange(2, 0, 3, 0)
      .hasRange(21, 0, 22, 0)
      .hasRange(36, 0, 39, 0)
      .hasRange(53, 0, 54, 0)
    ;
  }

  @Test
  void testConfigure4() {

    Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("maxEmptyLineCount", 4);
    diagnosticInstance.configure(configuration);

    List<Diagnostic> diagnostics = getDiagnostics();
    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true)
      .hasRange(37, 0, 39, 0)
    ;
  }


  @Test
  void test_EmptyTwoFirstLines() {

    configure(1);

    String module = "  \n" +
      "\n" +
      "КонецПроцедуры";

    List<Diagnostic> diagnostics = getDiagnostics(TestUtils.getDocumentContext(module));

    assertThat(diagnostics, true)
      .hasSize(1)
      .hasRange(0, 0, 1, 0)
    ;
  }

  @Test
  void test_EmptyThreeFirstLines() {

    configure(1);

    String module = "  \n" +
      "\n" +
      "\n" +
      "КонецПроцедуры";

    List<Diagnostic> diagnostics = getDiagnostics(TestUtils.getDocumentContext(module));

    assertThat(diagnostics, true)
      .hasSize(1)
      .hasRange(1, 0, 2, 0)
    ;
  }

  @Test
  void test_EmptyTwoInnerLines() {

    configure(1);

    String module = "Процедура Первая()\n" +
      "\n" +
      "\n" +
      "КонецПроцедуры";

    List<Diagnostic> diagnostics = getDiagnostics(TestUtils.getDocumentContext(module));

    assertThat(diagnostics, true)
      .hasSize(1)
      .hasRange(1, 0, 2, 0)
    ;
  }

  @Test
  void test_EmptyTwoInnerLinesWithSpaces() {

    configure(1);

    String module = "Процедура Первая()  \n" +
      " \n" +
      " \n" +
      "КонецПроцедуры";

    List<Diagnostic> diagnostics = getDiagnostics(TestUtils.getDocumentContext(module));

    assertThat(diagnostics, true)
      .hasSize(1)
      .hasRange(1, 0, 2, 0)
    ;
  }

  @Test
  void test_WorseEmptyTwoInnerLines() {

    configure(1);

    String module = "Процедура Первая()  \n" +
      "  \n" +
      "  Метод1(); //комментарии  \n" +
      "\n" +
      "  \n" +
      "КонецПроцедуры";

    List<Diagnostic> diagnostics = getDiagnostics(TestUtils.getDocumentContext(module));

    assertThat(diagnostics, true)
      .hasSize(1)
      .hasRange(3, 0, 4, 0)
    ;
  }

  @Test
  void test_EmptyThreeInnerLines() {

    configure(1);

    String module = "Процедура Первая()\n" +
      "\n" +
      "\n" +
      "\n" +
      "КонецПроцедуры";

    List<Diagnostic> diagnostics = getDiagnostics(TestUtils.getDocumentContext(module));

    assertThat(diagnostics, true)
      .hasSize(1)
      .hasRange(2, 0, 3, 0)
    ;
  }

  @Test
  void test_EmptyLastLines() {

    configure(1);

    String module = "Перем А;\n" +
      "\n";

    List<Diagnostic> diagnostics = getDiagnostics(TestUtils.getDocumentContext(module));

//    assertThat(diagnostics, true) // А почему этот тест должен сработать?
//      .hasSize(1)
//      .hasRange(1, 0, 2, 0)
    ;
  }

  @Test
  void test_ConfigureEmptyLineParam() {

    configure(2);

    String module = "Процедура Первая()\n" +
      "\n" +
      "\n" +
      "\n" +
      "КонецПроцедуры";

    List<Diagnostic> diagnostics = getDiagnostics(TestUtils.getDocumentContext(module));

    assertThat(diagnostics, true)
      .hasRange(2, 0, 3, 0)
      .hasSize(1)
    ;
  }

  @Test
  void test_ConfigureEmptyLineParamNoIssue() {

    configure(2);

    String module = "Процедура Первая()\n" +
      "\n" +
      "\n" +
      "КонецПроцедуры";

    List<Diagnostic> diagnostics = getDiagnostics(TestUtils.getDocumentContext(module));

    assertThat(diagnostics, true)
      .hasSize(0)
    ;
  }

  private void configure(int maxEmptyLineCount) {
    Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("maxEmptyLineCount", maxEmptyLineCount);
    diagnosticInstance.configure(configuration);
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
