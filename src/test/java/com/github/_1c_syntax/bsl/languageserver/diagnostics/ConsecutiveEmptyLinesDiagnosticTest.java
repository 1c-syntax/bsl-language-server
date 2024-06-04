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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
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
  void test_EmptyTwoFirstLines() {
    String module = """
       \s

      КонецПроцедуры""";

    List<Diagnostic> diagnostics = getDiagnosticsForText(module);

    assertThat(diagnostics, true)
      .hasRange(0, 0, 1, 0)
      .hasSize(1)
    ;
  }

  @Test
  void test_EmptyThreeFirstLines() {
    String module = """
       \s


      КонецПроцедуры""";

    List<Diagnostic> diagnostics = getDiagnosticsForText(module);

    assertThat(diagnostics, true)
      .hasRange(0, 0, 2, 0)
      .hasSize(1)
    ;
  }

  @Test
  void test_EmptyTwoInnerLines() {
    String module = """
      Процедура Первая()


      КонецПроцедуры""";

    List<Diagnostic> diagnostics = getDiagnosticsForText(module);

    assertThat(diagnostics, true)
      .hasRange(1, 0, 2, 0)
      .hasSize(1)
    ;
  }

  @Test
  void test_EmptyTwoInnerLinesWithSpaces() {
    String module = """
      Процедура Первая() \s
      \s
      \s
      КонецПроцедуры""";

    List<Diagnostic> diagnostics = getDiagnosticsForText(module);

    assertThat(diagnostics, true)
      .hasRange(1, 0, 2, 0)
      .hasSize(1)
    ;
  }

  @Test
  void test_WorseEmptyTwoInnerLines() {
    String module = """
      Процедура Первая() \s
       \s
        Метод1(); //комментарии \s

       \s
      КонецПроцедуры""";

    List<Diagnostic> diagnostics = getDiagnosticsForText(module);

    assertThat(diagnostics, true)
      .hasRange(3, 0, 4, 0)
      .hasSize(1)
    ;
  }

  @Test
  void test_EmptyThreeInnerLines() {
    String module = """
      Процедура Первая()



      КонецПроцедуры""";

    List<Diagnostic> diagnostics = getDiagnosticsForText(module);

    assertThat(diagnostics, true)
      .hasRange(1, 0, 3, 0)
      .hasSize(1)
    ;
  }

  @Test
  void test_EmptyLastLines() {
    String module = """
      Перем А;

      """;

    List<Diagnostic> diagnostics = getDiagnosticsForText(module);

    assertThat(diagnostics, true)
      .hasRange(1, 0, 2, 0)
      .hasSize(1)
    ;
  }

  @Test
  void test_EmptyModule() {
    String module = "";

    List<Diagnostic> diagnostics = getDiagnosticsForText(module);

    assertThat(diagnostics, true)
      .hasSize(0)
    ;
  }

  @Test
  void test_OneLine() {
    // если в конце файла есть пустая строка и EOF, то для пользователя это выглядит как 2 пустые строки
    // и правило должно сработать
    String module = "\n";

    List<Diagnostic> diagnostics = getDiagnosticsForText(module);

    assertThat(diagnostics, true)
      .hasRange(0, 0, 1, 0)
      .hasSize(1)
    ;
  }

  @Test
  void test_EmptyLinePlusOneFilledLine() {
    String module = "\n//комментарий";

    List<Diagnostic> diagnostics = getDiagnosticsForText(module);

    assertThat(diagnostics, true)
      .hasSize(0)
    ;
  }

  @Test
  void test_ConfigureEmptyLineParam() {
    setTwoForAllowedEmptyLinesCount();

    String module = """
      Процедура Первая()



      КонецПроцедуры""";

    List<Diagnostic> diagnostics = getDiagnosticsForText(module);

    assertThat(diagnostics, true)
      .hasRange(1, 0, 3, 0)
      .hasSize(1)
    ;
  }

  @Test
  void test_ConfigureEmptyLineParamNoIssue() {
    setTwoForAllowedEmptyLinesCount();

    String module = """
      Процедура Первая()


      КонецПроцедуры""";

    List<Diagnostic> diagnostics = getDiagnosticsForText(module);

    assertThat(diagnostics, true)
      .hasSize(0)
    ;
  }

  private void setTwoForAllowedEmptyLinesCount() {
    Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("allowedEmptyLinesCount", 2);
    diagnosticInstance.configure(configuration);
  }

  @Test
  void test_moduleWith_CRLF_And_Spaces() {
    checkFileText(false);
  }

  @Test
  void test_moduleWith_CR_And_Spaces_And_Tab() {
    checkFileText(true);
  }

  void checkFileText(boolean use_CR_WithTab) {

    String module = getText();

    if (use_CR_WithTab) {
      module = module.replace("\n", "\r");
      module = module.replace("  ", "\t");
    }

    List<Diagnostic> diagnostics = getDiagnosticsForText(module);

    assertThat(diagnostics, true)
      .hasRange(0, 0, 1, 0)
      .hasRange(5, 0, 6, 0)
      .hasRange(10, 0, 11, 0)
      .hasRange(14, 0, 15, 0)
      .hasRange(17, 0, 18, 0)
      .hasRange(22, 0, 23, 0)
      .hasRange(26, 0, 27, 0)
      .hasRange(29, 0, 31, 0)
      .hasRange(33, 0, 34, 0)
      .hasSize(9);
  }

  @Test
  void testQuickFix() {
    String module = getText();
    checkQuickFixes(module, true);
  }

  private void checkQuickFixes(String module, boolean haveFix) {
    final var documentContext = TestUtils.getDocumentContext(module);
    List<Diagnostic> diagnostics = getDiagnostics(documentContext);

    diagnostics.forEach(diagnostic -> checkFix(documentContext, diagnostic, haveFix));
  }

  @Test
  void testQuickFixLastLines() {
    String module = """
      Перем А;

      """;
    checkQuickFixes(module, true);
  }

  @Test
  void testQuickFixEmptyModule() {
    String module = "";
    checkQuickFixes(module, false);
  }

  @Test
  void testQuickFixOneLine() {
    String module = "\n";
    checkQuickFixes(module, true);
  }

  private void checkFix(DocumentContext documentContext, Diagnostic diagnostic, boolean haveFix) {
    List<CodeAction> quickFixes = getQuickFixes(diagnostic, documentContext);

    assertThat(quickFixes).hasSize(1);

    final CodeAction quickFix = quickFixes.get(0);

    if (haveFix) {
      assertThat(quickFix).of(diagnosticInstance).in(documentContext)
        .fixes(diagnostic);

      assertThat(quickFix).in(documentContext)
        .hasChanges(1);
    } else {
      assertThat(quickFix).in(documentContext)
        .hasChanges(0);
    }
  }

  private List<Diagnostic> getDiagnosticsForText(String textDocumentContent) {
    var documentContext = TestUtils.getDocumentContext(textDocumentContent);
    return getDiagnostics(documentContext);
  }
}
