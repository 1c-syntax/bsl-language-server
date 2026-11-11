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
      .thenReturn(new DiagnosticIgnoranceComputer.Data(Map.of(), List.of()));
    when(documentContext.getUri()).thenReturn(URI.create("file:///test.bsl"));
  }

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
  }

  @Test
  void collectsResultsFromAllDiagnostics() {
    var firstDiagnostic = diagnostic("First", "first");
    var first = mockDiagnostic("First", true, List.of(firstDiagnostic));

    var secondDiagnostic = diagnostic("Second", "second");
    var second = mockDiagnostic("Second", true, List.of(secondDiagnostic));

    var computer = computerWith(List.of(first, second));

    var result = computer.compute(documentContext);

    assertThat(result).containsExactlyInAnyOrder(firstDiagnostic, secondDiagnostic);
  }

  @Test
  void skipsDiagnosticThatThrowsRuntimeException() {
    var expected = diagnostic("Good", "ok");
    var good = mockDiagnostic("Good", true, List.of(expected));

    var bad = mock(BSLDiagnostic.class);
    when(bad.getInfo()).thenReturn(diagnosticInfoWithCode("Bad"));
    when(bad.getDiagnostics(documentContext)).thenThrow(new RuntimeException("boom"));

    var computer = computerWith(List.of(good, bad));

    var result = computer.compute(documentContext);

    assertThat(result).containsExactly(expected);
  }

  @Test
  void wrapsNonRuntimeFailureFromDiagnostic() {
    var bad = mock(BSLDiagnostic.class);
    when(bad.getInfo()).thenReturn(diagnosticInfoWithCode("Bad"));
    when(bad.getDiagnostics(documentContext)).thenThrow(new AssertionError("fatal"));

    var computer = computerWith(List.of(bad));

    assertThatThrownBy(() -> computer.compute(documentContext))
      .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void keepsNonSuppressibleDiagnosticIgnoredByComment() {
    var expected = diagnostic("NonSuppressible", "kept");
    var nonSuppressible = mockDiagnostic("NonSuppressible", false, List.of(expected));
    when(documentContext.getDiagnosticIgnorance()).thenReturn(ignoranceOf("NonSuppressible"));

    var result = computerWith(List.of(nonSuppressible)).compute(documentContext);

    assertThat(result).containsExactly(expected);
  }

  @Test
  void filtersSuppressibleDiagnosticIgnoredByComment() {
    var suppressed = diagnostic("Suppressible", "hidden");
    var suppressible = mockDiagnostic("Suppressible", true, List.of(suppressed));
    when(documentContext.getDiagnosticIgnorance()).thenReturn(ignoranceOf("Suppressible"));

    var result = computerWith(List.of(suppressible)).compute(documentContext);

    assertThat(result).isEmpty();
  }

  private DefaultDiagnosticComputer computerWith(List<BSLDiagnostic> diagnostics) {
    return new DefaultDiagnosticComputer(configuration, executor) {
      @Override
      protected List<BSLDiagnostic> diagnostics(DocumentContext documentContext) {
        return diagnostics;
      }
    };
  }

  private BSLDiagnostic mockDiagnostic(String code, boolean suppressible, List<Diagnostic> diagnostics) {
    var info = diagnosticInfoWithCode(code);
    when(info.isSuppressible()).thenReturn(suppressible);

    var diagnostic = mock(BSLDiagnostic.class);
    when(diagnostic.getInfo()).thenReturn(info);
    when(diagnostic.getDiagnostics(documentContext)).thenReturn(diagnostics);
    return diagnostic;
  }

  private static DiagnosticIgnoranceComputer.Data ignoranceOf(String code) {
    var range = org.apache.commons.lang3.Range.of(0, 10);
    return new DiagnosticIgnoranceComputer.Data(Map.of(new DiagnosticCode(code), List.of(range)), List.of());
  }

  private static Diagnostic diagnostic(String code, String message) {
    var diagnostic = new Diagnostic(new Range(new Position(0, 0), new Position(0, 1)), message);
    diagnostic.setCode(new DiagnosticCode(code));
    return diagnostic;
  }

  private static DiagnosticInfo diagnosticInfoWithCode(String code) {
    var info = mock(DiagnosticInfo.class);
    when(info.getCode()).thenReturn(new DiagnosticCode(code));
    when(info.isSuppressible()).thenReturn(true);
    return info;
  }
}
