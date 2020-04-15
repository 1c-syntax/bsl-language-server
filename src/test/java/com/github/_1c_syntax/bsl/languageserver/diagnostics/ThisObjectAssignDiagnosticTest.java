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

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.mdclasses.metadata.additional.CompatibilityMode;
import com.github._1c_syntax.mdclasses.metadata.additional.ModuleType;
import org.eclipse.lsp4j.Diagnostic;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class ThisObjectAssignDiagnosticTest extends AbstractDiagnosticTest<ThisObjectAssignDiagnostic> {
  ThisObjectAssignDiagnosticTest() {
    super(ThisObjectAssignDiagnostic.class);
  }

  @Test
  void test832() {

    DocumentContext documentContext = setCompatibilityMode(new CompatibilityMode(3, 2));

    List<Diagnostic> diagnostics = getDiagnosticsFiltered(documentContext);

    assertThat(diagnostics).hasSize(0);

  }


  @Test
  void test833() {

    DocumentContext documentContext = setCompatibilityMode(new CompatibilityMode(3, 4));

    List<Diagnostic> diagnostics = getDiagnosticsFiltered(documentContext);
    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true)
      .hasRange(1, 4, 14);

  }

  @Test
  void test836() {

    DocumentContext documentContext = setCompatibilityMode(new CompatibilityMode(3, 14));

    List<Diagnostic> diagnostics = getDiagnosticsFiltered(documentContext);
    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true)
      .hasRange(1, 4, 14);

  }


  @NotNull
  private DocumentContext setCompatibilityMode(CompatibilityMode version) {

    var documentContext = spy(getDocumentContext());
    var serverContext = spy(documentContext.getServerContext());
    var configuration = spy(serverContext.getConfiguration());

    when(documentContext.getServerContext()).thenReturn(serverContext);
    when(documentContext.getModuleType()).thenReturn(ModuleType.CommonModule);

    when(serverContext.getConfiguration()).thenReturn(configuration);
    when(configuration.getCompatibilityMode()).thenReturn(version);

    return documentContext;
  }

  private List<Diagnostic> getDiagnosticsFiltered(DocumentContext documentContext) {
    DiagnosticSupplier diagnosticSupplier = new DiagnosticSupplier(LanguageServerConfiguration.create());

    Optional<BSLDiagnostic> first = diagnosticSupplier
      .getDiagnosticInstances(documentContext)
      .stream()
      .filter(di -> di instanceof ThisObjectAssignDiagnostic)
      .findFirst();

    List<Diagnostic> diagnostics = new ArrayList<>();
    if (first.isPresent()) {
      diagnostics = first.get().getDiagnostics(documentContext);
    }
    return diagnostics;
  }

}
