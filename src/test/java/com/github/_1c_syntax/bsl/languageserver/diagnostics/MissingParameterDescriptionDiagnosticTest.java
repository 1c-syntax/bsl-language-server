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

import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class MissingParameterDescriptionDiagnosticTest extends AbstractDiagnosticTest<MissingParameterDescriptionDiagnostic> {
  MissingParameterDescriptionDiagnosticTest() {
    super(MissingParameterDescriptionDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(12);
    assertThat(diagnostics, true)
      .hasMessageOnRange("Необходимо добавить описание всех параметров метода",
        7, 8, 15)
      .hasMessageOnRange("Необходимо удалить описания параметров \"Параметр1, Параметр2\", отсутствующих в сигнатуре метода",
        14, 8, 15)
      .hasMessageOnRange("Необходимо удалить описания параметров \"Параметр2\", отсутствующих в сигнатуре метода",
        21, 8, 15)
      .hasMessageOnRange("Необходимо удалить описания параметров \"Параметр1\", отсутствующих в сигнатуре метода",
        28, 8, 15)
      .hasMessageOnRange("Необходимо добавить описание параметра \"Параметр3\"",
        28, 27, 36)
      .hasMessageOnRange("Необходимо исправить порядок описаний параметров",
        35, 8, 15)
      .hasMessageOnRange("Необходимо добавить описание параметра \"Параметр2\"",
        42, 27, 36)
      .hasMessageOnRange("Необходимо удалить описания параметров \"Параметр2\", отсутствующих в сигнатуре метода",
        50, 8, 15)
      .hasMessageOnRange("Необходимо добавить описание параметра \"Параметр1\"",
        58, 16, 25)
      .hasMessageOnRange("Необходимо добавить описание параметра \"Параметр2\"",
        58, 27, 36)
      .hasMessageOnRange("Необходимо удалить описания параметров \"Параметр3, Параметр4\", отсутствующих в сигнатуре метода",
        58, 8, 15)
      .hasMessageOnRange("Необходимо удалить описания параметров \"Параметр2, Параметр3, Параметр5\", отсутствующих в сигнатуре метода",
        68, 8, 15)
    ;
  }
}
