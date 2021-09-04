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

import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.mdclasses.mdo.support.ModuleType;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class MissingHttpServiceHadlerDiagnosticTest extends AbstractDiagnosticTest<MissingHttpServiceHadlerDiagnostic> {
  MissingHttpServiceHadlerDiagnosticTest() {
    super(MissingHttpServiceHadlerDiagnostic.class);
  }
  private static final String PATH_TO_METADATA = "src/test/resources/metadata";

  @Test
  void test() {

    initServerContext(Absolute.path(PATH_TO_METADATA));
    var documentContext = spy(getDocumentContext());
    when(documentContext.getModuleType()).thenReturn(ModuleType.SessionModule);
    List<Diagnostic> diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics)
      .allMatch(
        diagnostic -> diagnostic.getRange().equals(Ranges.create(0, 0, 7)))
      .anyMatch(diagnostic -> diagnostic.getMessage()
        .equals("Создайте функцию-обработчик \"URLTemplate1GET\" или исправьте некорректный обработчик http-сервиса \"HTTPService.HTTPСервис1.URLTemplate.URLTemplate1.Method.GET\""))
      .anyMatch(diagnostic -> diagnostic.getMessage()
        .equals("Задайте обработчик http-сервиса \"HTTPService.HTTPСервис1.URLTemplate.URLTemplate_НеверныеОбработчики.Method.GET\""))
      .anyMatch(diagnostic -> diagnostic.getMessage()
        .equals("Задайте всего один параметр у обработчика \"URLTemplate_НеверныеОбработчики_POST\" для http-сервиса \"HTTPService.HTTPСервис1.URLTemplate.URLTemplate_НеверныеОбработчики.Method.POST\""))
      .hasSize(3)

    ;
  }
}
