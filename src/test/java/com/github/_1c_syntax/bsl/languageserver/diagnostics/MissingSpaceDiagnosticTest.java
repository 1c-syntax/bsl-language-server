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

import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;
import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.FAKE_DOCUMENT_URI;

class MissingSpaceDiagnosticTest extends AbstractDiagnosticTest<MissingSpaceDiagnostic> {

  MissingSpaceDiagnosticTest() {
    super(MissingSpaceDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics, true)
      // на +
      .hasRange(4, 18, 4, 19)
      .hasRange(7, 12, 7, 13)
      // на =
      .hasRange(4, 15, 4, 16)
      .hasRange(7, 5, 7, 6)
      .hasRange(8, 5, 8, 6)
      .hasRange(9, 5, 9, 6)
      .hasRange(10, 6, 10, 7)
      .hasRange(11, 6, 11, 7)
      .hasRange(13, 5, 13, 6)
      // на ,
      .hasRange(4, 9, 4, 10)
      .hasRange(17, 18, 17, 19)
      .hasRange(31, 9, 31, 10)
      .hasRange(31, 27, 31, 28)
      .hasRange(31, 28, 31, 29)
      // на ;
      .hasRange(4, 12, 4, 13)
      .hasRange(31, 13, 31, 14)
      // на -
      .hasRange(8, 12, 8, 13)
      // на *
      .hasRange(10, 13, 10, 14)
      // на /
      .hasRange(11, 14, 11, 15)
      // на %
      .hasRange(13, 13, 13, 14)
      // на >
      .hasRange(18, 8, 18, 9)
      // на <
      .hasRange(19, 9, 19, 10)
      // на >=
      .hasRange(22, 8, 22, 10)
      // на <=
      .hasRange(23, 9, 23, 11)
      // на <>
      .hasRange(25, 8, 25, 10)
      // проверка на отсутствие ошибки
      .hasRange(38, 3, 38, 4)
      .hasRange(38, 5, 38, 6)

      // с кавычками в строке
      .hasRange(43, 19, 20)
      .hasRange(43, 14, 15)

      // кейворды
      .hasRange(48, 17, 24)
      .hasRange(50, 0, 3)
      .hasRange(50, 11, 13)
      .hasRange(50, 16, 20)
      .hasRange(53, 4, 11)
      .hasRange(53, 17, 19)
      .hasRange(53, 30, 34)
      .hasRange(56, 0, 4)
      .hasRange(56, 12, 15)
      .hasRange(56, 21, 22)
      .hasRange(56, 30, 35)
      .hasRange(57, 0, 9)
      .hasRange(57, 17, 20)
      .hasRange(57, 21, 23)
      .hasRange(57, 30, 35)

      .hasSize(44)
    ;
  }

  @Test
  void testQuickFix() {

    List<Diagnostic> diagnostics = getDiagnostics();
    List<CodeAction> quickFixes = getQuickFixes(
      diagnostics.get(23),
      Ranges.create(10, 8, 10, 8)
    );

    assertThat(quickFixes)
      .hasSize(1)
      .first()
      .matches(codeAction -> codeAction.getKind().equals(CodeActionKind.QuickFix))

      .matches(codeAction -> codeAction.getDiagnostics().size() == 1)
      .matches(codeAction -> codeAction.getDiagnostics().get(0).equals(diagnostics.get(23)))

      .matches(codeAction -> codeAction.getEdit().getChanges().size() == 1)
      .matches(codeAction ->
        codeAction.getEdit().getChanges().get(FAKE_DOCUMENT_URI.toString()).get(0).getNewText().equals(" ")
      )
    ;
  }

  @Test
  void testConfigure() {

    List<Diagnostic> diagnostics;

    Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("listForCheckLeft", ")");
    configuration.put("listForCheckRight", "(");
    configuration.put("listForCheckLeftAndRight", "");
    diagnosticInstance.configure(configuration);

    diagnostics = getDiagnostics();
    // на )
    assertThat(diagnostics, true)
      .hasRange(3, 31, 3, 32)
      .hasRange(17, 26, 17, 27)
      .hasRange(31, 12, 31, 13)
      .hasRange(31, 31, 31, 32)
      .hasRange(33, 17, 33, 18)
      .hasRange(36, 4, 36, 5)
      .hasRange(41, 87, 41, 88)
      // на (
      .hasRange(3, 16, 3, 17)
      .hasRange(17, 16, 17, 17)
      .hasRange(31, 6, 31, 7)
      .hasRange(31, 20, 31, 21)
      .hasRange(33, 6, 33, 7)
      .hasRange(41, 45, 41, 46)
      .hasRange(43, 25, 43, 26)
      .hasRange(43, 32, 43, 33)
      .hasRange(45, 8, 45, 9)
      .hasRange(45, 22, 45, 23)
      .hasRange(48, 15, 16)

      .hasSize(55);

    configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("listForCheckLeft", "");
    configuration.put("listForCheckRight", "");
    configuration.put("listForCheckLeftAndRight", "-");
    configuration.put("checkSpaceToRightOfUnary", true);
    diagnosticInstance.configure(configuration);


    diagnostics = getDiagnostics();
    assertThat(diagnostics, true)
      .hasRange(8, 12, 8, 13)
      .hasRange(27, 10, 27, 11)
      .hasRange(41, 46, 41, 47)
      // кейворды
      .hasRange(48, 17, 24)
      .hasRange(50, 0, 3)
      .hasRange(50, 11, 13)
      .hasRange(50, 16, 20)
      .hasRange(53, 4, 11)
      .hasRange(53, 17, 19)
      .hasRange(53, 30, 34)
      .hasRange(56, 0, 4)
      .hasRange(56, 12, 15)
      .hasRange(56, 21, 22)
      .hasRange(56, 30, 35)
      .hasRange(57, 0, 9)
      .hasRange(57, 17, 20)
      .hasRange(57, 21, 23)
      .hasRange(57, 30, 35)
      .hasSize(18);


    configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("listForCheckLeft", "");
    configuration.put("listForCheckRight", ",");
    configuration.put("listForCheckLeftAndRight", "");
    configuration.put("allowMultipleCommas", true);
    diagnosticInstance.configure(configuration);

    diagnostics = getDiagnostics();
    assertThat(diagnostics, true)
      .hasRange(4, 9, 4, 10)
      .hasRange(17, 18, 17, 19)
      .hasRange(31, 9, 31, 10)
      .hasRange(31, 28, 31, 29)
      // кейворды
      .hasRange(48, 17, 24)
      .hasRange(50, 0, 3)
      .hasRange(50, 11, 13)
      .hasRange(50, 16, 20)
      .hasRange(53, 4, 11)
      .hasRange(53, 17, 19)
      .hasRange(53, 30, 34)
      .hasRange(56, 0, 4)
      .hasRange(56, 12, 15)
      .hasRange(56, 21, 22)
      .hasRange(56, 30, 35)
      .hasRange(57, 0, 9)
      .hasRange(57, 17, 20)
      .hasRange(57, 21, 23)
      .hasRange(57, 30, 35)
      .hasSize(19);
  }
}
