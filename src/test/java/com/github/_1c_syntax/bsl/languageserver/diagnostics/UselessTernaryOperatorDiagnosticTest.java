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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Тесты диагностики {@link UselessTernaryOperatorDiagnostic}.
 * Проверяют срабатывания на бесполезных тернарных операторах, корректность
 * быстрых исправлений и устойчивость к некорректному синтаксису.
 */
class UselessTernaryOperatorDiagnosticTest extends AbstractDiagnosticTest<UselessTernaryOperatorDiagnostic> {

  UselessTernaryOperatorDiagnosticTest() {
    super(UselessTernaryOperatorDiagnostic.class);
  }

  /**
   * Проверяет диапазоны срабатываний диагностики на наборе бесполезных
   * тернарных операторов в {@code UselessTernaryOperatorDiagnostic.bsl}:
   * упрощаемые ({@code TRUE/FALSE}, {@code FALSE/TRUE}), одинаковые булевы
   * ветки и константные условия. Тернарники, в которых булевой константой
   * является только одна ветка, считаются валидными и в счёт не идут.
   */
  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(6);
    assertThat(diagnostics, true)
      .hasRange(1, 4, 1, 26)
      .hasRange(2, 4, 2, 25)
      .hasRange(3, 4, 3, 26)
      .hasRange(4, 4, 4, 25)
      .hasRange(5, 4, 5, 19)
      .hasRange(6, 4, 6, 18);

  }

  /**
   * Проверяет быстрые исправления для двух упрощаемых случаев:
   * прямого ({@code ?(X, Истина, Ложь)} → {@code X}) и обратного
   * ({@code ?(X, Ложь, Истина)} → {@code НЕ X}).
   */
  @Test
  void testQuickFix() {

    final DocumentContext documentContext = getDocumentContext();
    List<Diagnostic> diagnostics = getDiagnostics();

    final Diagnostic directDiagnostic = diagnostics.getFirst();
    List<CodeAction> directQuickFixes = getQuickFixes(directDiagnostic);
    assertThat(directQuickFixes).hasSize(1);
    final CodeAction directQuickFix = directQuickFixes.getFirst();
    assertThat(directQuickFix)
      .of(diagnosticInstance)
      .in(documentContext)
      .fixes(directDiagnostic)
      .hasChanges(1)
      .hasNewText("Б=1");

    final Diagnostic reversDiagnostic = diagnostics.get(1);
    List<CodeAction> reversQuickFixes = getQuickFixes(reversDiagnostic);
    assertThat(reversQuickFixes).hasSize(1);
    final CodeAction reversQuickFix = reversQuickFixes.getFirst();
    assertThat(reversQuickFix)
      .of(diagnosticInstance)
      .in(documentContext)
      .fixes(reversDiagnostic)
      .hasChanges(1)
      .hasNewText("НЕ (Б=0)");
  }

  /**
   * Проверяет, что на некорректном синтаксисе тернарного оператора
   * (пример: {@code Return ?(table.Count() = 1, undefined, );}) диагностика
   * не падает с {@link NullPointerException}.
   */
  @Test
  void testMalformedTernaryOperatorDoesNotThrowNPE() {
    var documentContext = getDocumentContext("UselessTernaryOperatorDiagnosticMalformed");
    
    assertThatCode(() -> getDiagnostics(documentContext))
      .doesNotThrowAnyException();
  }

}
