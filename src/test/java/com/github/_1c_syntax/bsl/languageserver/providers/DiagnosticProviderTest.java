/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.LanguageClientHolder;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.configuration.events.LanguageServerConfigurationChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.events.LanguageServerInitializeRequestReceivedEvent;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticWorkspaceCapabilities;
import org.eclipse.lsp4j.DocumentDiagnosticReport;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.RelatedFullDocumentDiagnosticReport;
import org.eclipse.lsp4j.WorkspaceClientCapabilities;
import org.eclipse.lsp4j.services.LanguageServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.mockito.Mockito.mock;

@SpringBootTest
class DiagnosticProviderTest {

  @Autowired
  private DiagnosticProvider diagnosticProvider;

  @Autowired
  private LanguageClientHolder languageClientHolder;

  @Test
  void testComputeAndPublishDiagnostics() {
    // given
    final DocumentContext documentContext
      = TestUtils.getDocumentContextFromFile("./src/test/resources/providers/diagnosticProvider.bsl");

    // when-then
    assertThatCode(() -> diagnosticProvider.computeAndPublishDiagnostics(documentContext))
      .doesNotThrowAnyException();
  }

  @Test
  void testPublishEmptyDiagnosticList() {
    // given
    final DocumentContext documentContext
      = TestUtils.getDocumentContextFromFile("./src/test/resources/providers/diagnosticProvider.bsl");

    // when-then
    assertThatCode(() -> diagnosticProvider.publishEmptyDiagnosticList(documentContext))
      .doesNotThrowAnyException();
  }

  @Test
  void testGetDiagnostic() {
    // given
    final DocumentContext documentContext
      = TestUtils.getDocumentContextFromFile("./src/test/resources/providers/diagnosticProvider.bsl");

    // when
    final DocumentDiagnosticReport report = diagnosticProvider.getDiagnostic(documentContext);

    // then
    assertThat(report).isNotNull();
    assertThat(report.getLeft()).isNotNull();
    assertThat(report.getLeft()).isInstanceOf(RelatedFullDocumentDiagnosticReport.class);
    
    RelatedFullDocumentDiagnosticReport fullReport = report.getLeft();
    assertThat(fullReport.getItems()).isNotNull();
    assertThat(fullReport.getItems()).isNotEmpty();
  }

  @Test
  void testGetDiagnosticWithNoDiagnostics() {
    // given
    final DocumentContext documentContext
      = TestUtils.getDocumentContext("");

    // when
    final DocumentDiagnosticReport report = diagnosticProvider.getDiagnostic(documentContext);

    // then
    assertThat(report).isNotNull();
    assertThat(report.getLeft()).isNotNull();
    assertThat(report.getLeft()).isInstanceOf(RelatedFullDocumentDiagnosticReport.class);
    
    RelatedFullDocumentDiagnosticReport fullReport = report.getLeft();
    assertThat(fullReport.getItems()).isNotNull();
    assertThat(fullReport.getItems()).isEmpty();
  }

  @Test
  void testGetDiagnosticReportStructure() {
    // given
    final DocumentContext documentContext
      = TestUtils.getDocumentContextFromFile("./src/test/resources/providers/diagnosticProvider.bsl");

    // when
    final DocumentDiagnosticReport report = diagnosticProvider.getDiagnostic(documentContext);

    // then
    RelatedFullDocumentDiagnosticReport fullReport = report.getLeft();
    assertThat(fullReport.getKind()).isEqualTo("full");
    assertThat(fullReport.getItems()).hasSizeGreaterThan(0);
    
    // Verify diagnostics have required fields
    Diagnostic firstDiagnostic = fullReport.getItems().get(0);
    assertThat(firstDiagnostic.getRange()).isNotNull();
    assertThat(firstDiagnostic.getMessage()).isNotNull();
    assertThat(firstDiagnostic.getSource()).isEqualTo(DiagnosticProvider.SOURCE);
  }

  @Test
  void testHandleInitializeEvent() {
    // given
    var languageServer = mock(LanguageServer.class);
    var params = new InitializeParams();
    var capabilities = new ClientCapabilities();
    var workspace = new WorkspaceClientCapabilities();
    var diagnostics = new DiagnosticWorkspaceCapabilities();
    diagnostics.setRefreshSupport(true);
    workspace.setDiagnostics(diagnostics);
    capabilities.setWorkspace(workspace);
    params.setCapabilities(capabilities);
    
    var event = new LanguageServerInitializeRequestReceivedEvent(languageServer, params);

    // when-then
    assertThatCode(() -> diagnosticProvider.handleInitializeEvent(event))
      .doesNotThrowAnyException();
  }

  @Test
  void testHandleInitializeEventWithoutDiagnosticsCapabilities() {
    // given
    var languageServer = mock(LanguageServer.class);
    var params = new InitializeParams();
    var capabilities = new ClientCapabilities();
    var workspace = new WorkspaceClientCapabilities();
    capabilities.setWorkspace(workspace);
    params.setCapabilities(capabilities);
    
    var event = new LanguageServerInitializeRequestReceivedEvent(languageServer, params);

    // when-then
    assertThatCode(() -> diagnosticProvider.handleInitializeEvent(event))
      .doesNotThrowAnyException();
  }

  @Test
  void testHandleConfigurationChangedEvent() {
    // given
    var configuration = mock(LanguageServerConfiguration.class);
    var event = new LanguageServerConfigurationChangedEvent(configuration);

    // when-then
    assertThatCode(() -> diagnosticProvider.handleConfigurationChangedEvent(event))
      .doesNotThrowAnyException();
  }
}
