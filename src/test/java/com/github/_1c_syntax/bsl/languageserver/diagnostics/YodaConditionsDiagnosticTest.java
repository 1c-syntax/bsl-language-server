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
import java.util.Set;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

/**
 * Тесты диагностики {@link YodaConditionsDiagnostic}.
 * Проверяют срабатывания на сравнениях с литералом слева и отсутствие
 * срабатываний на корректном порядке и сравнениях двух литералов.
 */
class YodaConditionsDiagnosticTest extends AbstractDiagnosticTest<YodaConditionsDiagnostic> {

  YodaConditionsDiagnosticTest() {
    super(YodaConditionsDiagnostic.class);
  }

  /**
   * Проверяет срабатывание на литералах всех видов (число, строка, дата, булево,
   * знаковый литерал), на всех операторах сравнения и на двух сравнениях внутри
   * одного условия.
   */
  @Test
  void test() {
    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(15);
    assertThat(diagnostics, true)
      .hasRange(3, 9, 3, 18)
      .hasRange(8, 9, 8, 21)
      .hasRange(13, 9, 13, 26)
      .hasRange(18, 9, 18, 22)
      .hasRange(23, 9, 23, 19)
      .hasRange(28, 9, 28, 18)
      .hasRange(32, 9, 32, 19)
      .hasRange(36, 9, 36, 18)
      .hasRange(40, 9, 40, 19)
      .hasRange(44, 9, 44, 19)
      .hasRange(49, 10, 49, 19)
      .hasRange(49, 24, 49, 33)
      .hasRange(68, 9, 68, 26)
      .hasRange(72, 9, 72, 19)
      .hasRange(76, 9, 76, 35);
  }

  /**
   * Проверяет отсутствие срабатываний на правильном порядке операндов
   * и на сравнении двух литералов.
   */
  @Test
  void noFalsePositives() {
    List<Diagnostic> diagnostics = getDiagnostics();
    Set<Integer> correctLines = Set.of(54, 59, 63);

    assertThat(diagnostics)
      .noneMatch(diagnostic -> correctLines.contains(diagnostic.getRange().getStart().getLine()));
  }
}
