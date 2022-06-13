/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2022
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

import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

@CleanupContextBeforeClassAndAfterClass
class LostVariableDiagnosticTest extends AbstractDiagnosticTest<LostVariableDiagnostic> {
  LostVariableDiagnosticTest() {
    super(LostVariableDiagnostic.class);
  }

  @Test
  void test() {

    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics, true)
      .hasMessageOnRange(getMessage("Значение"), 1, 4, 12)
      .hasMessageOnRange(getMessage("МояПеременная"), 4, 4, 17)
      .hasMessageOnRange(getMessage("ТекстЗапроса"), 9, 4, 16)
      .hasMessageOnRange(getMessage("ТекстЗапроса"), 23, 4, 16)
      .hasMessageOnRange(getMessage("ТекстЗапроса"), 31, 4, 16)
      .hasMessageOnRange(getMessage("ТекстЗапроса"), 53, 7, 19)
      .hasMessageOnRange(getMessage("ТекстЗапроса"), 74, 2, 14)
      .hasSize(7);
    ;

  }
  String getMessage(String name){
    return String.format("Предыдущее значение <%s> не используется", name);
  }
}
