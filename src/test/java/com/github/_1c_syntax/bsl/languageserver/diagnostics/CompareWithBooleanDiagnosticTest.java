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

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

/**
 * Тесты диагностики {@link CompareWithBooleanDiagnostic}.
 * Проверяют срабатывания на сравнениях с булевой константой и отсутствие
 * срабатываний на корректных конструкциях.
 */
class CompareWithBooleanDiagnosticTest extends AbstractDiagnosticTest<CompareWithBooleanDiagnostic> {

  CompareWithBooleanDiagnosticTest() {
    super(CompareWithBooleanDiagnostic.class);
  }

  /**
   * Проверяет срабатывание диагностики на сравнениях с булевой константой
   * ({@code = Истина}, {@code <> Ложь}, {@code Истина = ...}) и отсутствие
   * срабатываний на корректных конструкциях.
   */
  @Test
  void test() {
    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(3);
    assertThat(diagnostics, true)
      .hasRange(4, 9, 4, 22)
      .hasRange(9, 9, 9, 20)
      .hasRange(14, 9, 14, 22);
  }

  /**
   * Проверяет отсутствие ложного срабатывания при сравнении с булевой константой
   * операнда, тип которого является объединением ({@code Булево | Строка}) — такое
   * сравнение может быть обоснованным (например для {@code БезопасныйРежим()}).
   * В фикстуре переменная {@code Результат} получает типы {@code Булево} и
   * {@code Строка} в разных ветках, сравнение с {@code Истина} на строке 35 (0-based 34)
   * не должно подсвечиваться.
   */
  @Test
  void noFalsePositiveOnUnionType() {
    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).noneMatch(d -> d.getRange().getStart().getLine() == 34);
  }

}
