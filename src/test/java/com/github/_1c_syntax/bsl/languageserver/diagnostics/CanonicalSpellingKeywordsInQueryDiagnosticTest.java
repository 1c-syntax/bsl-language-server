/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2021
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

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class CanonicalSpellingKeywordsInQueryDiagnosticTest extends AbstractDiagnosticTest<CanonicalSpellingKeywordsInQueryDiagnostic> {
  CanonicalSpellingKeywordsInQueryDiagnosticTest() {
    super(CanonicalSpellingKeywordsInQueryDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(50);
    assertThat(diagnostics, true)
      .hasRange(6, 38, 6, 45) // выбрать
      .hasRange(6, 46, 6, 57) // РаЗРЕШЕННЫЕ
      .hasRange(6, 58, 6, 67) // рАЗЛИЧНЫЕ
      .hasRange(6, 68, 6, 74) // пЕРВЫЕ
      .hasRange(9, 39, 9, 44) // сумма
      .hasRange(9, 59, 9, 62) // как
      .hasRange(13, 39, 13, 49) // КОЛИЧЕсТВО
      .hasRange(17, 38, 17, 40) // из
      .hasRange(19, 40, 19, 45) // левое
      .hasRange(19, 46, 19, 56) // СОЕДИНЕНИе
      .hasRange(20, 41, 20, 47) // ПрАВОЕ
      .hasRange(21, 41, 21, 43) // По
      .hasRange(24, 38, 24, 41) // Где
      .hasRange(26, 39, 26, 40) // и
      .hasRange(27, 60, 27, 61) // в
      .hasRange(28, 62, 28, 70) // ИЕРАРХИи
      .hasRange(30, 38, 30, 51) // СГРУППИрОВАТЬ
      .hasRange(30, 52, 30, 54) // пО
      .hasRange(30, 55, 30, 67) // гРУППИРУЮЩИМ
      .hasRange(30, 68, 30, 75) // нАБОРАМ
      .hasRange(44, 38, 44, 48) // ОБЪЕДиНИТЬ
      .hasRange(44, 49, 44, 52) // ВСе
      .hasRange(47, 39, 47, 43) // null
      .hasRange(60, 38, 60, 49) // уПОРЯДОЧИТЬ
      .hasRange(60, 50, 60, 52) // пО
      .hasRange(61, 48, 61, 52) // вОЗР
      .hasRange(62, 52, 62, 56) // уБЫВ
      .hasRange(63, 38, 63, 43) // иТОГИ
      .hasRange(65, 38, 65, 40) // пО
      .hasRange(66, 39, 66, 44) // оБЩИЕ
      .hasRange(76, 40, 76, 47) // выбрать
      .hasRange(76, 48, 76, 59) // РаЗРЕШЕННЫЕ
      .hasRange(79, 47, 79, 52) // сумма
      .hasRange(87, 46, 87, 48) // из
      .hasRange(94, 46, 94, 49) // Где
      .hasRange(97, 68, 97, 69) // в
      .hasRange(98, 70, 98, 78) // ИЕРАРХИи
      .hasRange(100, 76, 100, 83) // нАБОРАМ
      .hasRange(100, 46, 100, 59) // СГРУППИрОВАТЬ
      .hasRange(100, 60, 100, 62) // пО
      .hasRange(100, 63, 100, 75) // гРУППИРУЮЩИМ
      .hasRange(117, 47, 117, 51) // null
      .hasRange(130, 46, 130, 57) // уПОРЯДОЧИТЬ
      .hasRange(130, 58, 130, 60) // пО
      .hasRange(131, 56, 131, 60) // вОЗР
      .hasRange(132, 60, 132, 64) // уБЫВ
      .hasRange(133, 46, 133, 51) // иТОГИ
      .hasRange(135, 46, 135, 48) // пО
      .hasRange(136, 47, 136, 52) // оБЩИЕ
      .hasRange(146, 76, 146, 82); // первые

  }

  @Test
  void testQuickFix() {

    final var documentContext = getDocumentContext();
    List<Diagnostic> diagnostics = getDiagnostics();
    final Diagnostic firstDiagnostic = diagnostics.get(0);

    List<CodeAction> quickFixes = getQuickFixes(firstDiagnostic);

    assertThat(quickFixes).hasSize(1);

    final CodeAction quickFix = quickFixes.get(0);

    assertThat(quickFix).of(diagnosticInstance).in(documentContext)
      .fixes(firstDiagnostic);

    assertThat(quickFix).in(documentContext)
      .hasChanges(1);

  }

}
