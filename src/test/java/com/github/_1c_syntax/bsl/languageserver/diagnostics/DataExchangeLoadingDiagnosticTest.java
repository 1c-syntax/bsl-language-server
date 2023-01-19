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

import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class DataExchangeLoadingDiagnosticTest extends AbstractDiagnosticTest<DataExchangeLoadingDiagnostic> {
  DataExchangeLoadingDiagnosticTest() {
    super(DataExchangeLoadingDiagnostic.class);
  }

  @Test
  void test() {
    List<Diagnostic> diagnostics;
    Map<String, Object> configuration;

    // Проверяем срабатывания без изменения параметров
    // when
    configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    diagnosticInstance.configure(configuration);
    diagnostics = getDiagnostics();

    // then
    assertThat(diagnostics)
      .hasSize(3)
      .anyMatch(diagnostic -> diagnostic.getRange().equals(
        Ranges.create(7, 10, 7, 22)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(
        Ranges.create(19, 10, 19, 17)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(
        Ranges.create(70, 10, 70, 22)))
    //fix me "Если НЕ обменданными"
//      .anyMatch(diagnostic -> diagnostic.getRange().equals(
//        Ranges.create(57, 10, 57, 22)));
    ;

    // Проверяем с включенным параметром findFirst
    // when
    configuration = diagnosticInstance.getInfo().getDefaultConfiguration();
    configuration.put("findFirst", true);
    diagnosticInstance.configure(configuration);
    diagnostics = getDiagnostics();

    // then
    assertThat(diagnostics)
      .hasSize(4)
      .anyMatch(diagnostic -> diagnostic.getRange().equals(
        Ranges.create(7, 10, 7, 22)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(
        Ranges.create(19, 10, 19, 17)))
      .anyMatch(diagnostic -> diagnostic.getRange().equals(
        Ranges.create(33, 10, 33, 22)));
  }
}
