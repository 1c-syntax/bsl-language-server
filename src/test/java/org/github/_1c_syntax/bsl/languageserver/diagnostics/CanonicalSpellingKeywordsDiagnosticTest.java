/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
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

    assertThat(diagnostics).hasSize(127);

    // ПереМ
    assertThat(diagnostics.get(0).getRange()).isEqualTo(RangeHelper.newRange(8, 4, 8, 9));
    // НЕОПРЕделено
    assertThat(diagnostics.get(1).getRange()).isEqualTo(RangeHelper.newRange(15, 8, 15, 20));
    // НоВый
    assertThat(diagnostics.get(2).getRange()).isEqualTo(RangeHelper.newRange(16, 8, 16, 13));
    // ЕслИ
    assertThat(diagnostics.get(3).getRange()).isEqualTo(RangeHelper.newRange(35, 4, 35, 8));
    // ТогдА
    assertThat(diagnostics.get(4).getRange()).isEqualTo(RangeHelper.newRange(35, 20, 35, 25));
    // ИначеЕСли
    assertThat(diagnostics.get(5).getRange()).isEqualTo(RangeHelper.newRange(37, 4, 37, 13));
    // ИнаЧе
    assertThat(diagnostics.get(6).getRange()).isEqualTo(RangeHelper.newRange(41, 4, 41, 9));
    // КонецЕсЛи
    assertThat(diagnostics.get(7).getRange()).isEqualTo(RangeHelper.newRange(43, 4, 43, 13));
    // ДЛЯ
    assertThat(diagnostics.get(8).getRange()).isEqualTo(RangeHelper.newRange(73, 4, 73, 7));
    // КАЖДОГО
    assertThat(diagnostics.get(9).getRange()).isEqualTo(RangeHelper.newRange(73, 8, 73, 15));
    // ИЗ
    assertThat(diagnostics.get(10).getRange()).isEqualTo(RangeHelper.newRange(73, 29, 73, 31));
    // ЦикЛ
    assertThat(diagnostics.get(11).getRange()).isEqualTo(RangeHelper.newRange(73, 34, 73, 38));
    // ПРервать
    assertThat(diagnostics.get(12).getRange()).isEqualTo(RangeHelper.newRange(74, 7, 74, 15));
    // ПРодолжить
    assertThat(diagnostics.get(13).getRange()).isEqualTo(RangeHelper.newRange(75, 7, 75, 17));
    // КонецЦиклА
    assertThat(diagnostics.get(14).getRange()).isEqualTo(RangeHelper.newRange(76, 4, 76, 14));
    // ПО
    assertThat(diagnostics.get(15).getRange()).isEqualTo(RangeHelper.newRange(79, 20, 79, 22));
    // ПокА
    assertThat(diagnostics.get(16).getRange()).isEqualTo(RangeHelper.newRange(84, 4, 84, 8));
    // и
    assertThat(diagnostics.get(17).getRange()).isEqualTo(RangeHelper.newRange(100, 10, 100, 11));
    // ИлИ
    assertThat(diagnostics.get(18).getRange()).isEqualTo(RangeHelper.newRange(100, 14, 100, 17));
    // нЕ
    assertThat(diagnostics.get(19).getRange()).isEqualTo(RangeHelper.newRange(100, 22, 100, 24));
    // ЛОЖЬ
    assertThat(diagnostics.get(20).getRange()).isEqualTo(RangeHelper.newRange(101, 8, 101, 12));
    // ИсТИна
    assertThat(diagnostics.get(21).getRange()).isEqualTo(RangeHelper.newRange(102, 8, 102, 14));
    // ПопЫтка
    assertThat(diagnostics.get(22).getRange()).isEqualTo(RangeHelper.newRange(116, 4, 116, 11));
    // ИсключенИЕ
    assertThat(diagnostics.get(23).getRange()).isEqualTo(RangeHelper.newRange(118, 4, 118, 14));
    // ВызваТЬИсключение
    assertThat(diagnostics.get(24).getRange()).isEqualTo(RangeHelper.newRange(119, 8, 119, 25));
    // КОНЕЦПопытки
    assertThat(diagnostics.get(25).getRange()).isEqualTo(RangeHelper.newRange(120, 4, 120, 16));
    // ПЕРейти
    assertThat(diagnostics.get(26).getRange()).isEqualTo(RangeHelper.newRange(131, 4, 131, 11));
    // ВЫполнить
    assertThat(diagnostics.get(27).getRange()).isEqualTo(RangeHelper.newRange(132, 4, 132, 13));
    // ПРОЦЕДУРА
    assertThat(diagnostics.get(28).getRange()).isEqualTo(RangeHelper.newRange(152, 0, 152, 9));
    // ЗнаЧ
    assertThat(diagnostics.get(29).getRange()).isEqualTo(RangeHelper.newRange(152, 16, 152, 20));
    // ЭКспорт
    assertThat(diagnostics.get(30).getRange()).isEqualTo(RangeHelper.newRange(152, 31, 152, 38));
    // ДОБавитьОбработчик
    assertThat(diagnostics.get(31).getRange()).isEqualTo(RangeHelper.newRange(153, 4, 153, 22));
    // УДАлитьОбработчик
    assertThat(diagnostics.get(32).getRange()).isEqualTo(RangeHelper.newRange(154, 4, 154, 21));
    // КонецПРоцедуры
    assertThat(diagnostics.get(33).getRange()).isEqualTo(RangeHelper.newRange(155, 0, 155, 14));
    // ФункцИЯ
    assertThat(diagnostics.get(34).getRange()).isEqualTo(RangeHelper.newRange(158, 0, 158, 7));
    // ВоЗВрат
    assertThat(diagnostics.get(35).getRange()).isEqualTo(RangeHelper.newRange(159, 4, 159, 11));
    // КонецФункцИИ
    assertThat(diagnostics.get(36).getRange()).isEqualTo(RangeHelper.newRange(160, 0, 160, 12));
    // ЕСЛИ
    assertThat(diagnostics.get(37).getRange()).isEqualTo(RangeHelper.newRange(170, 1, 170, 5));
    // СеРвер
    assertThat(diagnostics.get(38).getRange()).isEqualTo(RangeHelper.newRange(170, 6, 170, 12));
    // ИЛи
    assertThat(diagnostics.get(39).getRange()).isEqualTo(RangeHelper.newRange(170, 13, 170, 16));
    // КлИЕнт
    assertThat(diagnostics.get(40).getRange()).isEqualTo(RangeHelper.newRange(170, 17, 170, 23));
    // МобильнОЕПриложениеКлиент
    assertThat(diagnostics.get(41).getRange()).isEqualTo(RangeHelper.newRange(170, 28, 170, 53));
    // МобильноеПриложениеСЕРВЕР
    assertThat(diagnostics.get(42).getRange()).isEqualTo(RangeHelper.newRange(170, 58, 170, 83));
    // МобильныйКЛИент
    assertThat(diagnostics.get(43).getRange()).isEqualTo(RangeHelper.newRange(170, 88, 170, 103));
    // ТОГДА
    assertThat(diagnostics.get(44).getRange()).isEqualTo(RangeHelper.newRange(170, 104, 170, 109));
    // ИначеЕСЛИ
    assertThat(diagnostics.get(45).getRange()).isEqualTo(RangeHelper.newRange(171, 1, 171, 10));
    // ТолстыйКЛИЕНТОбычноеПриложение
    assertThat(diagnostics.get(46).getRange()).isEqualTo(RangeHelper.newRange(171, 11, 171, 41));
    // ТолстыйКЛИЕНТУправляемоеПриложение
    assertThat(diagnostics.get(47).getRange()).isEqualTo(RangeHelper.newRange(171, 46, 171, 80));
    // ВнешнееСоЕДИНение
    assertThat(diagnostics.get(48).getRange()).isEqualTo(RangeHelper.newRange(171, 83, 171, 100));
    // ТонкийКЛИЕНТ
    assertThat(diagnostics.get(49).getRange()).isEqualTo(RangeHelper.newRange(172, 11, 172, 23));
    // ВЕБКлиент
    assertThat(diagnostics.get(50).getRange()).isEqualTo(RangeHelper.newRange(172, 26, 172, 35));
    // нЕ
    assertThat(diagnostics.get(51).getRange()).isEqualTo(RangeHelper.newRange(172, 38, 172, 40));
    // НАКлиенте
    assertThat(diagnostics.get(52).getRange()).isEqualTo(RangeHelper.newRange(172, 41, 172, 50));
    // НаСеРВере
    assertThat(diagnostics.get(53).getRange()).isEqualTo(RangeHelper.newRange(172, 56, 172, 65));
    // ИнАЧе
    assertThat(diagnostics.get(54).getRange()).isEqualTo(RangeHelper.newRange(173, 1, 173, 6));
    // КонецЕСЛИ
    assertThat(diagnostics.get(55).getRange()).isEqualTo(RangeHelper.newRange(174, 1, 174, 10));
    // НАСервере
    assertThat(diagnostics.get(56).getRange()).isEqualTo(RangeHelper.newRange(182, 1, 182, 10));
    // НАКлиенте
    assertThat(diagnostics.get(57).getRange()).isEqualTo(RangeHelper.newRange(192, 1, 192, 10));
    // НАСервереБезКонтекста
    assertThat(diagnostics.get(58).getRange()).isEqualTo(RangeHelper.newRange(202, 1, 202, 22));
    // НАКлиентеНаСервереБезКонтекста
    assertThat(diagnostics.get(59).getRange()).isEqualTo(RangeHelper.newRange(212, 1, 212, 31));
    // НАКлиентеНаСервере
    assertThat(diagnostics.get(60).getRange()).isEqualTo(RangeHelper.newRange(222, 1, 222, 19));
    // ОБЛАСТЬ
    assertThat(diagnostics.get(61).getRange()).isEqualTo(RangeHelper.newRange(231, 1, 231, 8));
    // КонецОбластИ
    assertThat(diagnostics.get(62).getRange()).isEqualTo(RangeHelper.newRange(232, 1, 232, 13));

    // VAr
    assertThat(diagnostics.get(63).getRange()).isEqualTo(RangeHelper.newRange(241, 4, 241, 7));
    // UNDEFined
    assertThat(diagnostics.get(64).getRange()).isEqualTo(RangeHelper.newRange(248, 8, 248, 17));
    // nEw
    assertThat(diagnostics.get(65).getRange()).isEqualTo(RangeHelper.newRange(249, 8, 249, 11));
    // IF
    assertThat(diagnostics.get(66).getRange()).isEqualTo(RangeHelper.newRange(268, 4, 268, 6));
    // TheN
    assertThat(diagnostics.get(67).getRange()).isEqualTo(RangeHelper.newRange(268, 18, 268, 22));
    // ElsIF
    assertThat(diagnostics.get(68).getRange()).isEqualTo(RangeHelper.newRange(270, 4, 270, 9));
    // ELSE
    assertThat(diagnostics.get(69).getRange()).isEqualTo(RangeHelper.newRange(274, 4, 274, 8));
    // ENDIf
    assertThat(diagnostics.get(70).getRange()).isEqualTo(RangeHelper.newRange(276, 4, 276, 9));
    // FOR
    assertThat(diagnostics.get(71).getRange()).isEqualTo(RangeHelper.newRange(306, 4, 306, 7));
    // EACH
    assertThat(diagnostics.get(72).getRange()).isEqualTo(RangeHelper.newRange(306, 8, 306, 12));
    // IN
    assertThat(diagnostics.get(73).getRange()).isEqualTo(RangeHelper.newRange(306, 26, 306, 28));
    // DO
    assertThat(diagnostics.get(74).getRange()).isEqualTo(RangeHelper.newRange(306, 31, 306, 33));
    // BReak
    assertThat(diagnostics.get(75).getRange()).isEqualTo(RangeHelper.newRange(307, 7, 307, 12));
    // ContiNue
    assertThat(diagnostics.get(76).getRange()).isEqualTo(RangeHelper.newRange(308, 7, 308, 15));
    // EndDO
    assertThat(diagnostics.get(77).getRange()).isEqualTo(RangeHelper.newRange(309, 4, 309, 9));
    // TO
    assertThat(diagnostics.get(78).getRange()).isEqualTo(RangeHelper.newRange(312, 20, 312, 22));
    // WHILe
    assertThat(diagnostics.get(79).getRange()).isEqualTo(RangeHelper.newRange(317, 4, 317, 9));
    // AnD
    assertThat(diagnostics.get(80).getRange()).isEqualTo(RangeHelper.newRange(333, 10, 333, 13));
    // oR
    assertThat(diagnostics.get(81).getRange()).isEqualTo(RangeHelper.newRange(333, 16, 333, 18));
    // NOt
    assertThat(diagnostics.get(82).getRange()).isEqualTo(RangeHelper.newRange(333, 25, 333, 28));
    // FALSe
    assertThat(diagnostics.get(83).getRange()).isEqualTo(RangeHelper.newRange(334, 8, 334, 13));
    // TruE
    assertThat(diagnostics.get(84).getRange()).isEqualTo(RangeHelper.newRange(335, 8, 335, 12));
    // TRY
    assertThat(diagnostics.get(85).getRange()).isEqualTo(RangeHelper.newRange(349, 4, 349, 7));
    // EXcePt
    assertThat(diagnostics.get(86).getRange()).isEqualTo(RangeHelper.newRange(351, 4, 351, 10));
    // RAISE
    assertThat(diagnostics.get(87).getRange()).isEqualTo(RangeHelper.newRange(352, 8, 352, 13));
    // EndTrY
    assertThat(diagnostics.get(88).getRange()).isEqualTo(RangeHelper.newRange(353, 4, 353, 10));
    // GOTO
    assertThat(diagnostics.get(89).getRange()).isEqualTo(RangeHelper.newRange(364, 4, 364, 8));
    // EXECUTE
    assertThat(diagnostics.get(90).getRange()).isEqualTo(RangeHelper.newRange(365, 4, 365, 11));
    // PROCEDURE
    assertThat(diagnostics.get(91).getRange()).isEqualTo(RangeHelper.newRange(385, 0, 385, 9));
    // VaL
    assertThat(diagnostics.get(92).getRange()).isEqualTo(RangeHelper.newRange(385, 16, 385, 19));
    // ExPort
    assertThat(diagnostics.get(93).getRange()).isEqualTo(RangeHelper.newRange(385, 27, 385, 33));
    // ADDHandler
    assertThat(diagnostics.get(94).getRange()).isEqualTo(RangeHelper.newRange(386, 4, 386, 14));
    // REMoveHandler
    assertThat(diagnostics.get(95).getRange()).isEqualTo(RangeHelper.newRange(387, 4, 387, 17));
    // EndPROCedure
    assertThat(diagnostics.get(96).getRange()).isEqualTo(RangeHelper.newRange(388, 0, 388, 12));
    // FUNCtion
    assertThat(diagnostics.get(97).getRange()).isEqualTo(RangeHelper.newRange(391, 0, 391, 8));
    // RETUrn
    assertThat(diagnostics.get(98).getRange()).isEqualTo(RangeHelper.newRange(392, 4, 392, 10));
    // EnDFunction
    assertThat(diagnostics.get(99).getRange()).isEqualTo(RangeHelper.newRange(393, 0, 393, 11));
    // if
    assertThat(diagnostics.get(100).getRange()).isEqualTo(RangeHelper.newRange(403, 1, 403, 3));
    // ServeR
    assertThat(diagnostics.get(101).getRange()).isEqualTo(RangeHelper.newRange(403, 4, 403, 10));
    // oR
    assertThat(diagnostics.get(102).getRange()).isEqualTo(RangeHelper.newRange(403, 11, 403, 13));
    // CLient
    assertThat(diagnostics.get(103).getRange()).isEqualTo(RangeHelper.newRange(403, 14, 403, 20));
    // MOBileAppClient
    assertThat(diagnostics.get(104).getRange()).isEqualTo(RangeHelper.newRange(403, 24, 403, 39));
    // MOBileAppServer
    assertThat(diagnostics.get(105).getRange()).isEqualTo(RangeHelper.newRange(403, 43, 403, 58));
    // MOBileClient
    assertThat(diagnostics.get(106).getRange()).isEqualTo(RangeHelper.newRange(403, 62, 403, 74));
    // THen
    assertThat(diagnostics.get(107).getRange()).isEqualTo(RangeHelper.newRange(403, 75, 403, 79));
    // ELSIf
    assertThat(diagnostics.get(108).getRange()).isEqualTo(RangeHelper.newRange(404, 1, 404, 6));
    // THickClientOrdinaryApplication
    assertThat(diagnostics.get(109).getRange()).isEqualTo(RangeHelper.newRange(404, 7, 404, 37));
    // THickClientManagedApplication
    assertThat(diagnostics.get(110).getRange()).isEqualTo(RangeHelper.newRange(404, 41, 404, 70));
    // ANd
    assertThat(diagnostics.get(111).getRange()).isEqualTo(RangeHelper.newRange(404, 71, 404, 74));
    // EXTernalConnection
    assertThat(diagnostics.get(112).getRange()).isEqualTo(RangeHelper.newRange(404, 75, 404, 93));
    // ThiNClient
    assertThat(diagnostics.get(113).getRange()).isEqualTo(RangeHelper.newRange(405, 7, 405, 17));
    // WEBClient
    assertThat(diagnostics.get(114).getRange()).isEqualTo(RangeHelper.newRange(405, 22, 405, 31));
    // NoT
    assertThat(diagnostics.get(115).getRange()).isEqualTo(RangeHelper.newRange(405, 36, 405, 39));
    // ATClient
    assertThat(diagnostics.get(116).getRange()).isEqualTo(RangeHelper.newRange(405, 40, 405, 48));
    // ATServer
    assertThat(diagnostics.get(117).getRange()).isEqualTo(RangeHelper.newRange(405, 57, 405, 65));
    // ElSe
    assertThat(diagnostics.get(118).getRange()).isEqualTo(RangeHelper.newRange(406, 1, 406, 5));
    // EndIF
    assertThat(diagnostics.get(119).getRange()).isEqualTo(RangeHelper.newRange(407, 1, 407, 6));
    // ATServer
    assertThat(diagnostics.get(120).getRange()).isEqualTo(RangeHelper.newRange(415, 1, 415, 9));
    // ATClient
    assertThat(diagnostics.get(121).getRange()).isEqualTo(RangeHelper.newRange(425, 1, 425, 9));
    // AtServerNOContext
    assertThat(diagnostics.get(122).getRange()).isEqualTo(RangeHelper.newRange(435, 1, 435, 18));
    // AtClientAtServerNOContext
    assertThat(diagnostics.get(123).getRange()).isEqualTo(RangeHelper.newRange(445, 1, 445, 26));
    // AtClientATServer
    assertThat(diagnostics.get(124).getRange()).isEqualTo(RangeHelper.newRange(455, 1, 455, 17));
    // RegioN
    assertThat(diagnostics.get(125).getRange()).isEqualTo(RangeHelper.newRange(464, 1, 464, 7));
    // EndRegioN
    assertThat(diagnostics.get(126).getRange()).isEqualTo(RangeHelper.newRange(465, 1, 465, 10));
  }
}
