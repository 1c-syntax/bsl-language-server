/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2024
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

import com.github._1c_syntax.bsl.mdo.Form;
import com.github._1c_syntax.bsl.mdo.support.FormType;
import com.github._1c_syntax.bsl.types.ModuleType;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class CompilationDirectiveLostDiagnosticTest extends AbstractDiagnosticTest<CompilationDirectiveLostDiagnostic> {
  CompilationDirectiveLostDiagnosticTest() {
    super(CompilationDirectiveLostDiagnostic.class);
  }

  @Test
  void test() {

    var documentContext = getDocumentContext();
    List<Diagnostic> diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true)
      .hasRange(9, 8, 9, 16);

  }

  @Test
  void testOriginalFormModule() {
    final var PATH_TO_METADATA = "src/test/resources/metadata/designer";
    initServerContext(Absolute.path(PATH_TO_METADATA));
    var form = spy((Form) context.getConfiguration().getPlainChildren().stream()
      .filter(mdo -> mdo.getName().equalsIgnoreCase("ФормаЭлемента"))
      .findFirst()
      .get());

    var documentContext = spy(getDocumentContext());
    when(documentContext.getModuleType()).thenReturn(ModuleType.FormModule);
    when(form.getFormType()).thenReturn(FormType.ORDINARY);
    when(documentContext.getMdObject()).thenReturn(Optional.of(form));

    List<Diagnostic> diagnostics = getDiagnostics(documentContext);

    assertThat(diagnostics).isEmpty();
  }
}
