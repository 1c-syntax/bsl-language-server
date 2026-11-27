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

import com.github._1c_syntax.bsl.languageserver.configuration.diagnostics.Mode;
import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.types.ModuleType;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.Map;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class UnusedLocalVariableModuleDiagnosticTest extends AbstractServerContextAwareTest {
  private static final String ORPHAN = """
    Var ModuleVariable;

    Procedure Check(Argument)
      Var ExplicitLocal;
      MaybeAttribute = UnknownFunction();
      Argument = 1;
    EndProcedure

    ModuleWrite = 1;
    """;

  @ParameterizedTest
  @ValueSource(strings = {"Catalogs/Test/Ext/ObjectModule.bsl", "Documents/D/ObjectModule.bsl",
    "Catalogs/Ext/ObjectModule.bsl", "InformationRegisters/R/RecordSetModule.bsl",
    "AccumulationRegisters/R/Ext/RecordSetModule.bsl",
    "Forms/F/Ext/Form/Module.bsl", "Catalogs/Test/Forms/F/Module.bsl", "CommonForms/F/Module.bsl"})
  void incompleteExportOnlyProvesExplicitLocals(String path) {
    initServerContext();
    configure(true);
    var document = TestUtils.getDocumentContext(Path.of(path).toUri(), ORPHAN, context);
    assertThat(document.getModuleType()).isEqualTo(ModuleType.UNKNOWN);
    assertThat(document.getDiagnostics()).hasSize(1);
    assertThat(document.getDiagnostics(), true).hasRange(3, 6, 19);
  }

  @ParameterizedTest
  @ValueSource(strings = {"ObjectModule.bsl", "RecordSetModule.bsl", "work/Ext/Form/Module.bsl",
    "Catalogs/Test/RecordSetModule.bsl", "InformationRegisters/R/Ext/ObjectModule.bsl"})
  void standaloneFilesKeepUnknownModuleChecks(String path) {
    initServerContext();
    configure(true);
    var document = TestUtils.getDocumentContext(Path.of(path).toUri(), """
      Procedure Check()
        Unread = 1;
      EndProcedure
      """, context);
    assertThat(document.getModuleType()).isEqualTo(ModuleType.UNKNOWN);
    assertThat(document.getDiagnostics()).hasSize(1);
    assertThat(document.getDiagnostics(), true).hasRange(1, 2, 8);
  }

  @Test
  void noContextFormNeedsNoOwnerMetadata() {
    initServerContext();
    configure(true);
    var document = TestUtils.getDocumentContext(Path.of("CommonForms/F/Module.bsl").toUri(), """
      &AtServerNoContext
      Procedure Check(Form)
        MyAttribute = Form.MyAttribute;
      EndProcedure
      """, context);
    assertThat(document.getDiagnostics()).hasSize(1);
    assertThat(document.getDiagnostics(), true).hasRange(2, 2, 13);
  }

  @ParameterizedTest
  @ValueSource(strings = {"CommonForms/F/Module.bsl", "Catalogs/C/Ext/ObjectModule.bsl",
    "InformationRegisters/R/Ext/RecordSetModule.bsl"})
  void numericLoopReadsItsCounterInTheEnclosingMethod(String path) {
    initServerContext();
    configure(true);
    var directive = path.startsWith("CommonForms/") ? "&AtServerNoContext" : "";
    var document = TestUtils.getDocumentContext(Path.of(path).toUri(), """
      %s
      Procedure Check(Items)
        Var Counter, Item;
        Assigned = 0;
        For counter = 1 To 2 Do
        EndDo;
        For Assigned = 1 To 2 Do
        EndDo;
        For Each Mixed In Items Do
        EndDo;
        For Mixed = 1 To 2 Do
        EndDo;
        For Each Item In Items Do
        EndDo;
      EndProcedure

      Procedure Other()
        Var Counter;
      EndProcedure
      """.formatted(directive), context);
    var diagnostics = document.getDiagnostics();
    assertThat(diagnostics).hasSize(2);
    assertThat(diagnostics, true).hasRange(2, 15, 19);
    assertThat(diagnostics, true).hasRange(17, 6, 13);
  }

  @Test
  void disabledRuleDoesNotReportProvenLocal() {
    initServerContext();
    configure(false);
    var document = TestUtils.getDocumentContext(Path.of("Catalogs/Test/Ext/ObjectModule.bsl").toUri(), ORPHAN, context);
    assertThat(document.getDiagnostics()).isEmpty();
  }

  private void configure(boolean enabled) {
    var options = context.getLanguageServerConfiguration().getDiagnosticsOptions();
    options.setMode(Mode.ONLY);
    options.setParameters(Map.of("UnusedLocalVariable", Either.forLeft(enabled)));
  }
}
