/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2023
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

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class CanonicalSpellingKeywordsDiagnosticTest extends AbstractDiagnosticTest<CanonicalSpellingKeywordsDiagnostic> {

  CanonicalSpellingKeywordsDiagnosticTest() {
    super(CanonicalSpellingKeywordsDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(127);

    assertThat(diagnostics, true)
      // ПереМ
      .hasRange(8, 4, 8, 9)
      // НЕОПРЕделено
      .hasRange(15, 8, 15, 20)
      // НоВый
      .hasRange(16, 8, 16, 13)
      // ЕслИ
      .hasRange(35, 4, 35, 8)
      // ТогдА
      .hasRange(35, 20, 35, 25)
      // ИначеЕСли
      .hasRange(37, 4, 37, 13)
      // ИнаЧе
      .hasRange(41, 4, 41, 9)
      // КонецЕсЛи
      .hasRange(43, 4, 43, 13)
      // ДЛЯ
      .hasRange(73, 4, 73, 7)
      // КАЖДОГО
      .hasRange(73, 8, 73, 15)
      // ИЗ
      .hasRange(73, 29, 73, 31)
      // ЦикЛ
      .hasRange(73, 34, 73, 38)
      // ПРервать
      .hasRange(74, 7, 74, 15)
      // ПРодолжить
      .hasRange(75, 7, 75, 17)
      // КонецЦиклА
      .hasRange(76, 4, 76, 14)
      // ПО
      .hasRange(79, 20, 79, 22)
      // ПокА
      .hasRange(84, 4, 84, 8)
      // и
      .hasRange(100, 10, 100, 11)
      // ИлИ
      .hasRange(100, 14, 100, 17)
      // нЕ
      .hasRange(100, 22, 100, 24)
      // ЛОЖЬ
      .hasRange(101, 8, 101, 12)
      // ИсТИна
      .hasRange(102, 8, 102, 14)
      // ПопЫтка
      .hasRange(116, 4, 116, 11)
      // ИсключенИЕ
      .hasRange(118, 4, 118, 14)
      // ВызваТЬИсключение
      .hasRange(119, 8, 119, 25)
      // КОНЕЦПопытки
      .hasRange(120, 4, 120, 16)
      // ПЕРейти
      .hasRange(131, 4, 131, 11)
      // ВЫполнить
      .hasRange(132, 4, 132, 13)
      // ПРОЦЕДУРА
      .hasRange(152, 0, 152, 9)
      // ЗнаЧ
      .hasRange(152, 16, 152, 20)
      // ЭКспорт
      .hasRange(152, 31, 152, 38)
      // ДОБавитьОбработчик
      .hasRange(153, 4, 153, 22)
      // УДАлитьОбработчик
      .hasRange(154, 4, 154, 21)
      // КонецПРоцедуры
      .hasRange(155, 0, 155, 14)
      // ФункцИЯ
      .hasRange(158, 0, 158, 7)
      // ВоЗВрат
      .hasRange(159, 4, 159, 11)
      // КонецФункцИИ
      .hasRange(160, 0, 160, 12)
      // ЕСЛИ
      .hasRange(170, 1, 170, 5)
      // СеРвер
      .hasRange(170, 6, 170, 12)
      // ИЛи
      .hasRange(170, 13, 170, 16)
      // КлИЕнт
      .hasRange(170, 17, 170, 23)
      // МобильнОЕПриложениеКлиент
      .hasRange(170, 28, 170, 53)
      // МобильноеПриложениеСЕРВЕР
      .hasRange(170, 58, 170, 83)
      // МобильныйКЛИент
      .hasRange(170, 88, 170, 103)
      // ТОГДА
      .hasRange(170, 104, 170, 109)
      // ИначеЕСЛИ
      .hasRange(171, 1, 171, 10)
      // ТолстыйКЛИЕНТОбычноеПриложение
      .hasRange(171, 11, 171, 41)
      // ТолстыйКЛИЕНТУправляемоеПриложение
      .hasRange(171, 46, 171, 80)
      // ВнешнееСоЕДИНение
      .hasRange(171, 83, 171, 100)
      // ТонкийКЛИЕНТ
      .hasRange(172, 11, 172, 23)
      // ВЕБКлиент
      .hasRange(172, 26, 172, 35)
      // нЕ
      .hasRange(172, 38, 172, 40)
      // НАКлиенте
      .hasRange(172, 41, 172, 50)
      // НаСеРВере
      .hasRange(172, 56, 172, 65)
      // ИнАЧе
      .hasRange(173, 1, 173, 6)
      // КонецЕСЛИ
      .hasRange(174, 1, 174, 10)
      // НАСервере
      .hasRange(182, 1, 182, 10)
      // НАКлиенте
      .hasRange(192, 1, 192, 10)
      // НАСервереБезКонтекста
      .hasRange(202, 1, 202, 22)
      // НАКлиентеНаСервереБезКонтекста
      .hasRange(212, 1, 212, 31)
      // НАКлиентеНаСервере
      .hasRange(222, 1, 222, 19)
      // ОБЛАСТЬ
      .hasRange(231, 1, 231, 8)
      // КонецОбластИ
      .hasRange(232, 1, 232, 13)

      // VAr
      .hasRange(241, 4, 241, 7)
      // UNDEFined
      .hasRange(248, 8, 248, 17)
      // nEw
      .hasRange(249, 8, 249, 11)
      // IF
      .hasRange(268, 4, 268, 6)
      // TheN
      .hasRange(268, 18, 268, 22)
      // ElsIF
      .hasRange(270, 4, 270, 9)
      // ELSE
      .hasRange(274, 4, 274, 8)
      // ENDIf
      .hasRange(276, 4, 276, 9)
      // FOR
      .hasRange(306, 4, 306, 7)
      // EACH
      .hasRange(306, 8, 306, 12)
      // IN
      .hasRange(306, 26, 306, 28)
      // DO
      .hasRange(306, 31, 306, 33)
      // BReak
      .hasRange(307, 7, 307, 12)
      // ContiNue
      .hasRange(308, 7, 308, 15)
      // EndDO
      .hasRange(309, 4, 309, 9)
      // TO
      .hasRange(312, 20, 312, 22)
      // WHILe
      .hasRange(317, 4, 317, 9)
      // AnD
      .hasRange(333, 10, 333, 13)
      // oR
      .hasRange(333, 16, 333, 18)
      // NOt
      .hasRange(333, 25, 333, 28)
      // FALSe
      .hasRange(334, 8, 334, 13)
      // TruE
      .hasRange(335, 8, 335, 12)
      // TRY
      .hasRange(349, 4, 349, 7)
      // EXcePt
      .hasRange(351, 4, 351, 10)
      // RAISE
      .hasRange(352, 8, 352, 13)
      // EndTrY
      .hasRange(353, 4, 353, 10)
      // GOTO
      .hasRange(364, 4, 364, 8)
      // EXECUTE
      .hasRange(365, 4, 365, 11)
      // PROCEDURE
      .hasRange(385, 0, 385, 9)
      // VaL
      .hasRange(385, 16, 385, 19)
      // ExPort
      .hasRange(385, 27, 385, 33)
      // ADDHandler
      .hasRange(386, 4, 386, 14)
      // REMoveHandler
      .hasRange(387, 4, 387, 17)
      // EndPROCedure
      .hasRange(388, 0, 388, 12)
      // FUNCtion
      .hasRange(391, 0, 391, 8)
      // RETUrn
      .hasRange(392, 4, 392, 10)
      // EnDFunction
      .hasRange(393, 0, 393, 11)
      // if
      .hasRange(403, 1, 403, 3)
      // ServeR
      .hasRange(403, 4, 403, 10)
      // oR
      .hasRange(403, 11, 403, 13)
      // CLient
      .hasRange(403, 14, 403, 20)
      // MOBileAppClient
      .hasRange(403, 24, 403, 39)
      // MOBileAppServer
      .hasRange(403, 43, 403, 58)
      // MOBileClient
      .hasRange(403, 62, 403, 74)
      // THen
      .hasRange(403, 75, 403, 79)
      // ELSIf
      .hasRange(404, 1, 404, 6)
      // THickClientOrdinaryApplication
      .hasRange(404, 7, 404, 37)
      // THickClientManagedApplication
      .hasRange(404, 41, 404, 70)
      // ANd
      .hasRange(404, 71, 404, 74)
      // EXTernalConnection
      .hasRange(404, 75, 404, 93)
      // ThiNClient
      .hasRange(405, 7, 405, 17)
      // WEBClient
      .hasRange(405, 22, 405, 31)
      // NoT
      .hasRange(405, 36, 405, 39)
      // ATClient
      .hasRange(405, 40, 405, 48)
      // ATServer
      .hasRange(405, 57, 405, 65)
      // ElSe
      .hasRange(406, 1, 406, 5)
      // EndIF
      .hasRange(407, 1, 407, 6)
      // ATServer
      .hasRange(415, 1, 415, 9)
      // ATClient
      .hasRange(425, 1, 425, 9)
      // AtServerNOContext
      .hasRange(435, 1, 435, 18)
      // AtClientAtServerNOContext
      .hasRange(445, 1, 445, 26)
      // AtClientATServer
      .hasRange(455, 1, 455, 17)
      // RegioN
      .hasRange(464, 1, 464, 7)
      // EndRegioN
      .hasRange(465, 1, 465, 10)

    ;
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
