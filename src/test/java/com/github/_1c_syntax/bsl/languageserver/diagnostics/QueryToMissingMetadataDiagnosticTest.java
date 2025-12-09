/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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

import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

@CleanupContextBeforeClassAndAfterEachTestMethod
class QueryToMissingMetadataDiagnosticTest extends AbstractDiagnosticTest<QueryToMissingMetadataDiagnostic> {
  private static final String PATH_TO_METADATA = "src/test/resources/metadata/designer";

  QueryToMissingMetadataDiagnosticTest() {
    super(QueryToMissingMetadataDiagnostic.class);
  }

  @Test
  void test() {
    initServerContext(Absolute.path(PATH_TO_METADATA));

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics, true)
      .hasMessageOnRange("Исправьте обращение к несуществующему метаданному \"РегистрСведений.УстаревшееИмяРегистра\" в запросе",
        4, 18, 55)
      .hasMessageOnRange("Исправьте обращение к несуществующему метаданному \"РегистрСведений.УдалитьИмяРегистра\" в запросе",
        19, 40, 74)
      .hasMessageOnRange("Исправьте обращение к несуществующему метаданному \"ВнешнийИсточникДанных.ВнешнийИсточникДанных2\" в запросе",
        50, 18, 62)
      .hasMessageOnRange("Исправьте обращение к несуществующему метаданному \"ВнешнийИсточникДанных.ВнешнийИсточникДанных2\" в запросе",
        66, 18, 62)
      .hasMessageOnRange("Исправьте обращение к несуществующему метаданному \"Куб2\" в запросе",
        80, 51, 55)
      .hasMessageOnRange("Исправьте обращение к несуществующему метаданному \"ТаблицаИзмерения2\" в запросе",
        94, 73, 90)

      .hasSize(6);

  }

  @Test
  void testSingleFile() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics, true)
      .hasSize(0);

  }
}
