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

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.diagnostics.infrastructure.DiagnosticObjectProvider;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MethodCallUnavailableFileModeTest extends AbstractServerContextAwareTest {
  private static final Path ROOT = Path.of("src/test/resources/metadata/commonModuleCalls");
  @Autowired
  private DiagnosticObjectProvider diagnostics;

  @BeforeEach
  void workspace() {
    initServerContext(ROOT);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void diagnosticOnCannotOverrideTheProjectPolicy(boolean enabled) {
    context.getLanguageServerConfiguration().setSupportThickClientFileMode(enabled);
    var document = form("""
      #If ThinClient Then
      &AtClient
      Procedure Target()
      EndProcedure
      #EndIf
      &AtClient
      Procedure Caller()
        #If Client And Server Then
          Target();
        #EndIf
      EndProcedure
      """);
    var diagnostic = diagnostics.get(MethodCallUnavailableDiagnostic.class);
    diagnostic.configure(Map.of("checkThickClientManagedApplication", "on"));

    assertThat(diagnostic.getDiagnostics(document)).hasSize(enabled ? 1 : 0);
  }


  private DocumentContext form(String source) {
    return TestUtils.getDocumentContext(ROOT.resolve("CommonForms/Тест/Ext/Form/Module.bsl").toUri(), source, context);
  }
}
