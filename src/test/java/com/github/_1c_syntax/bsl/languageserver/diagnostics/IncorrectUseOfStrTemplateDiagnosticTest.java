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

import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class IncorrectUseOfStrTemplateDiagnosticTest extends AbstractDiagnosticTest<IncorrectUseOfStrTemplateDiagnostic> {
  IncorrectUseOfStrTemplateDiagnosticTest() {
    super(IncorrectUseOfStrTemplateDiagnostic.class);
  }

  @Test
  void overwrittenConstantTemplateIsNotChecked() {
    // given — константа шаблона перезаписана значением, которое статически неизвестно
    var documentContext = TestUtils.getDocumentContext("""
      Процедура Тест()
        Шаблон = "123";
        Шаблон = ПолучитьШаблон();
        Н = СтрШаблон(Шаблон, Наименование);
      КонецПроцедуры
      """);

    // when
    var diagnostics = getDiagnostics(documentContext);

    // then — проверять нечего: шаблон берётся из последнего присваивания, а не из старой константы
    assertThat(diagnostics).isEmpty();
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics, true)
      .hasRange(2, 8, 45)
      .hasRange(4, 8, 49)
      .hasRange(6, 8, 51)
      .hasRange(8, 8, 50)
      .hasRange(10, 8, 71)
      .hasRange(13, 8, 66)
      .hasRange(16, 8, 44)
      .hasRange(20, 8, 45)
      .hasRange(24, 8, 45)
      .hasRange(27, 10, 116)
      .hasRange(29, 8, 38)
      .hasRange(58, 8, 58)
    ;
    assertThat(diagnostics).hasSize(12);

  }
}
