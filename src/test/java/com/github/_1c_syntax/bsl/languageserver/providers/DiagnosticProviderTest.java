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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.client.ClientCapabilitiesHolder;
import com.github._1c_syntax.bsl.languageserver.client.LanguageClientHolder;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.configuration.events.LanguageServerConfigurationChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextPopulatedEvent;
import com.github._1c_syntax.bsl.languageserver.events.LanguageServerInitializedEvent;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticCapabilities;
import org.eclipse.lsp4j.DiagnosticWorkspaceCapabilities;
import org.eclipse.lsp4j.DocumentDiagnosticReport;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.RelatedFullDocumentDiagnosticReport;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.WorkspaceClientCapabilities;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class DiagnosticProviderTest {

  @Autowired
  private DiagnosticProvider diagnosticProvider;

  @Autowired
  private LanguageClientHolder languageClientHolder;

  @Autowired
  private ClientCapabilitiesHolder clientCapabilitiesHolder;

  @AfterEach
  void tearDown() {
    languageClientHolder.connect(null);
    clientCapabilitiesHolder.setCapabilities(null);
  }

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
    Diagnostic firstDiagnostic = fullReport.getItems().getFirst();
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
    
    var event = new LanguageServerInitializedEvent(languageServer, params);

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
    
    var event = new LanguageServerInitializedEvent(languageServer, params);

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

  @Test
  void testServerContextPopulatedRequestsRefreshWhenClientSupportsRefresh() {
    // given
    initializeRefreshSupport(true);

    var languageClient = mock(LanguageClient.class);
    languageClientHolder.connect(languageClient);

    // when
    diagnosticProvider.handleServerContextPopulatedEvent(
      new ServerContextPopulatedEvent(mock(ServerContext.class))
    );

    // then
    verify(languageClient, times(1)).refreshDiagnostics();
  }

  @Test
  void testServerContextPopulatedClearsCachedDiagnosticsForPullClient() {
    // given: pull-клиент (VSCode) объявил refreshSupport — мы не должны полагаться на то,
    // что он сам вычищает наш кэш. Иначе после workspace/diagnostic/refresh клиент перезапросит
    // textDocument/diagnostic, а получит закэшированную диагностику, посчитанную ещё до
    // наполнения контекста.
    initializeRefreshSupport(true);

    var languageClient = mock(LanguageClient.class);
    languageClientHolder.connect(languageClient);

    var openedA = mock(DocumentContext.class);
    var openedB = mock(DocumentContext.class);
    var serverContext = mock(ServerContext.class);
    when(serverContext.getOpenedDocuments()).thenReturn(java.util.Set.of(openedA, openedB));

    // when
    diagnosticProvider.handleServerContextPopulatedEvent(new ServerContextPopulatedEvent(serverContext));

    // then
    verify(openedA, times(1)).clearDiagnostics();
    verify(openedB, times(1)).clearDiagnostics();
    verify(languageClient, times(1)).refreshDiagnostics();
  }

  @Test
  void testServerContextPopulatedDoesNotRequestRefreshWhenClientDoesNotSupportRefresh() {
    // given
    initializeRefreshSupport(false);

    var languageClient = mock(LanguageClient.class);
    languageClientHolder.connect(languageClient);

    // when
    diagnosticProvider.handleServerContextPopulatedEvent(
      new ServerContextPopulatedEvent(mock(ServerContext.class))
    );

    // then
    verify(languageClient, never()).refreshDiagnostics();
  }

  @Test
  void testSupportsPullDiagnosticsReturnsTrueWhenClientDeclaresDiagnosticCapability() {
    // given
    var capabilities = new ClientCapabilities();
    var textDocument = new TextDocumentClientCapabilities();
    textDocument.setDiagnostic(new DiagnosticCapabilities());
    capabilities.setTextDocument(textDocument);
    clientCapabilitiesHolder.setCapabilities(capabilities);

    // when
    var supportsPullDiagnostics = diagnosticProvider.supportsPullDiagnostics();

    // then
    assertThat(supportsPullDiagnostics).isTrue();
  }

  @Test
  void testSupportsPullDiagnosticsReturnsFalseWhenClientDoesNotDeclareDiagnosticCapability() {
    // given
    clientCapabilitiesHolder.setCapabilities(new ClientCapabilities());

    // when
    var supportsPullDiagnostics = diagnosticProvider.supportsPullDiagnostics();

    // then
    assertThat(supportsPullDiagnostics).isFalse();
  }

  private void initializeRefreshSupport(boolean refreshSupport) {
    var capabilities = new ClientCapabilities();
    var workspace = new WorkspaceClientCapabilities();
    var diagnostics = new DiagnosticWorkspaceCapabilities();
    diagnostics.setRefreshSupport(refreshSupport);
    workspace.setDiagnostics(diagnostics);
    capabilities.setWorkspace(workspace);
    clientCapabilitiesHolder.setCapabilities(capabilities);

    var languageServer = mock(LanguageServer.class);
    var params = new InitializeParams();
    params.setCapabilities(capabilities);
    diagnosticProvider.handleInitializeEvent(
      new LanguageServerInitializedEvent(languageServer, params)
    );
  }
}
