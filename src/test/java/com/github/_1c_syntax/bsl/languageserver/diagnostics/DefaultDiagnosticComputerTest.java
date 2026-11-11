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

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.configuration.diagnostics.DiagnosticsOptions;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.computer.DiagnosticIgnoranceComputer;
import com.github._1c_syntax.bsl.languageserver.diagnostics.info.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticCode;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultDiagnosticComputerTest {

  private ExecutorService executor;
  private LanguageServerConfiguration configuration;
  private DocumentContext documentContext;

  @BeforeEach
  void setUp() {
    executor = Executors.newFixedThreadPool(2);

    configuration = mock(LanguageServerConfiguration.class);
    when(configuration.getDiagnosticsOptions()).thenReturn(new DiagnosticsOptions());

    documentContext = mock(DocumentContext.class);
    when(documentContext.getDiagnosticIgnorance())
      .thenReturn(new DiagnosticIgnoranceComputer.Data(Map.of()));
    when(documentContext.getUri()).thenReturn(URI.create("file:///test.bsl"));
  }

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
  }

  @Test
  void collectsResultsFromAllDiagnostics() {
    var first = mock(BSLDiagnostic.class);
    var firstDiagnostic = diagnostic("first");
    when(first.getDiagnostics(documentContext)).thenReturn(List.of(firstDiagnostic));

    var second = mock(BSLDiagnostic.class);
    var secondDiagnostic = diagnostic("second");
    when(second.getDiagnostics(documentContext)).thenReturn(List.of(secondDiagnostic));

    var computer = computerWith(List.of(first, second));

    var result = computer.compute(documentContext);

    assertThat(result).containsExactlyInAnyOrder(firstDiagnostic, secondDiagnostic);
  }

  @Test
  void skipsDiagnosticThatThrowsRuntimeException() {
    var good = mock(BSLDiagnostic.class);
    var expected = diagnostic("ok");
    when(good.getDiagnostics(documentContext)).thenReturn(List.of(expected));

    var bad = mock(BSLDiagnostic.class);
    when(bad.getDiagnostics(documentContext)).thenThrow(new RuntimeException("boom"));
    var badInfo = diagnosticInfoWithCode("Bad");
    when(bad.getInfo()).thenReturn(badInfo);

    var computer = computerWith(List.of(good, bad));

    var result = computer.compute(documentContext);

    assertThat(result).containsExactly(expected);
  }

  @Test
  void wrapsNonRuntimeFailureFromDiagnostic() {
    var bad = mock(BSLDiagnostic.class);
    when(bad.getDiagnostics(documentContext)).thenThrow(new AssertionError("fatal"));

    var computer = computerWith(List.of(bad));

    assertThatThrownBy(() -> computer.compute(documentContext))
      .isInstanceOf(IllegalStateException.class);
  }

  private DefaultDiagnosticComputer computerWith(List<BSLDiagnostic> diagnostics) {
    return new DefaultDiagnosticComputer(configuration, executor) {
      @Override
      protected List<BSLDiagnostic> diagnostics(DocumentContext documentContext) {
        return diagnostics;
      }
    };
  }

  private static Diagnostic diagnostic(String message) {
    return new Diagnostic(new Range(new Position(0, 0), new Position(0, 1)), message);
  }

  private static DiagnosticInfo diagnosticInfoWithCode(String code) {
    var info = mock(DiagnosticInfo.class);
    when(info.getCode()).thenReturn(new DiagnosticCode(code));
    return info;
  }
}
