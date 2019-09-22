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

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Diagnostic;
import org.github._1c_syntax.bsl.languageserver.configuration.DiagnosticLanguage;
import org.github._1c_syntax.bsl.languageserver.providers.DiagnosticProvider;
import org.github._1c_syntax.bsl.languageserver.utils.RangeHelper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class MissingSpaceDiagnosticTest extends AbstractDiagnosticTest<MissingSpaceDiagnostic> {

  MissingSpaceDiagnosticTest() {
    super(MissingSpaceDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(25);
    assertThat(diagnostics)
      // на +
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(4, 18, 4, 19)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(7, 12, 7, 13)))
      // на =
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(4, 15, 4, 16)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(7, 5, 7, 6)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(8, 5, 8, 6)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(9, 5, 9, 6)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(10, 6, 10, 7)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(11, 6, 11, 7)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(13, 5, 13, 6)))
      // на ,
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(4, 9, 4, 10)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(17, 18, 17, 19)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(31, 9, 31, 10)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(31, 27, 31, 28)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(31, 28, 31, 29)))
      // на ;
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(4, 12, 4, 13)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(31, 13, 31, 14)))
      // на -
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(8, 12, 8, 13)))
      // на *
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(10, 13, 10, 14)))
      // на /
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(11, 14, 11, 15)))
      // на %
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(13, 13, 13, 14)))
      // на >
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(18, 8, 18, 9)))
      // на <
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(19, 9, 19, 10)))
      // на >=
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(22, 8, 22, 10)))
      // на <=
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(23, 9, 23, 11)))
      // на <>
      .anyMatch(diagnostic -> diagnostic.getRange().equals(RangeHelper.newRange(25, 8, 25, 10)))
    ;
  }

  @Test
  void testQuickFix() {

    List<Diagnostic> diagnostics = getDiagnostics();
    List<CodeAction> quickFixes = getQuickFixes(
      diagnostics.get(23),
      RangeHelper.newRange(10, 8, 10, 8)
    );

    assertThat(quickFixes)
      .hasSize(1)
      .first()
      .matches(codeAction -> codeAction.getKind().equals(CodeActionKind.QuickFix))

      .matches(codeAction -> codeAction.getDiagnostics().size() == 1)
      .matches(codeAction -> codeAction.getDiagnostics().get(0).equals(diagnostics.get(23)))

      .matches(codeAction -> codeAction.getEdit().getChanges().size() == 1)
      .matches(codeAction ->
        codeAction.getEdit().getChanges().get("file:///fake-uri.bsl").get(0).getNewText().equals(" ")
      )
    ;
  }

  @Test
  void testConfigure() {

    List<Diagnostic> diagnostics;

    Map<String, Object> configuration = DiagnosticProvider.getDefaultDiagnosticConfiguration(getDiagnosticInstance());
    configuration.put("listForCheckLeft", ")");
    configuration.put("listForCheckRight", "(");
    configuration.put("listForCheckLeftAndRight", "");
    getDiagnosticInstance().configure(configuration);

    diagnostics = getDiagnostics();
    assertThat(diagnostics).hasSize(12);
    // на )
    assertThat(diagnostics.get(0).getRange()).isEqualTo(RangeHelper.newRange(3, 31, 3, 32));
    assertThat(diagnostics.get(1).getRange()).isEqualTo(RangeHelper.newRange(17, 26, 17, 27));
    assertThat(diagnostics.get(2).getRange()).isEqualTo(RangeHelper.newRange(31, 12, 31, 13));
    assertThat(diagnostics.get(3).getRange()).isEqualTo(RangeHelper.newRange(31, 31, 31, 32));
    assertThat(diagnostics.get(4).getRange()).isEqualTo(RangeHelper.newRange(33, 17, 33, 18));
    assertThat(diagnostics.get(5).getRange()).isEqualTo(RangeHelper.newRange(36, 4, 36, 5));
    // на (
    assertThat(diagnostics.get(6).getRange()).isEqualTo(RangeHelper.newRange(3, 16, 3, 17));
    assertThat(diagnostics.get(7).getRange()).isEqualTo(RangeHelper.newRange(17, 16, 17, 17));
    assertThat(diagnostics.get(8).getRange()).isEqualTo(RangeHelper.newRange(31, 6, 31, 7));
    assertThat(diagnostics.get(9).getRange()).isEqualTo(RangeHelper.newRange(31, 20, 31, 21));
    assertThat(diagnostics.get(10).getRange()).isEqualTo(RangeHelper.newRange(33, 6, 33, 7));
    assertThat(diagnostics.get(11).getRange()).isEqualTo(RangeHelper.newRange(34, 6, 34, 7));


    configuration = DiagnosticProvider.getDefaultDiagnosticConfiguration(getDiagnosticInstance());
    configuration.put("listForCheckLeft", "");
    configuration.put("listForCheckRight", "");
    configuration.put("listForCheckLeftAndRight", "-");
    configuration.put("checkSpaceToRightOfUnary", true);
    getDiagnosticInstance().configure(configuration);


    diagnostics = getDiagnostics();
    assertThat(diagnostics).hasSize(2);
    assertThat(diagnostics.get(0).getRange()).isEqualTo(RangeHelper.newRange(8, 12, 8, 13));
    assertThat(diagnostics.get(1).getRange()).isEqualTo(RangeHelper.newRange(27, 10, 27, 11));


    configuration = DiagnosticProvider.getDefaultDiagnosticConfiguration(getDiagnosticInstance());
    configuration.put("listForCheckLeft", "");
    configuration.put("listForCheckRight", ",");
    configuration.put("listForCheckLeftAndRight", "");
    configuration.put("allowMultipleCommas", true);
    getDiagnosticInstance().configure(configuration);

    diagnostics = getDiagnostics();
    assertThat(diagnostics).hasSize(4);
    assertThat(diagnostics.get(0).getRange()).isEqualTo(RangeHelper.newRange(4, 9, 4, 10));
    assertThat(diagnostics.get(1).getRange()).isEqualTo(RangeHelper.newRange(17, 18, 17, 19));
    assertThat(diagnostics.get(2).getRange()).isEqualTo(RangeHelper.newRange(31, 9, 31, 10));
    assertThat(diagnostics.get(3).getRange()).isEqualTo(RangeHelper.newRange(31, 28, 31, 29));
  }
}
