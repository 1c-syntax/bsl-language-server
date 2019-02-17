/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com>
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
package org.github._1c_syntax.bsl.languageserver.diagnostics;

import org.eclipse.lsp4j.Diagnostic;
import org.github._1c_syntax.bsl.languageserver.utils.RangeHelper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CanonicalSpellingKeywordsDiagnosticTest extends AbstractDiagnosticTest<CanonicalSpellingKeywordsDiagnostic> {

  CanonicalSpellingKeywordsDiagnosticTest() {
    super(CanonicalSpellingKeywordsDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(70);

    // ПереМ
    assertThat(diagnostics.get(0).getRange()).isEqualTo(RangeHelper.newRange(8, 4, 8, 8));
    // НЕОПРЕделено
    assertThat(diagnostics.get(1).getRange()).isEqualTo(RangeHelper.newRange(15, 8, 15, 19));
    // НоВый
    assertThat(diagnostics.get(2).getRange()).isEqualTo(RangeHelper.newRange(16, 8, 16, 12));
    // ЕслИ
    assertThat(diagnostics.get(3).getRange()).isEqualTo(RangeHelper.newRange(35, 4, 35, 7));
    // ТогдА
    assertThat(diagnostics.get(4).getRange()).isEqualTo(RangeHelper.newRange(35, 20, 35, 24));
    // ИначеЕСли
    assertThat(diagnostics.get(5).getRange()).isEqualTo(RangeHelper.newRange(37, 4, 37, 12));
    // ИнаЧе
    assertThat(diagnostics.get(6).getRange()).isEqualTo(RangeHelper.newRange(41, 4, 41, 8));
    // КонецЕсЛи
    assertThat(diagnostics.get(7).getRange()).isEqualTo(RangeHelper.newRange(43, 4, 43, 12));
    // ДЛЯ
    assertThat(diagnostics.get(8).getRange()).isEqualTo(RangeHelper.newRange(73, 4, 73, 6));
    // КАЖДОГО
    assertThat(diagnostics.get(9).getRange()).isEqualTo(RangeHelper.newRange(73, 8, 73, 14));
    // ИЗ
    assertThat(diagnostics.get(10).getRange()).isEqualTo(RangeHelper.newRange(73, 29, 73, 30));
    // ЦикЛ
    assertThat(diagnostics.get(11).getRange()).isEqualTo(RangeHelper.newRange(73, 34, 73, 37));
    // ПРервать
    assertThat(diagnostics.get(12).getRange()).isEqualTo(RangeHelper.newRange(74, 7, 74, 14));
    // ПРодолжить
    assertThat(diagnostics.get(13).getRange()).isEqualTo(RangeHelper.newRange(75, 7, 75, 16));
    // КонецЦиклА
    assertThat(diagnostics.get(14).getRange()).isEqualTo(RangeHelper.newRange(76, 4, 76, 13));
    // ПО
    assertThat(diagnostics.get(15).getRange()).isEqualTo(RangeHelper.newRange(79, 20, 79, 21));
    // ПокА
    assertThat(diagnostics.get(16).getRange()).isEqualTo(RangeHelper.newRange(84, 4, 84, 7));
    // и
    assertThat(diagnostics.get(17).getRange()).isEqualTo(RangeHelper.newRange(100, 10, 100, 10));
    // ИлИ
    assertThat(diagnostics.get(18).getRange()).isEqualTo(RangeHelper.newRange(100, 14, 100, 16));
    // нЕ
    assertThat(diagnostics.get(19).getRange()).isEqualTo(RangeHelper.newRange(100, 22, 100, 23));
    // ЛОЖЬ
    assertThat(diagnostics.get(20).getRange()).isEqualTo(RangeHelper.newRange(101, 8, 101, 11));
    // ИсТИна
    assertThat(diagnostics.get(21).getRange()).isEqualTo(RangeHelper.newRange(102, 8, 102, 13));
    // ПопЫтка
    assertThat(diagnostics.get(22).getRange()).isEqualTo(RangeHelper.newRange(116, 4, 116, 10));
    // ИсключенИЕ
    assertThat(diagnostics.get(23).getRange()).isEqualTo(RangeHelper.newRange(118, 4, 118, 13));
    // ВызваТЬИсключение
    assertThat(diagnostics.get(24).getRange()).isEqualTo(RangeHelper.newRange(119, 8, 119, 24));
    // КОНЕЦПопытки
    assertThat(diagnostics.get(25).getRange()).isEqualTo(RangeHelper.newRange(120, 4, 120, 15));
    // ПЕРейти
    assertThat(diagnostics.get(26).getRange()).isEqualTo(RangeHelper.newRange(131, 4, 131, 10));
    // ВЫполнить
    assertThat(diagnostics.get(27).getRange()).isEqualTo(RangeHelper.newRange(132, 4, 132, 12));
    // ПРОЦЕДУРА
    assertThat(diagnostics.get(28).getRange()).isEqualTo(RangeHelper.newRange(150, 0, 150, 8));
    // ЗнаЧ
    assertThat(diagnostics.get(29).getRange()).isEqualTo(RangeHelper.newRange(150, 16, 150, 19));
    // ЭКспорт
    assertThat(diagnostics.get(30).getRange()).isEqualTo(RangeHelper.newRange(150, 31, 150, 37));
    // КонецПРоцедуры
    assertThat(diagnostics.get(31).getRange()).isEqualTo(RangeHelper.newRange(151, 0, 151, 13));
    // ФункцИЯ
    assertThat(diagnostics.get(32).getRange()).isEqualTo(RangeHelper.newRange(154, 0, 154, 6));
    // ВоЗВрат
    assertThat(diagnostics.get(33).getRange()).isEqualTo(RangeHelper.newRange(155, 4, 155, 10));
    // КонецФункцИИ
    assertThat(diagnostics.get(34).getRange()).isEqualTo(RangeHelper.newRange(156, 0, 156, 11));

    // VAr
    assertThat(diagnostics.get(35).getRange()).isEqualTo(RangeHelper.newRange(165, 4, 165, 6));
    // UNDEFined
    assertThat(diagnostics.get(36).getRange()).isEqualTo(RangeHelper.newRange(172, 8, 172, 16));
    // nEw
    assertThat(diagnostics.get(37).getRange()).isEqualTo(RangeHelper.newRange(173, 8, 173, 10));
    // IF
    assertThat(diagnostics.get(38).getRange()).isEqualTo(RangeHelper.newRange(192, 4, 192, 5));
    // TheN
    assertThat(diagnostics.get(39).getRange()).isEqualTo(RangeHelper.newRange(192, 18, 192, 21));
    // ElsIF
    assertThat(diagnostics.get(40).getRange()).isEqualTo(RangeHelper.newRange(194, 4, 194, 8));
    // ELSE
    assertThat(diagnostics.get(41).getRange()).isEqualTo(RangeHelper.newRange(198, 4, 198, 7));
    // ENDIf
    assertThat(diagnostics.get(42).getRange()).isEqualTo(RangeHelper.newRange(200, 4, 200, 8));
    // FOR
    assertThat(diagnostics.get(43).getRange()).isEqualTo(RangeHelper.newRange(230, 4, 230, 6));
    // EACH
    assertThat(diagnostics.get(44).getRange()).isEqualTo(RangeHelper.newRange(230, 8, 230, 11));
    // IN
    assertThat(diagnostics.get(45).getRange()).isEqualTo(RangeHelper.newRange(230, 26, 230, 27));
    // DO
    assertThat(diagnostics.get(46).getRange()).isEqualTo(RangeHelper.newRange(230, 31, 230, 32));
    // BReak
    assertThat(diagnostics.get(47).getRange()).isEqualTo(RangeHelper.newRange(231, 7, 231, 11));
    // ContiNue
    assertThat(diagnostics.get(48).getRange()).isEqualTo(RangeHelper.newRange(232, 7, 232, 14));
    // EndDO
    assertThat(diagnostics.get(49).getRange()).isEqualTo(RangeHelper.newRange(233, 4, 233, 8));
    // TO
    assertThat(diagnostics.get(50).getRange()).isEqualTo(RangeHelper.newRange(236, 20, 236, 21));
    // WHILe
    assertThat(diagnostics.get(51).getRange()).isEqualTo(RangeHelper.newRange(241, 4, 241, 8));
    // AnD
    assertThat(diagnostics.get(52).getRange()).isEqualTo(RangeHelper.newRange(257, 10, 257, 12));
    // oR
    assertThat(diagnostics.get(53).getRange()).isEqualTo(RangeHelper.newRange(257, 16, 257, 17));
    // NOt
    assertThat(diagnostics.get(54).getRange()).isEqualTo(RangeHelper.newRange(257, 25, 257, 27));
    // FALSe
    assertThat(diagnostics.get(55).getRange()).isEqualTo(RangeHelper.newRange(258, 8, 258, 12));
    // TruE
    assertThat(diagnostics.get(56).getRange()).isEqualTo(RangeHelper.newRange(259, 8, 259, 11));
    // TRY
    assertThat(diagnostics.get(57).getRange()).isEqualTo(RangeHelper.newRange(273, 4, 273, 6));
    // EXcePt
    assertThat(diagnostics.get(58).getRange()).isEqualTo(RangeHelper.newRange(275, 4, 275, 9));
    // RAISE
    assertThat(diagnostics.get(59).getRange()).isEqualTo(RangeHelper.newRange(276, 8, 276, 12));
    // EndTrY
    assertThat(diagnostics.get(60).getRange()).isEqualTo(RangeHelper.newRange(277, 4, 277, 9));
    // GOTO
    assertThat(diagnostics.get(61).getRange()).isEqualTo(RangeHelper.newRange(288, 4, 288, 7));
    // EXECUTE
    assertThat(diagnostics.get(62).getRange()).isEqualTo(RangeHelper.newRange(289, 4, 289, 10));
    // PROCEDURE
    assertThat(diagnostics.get(63).getRange()).isEqualTo(RangeHelper.newRange(307, 0, 307, 8));
    // VaL
    assertThat(diagnostics.get(64).getRange()).isEqualTo(RangeHelper.newRange(307, 16, 307, 18));
    // ExPort
    assertThat(diagnostics.get(65).getRange()).isEqualTo(RangeHelper.newRange(307, 27, 307, 32));
    // EndPROCedure
    assertThat(diagnostics.get(66).getRange()).isEqualTo(RangeHelper.newRange(308, 0, 308, 11));
    // FUNCtion
    assertThat(diagnostics.get(67).getRange()).isEqualTo(RangeHelper.newRange(311, 0, 311, 7));
    // RETUrn
    assertThat(diagnostics.get(68).getRange()).isEqualTo(RangeHelper.newRange(312, 4, 312, 9));
    // EnDFunction
    assertThat(diagnostics.get(69).getRange()).isEqualTo(RangeHelper.newRange(313, 0, 313, 10));
  }
}
