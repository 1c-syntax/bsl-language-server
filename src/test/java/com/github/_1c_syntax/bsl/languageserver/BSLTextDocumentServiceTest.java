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
package com.github._1c_syntax.bsl.languageserver;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextContentChangedEvent;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.bsl.languageserver.jsonrpc.DiagnosticParams;
import com.github._1c_syntax.bsl.languageserver.providers.DefinitionProvider;
import com.github._1c_syntax.bsl.languageserver.providers.DiagnosticProvider;
import com.github._1c_syntax.bsl.languageserver.providers.HoverProvider;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.utils.Absolute;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.CallHierarchyIncomingCallsParams;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.eclipse.lsp4j.CallHierarchyOutgoingCallsParams;
import org.eclipse.lsp4j.CallHierarchyPrepareParams;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.ColorPresentationParams;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.DiagnosticCapabilities;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentColorParams;
import org.eclipse.lsp4j.DocumentDiagnosticParams;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentHighlightParams;
import org.eclipse.lsp4j.DocumentLinkParams;
import org.eclipse.lsp4j.DocumentRangeFormattingParams;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.FoldingRangeRequestParams;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.ImplementationParams;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.PrepareRenameParams;
import org.eclipse.lsp4j.ReferenceContext;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.SelectionRangeParams;
import org.eclipse.lsp4j.SemanticTokensDeltaParams;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.SemanticTokensRangeParams;
import org.eclipse.lsp4j.SignatureHelpParams;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TypeHierarchyItem;
import org.eclipse.lsp4j.TypeHierarchyPrepareParams;
import org.eclipse.lsp4j.TypeHierarchySubtypesParams;
import org.eclipse.lsp4j.TypeHierarchySupertypesParams;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.SmartApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
// didOpen/didChange-тесты оставляют документ в индексе провайдера; *UnknownFile-тесты
// предполагают, что фикстура НЕ открыта. Per-method liteCleanup сбрасывает workspace-state
// между методами, изолируя интра-классовые мутации.
@CleanupContextBeforeClassAndAfterEachTestMethod
class BSLTextDocumentServiceTest {

  @Autowired
  private BSLTextDocumentService textDocumentService;
  @Autowired
  private ServerContextProvider serverContextProvider;
  @Autowired
  private ConfigurableApplicationContext applicationContext;
  @MockitoSpyBean
  private DiagnosticProvider diagnosticProvider;
  @MockitoSpyBean
  private ClientCapabilitiesHolder clientCapabilitiesHolder;
  @MockitoSpyBean
  private HoverProvider hoverProvider;
  @MockitoSpyBean
  private DefinitionProvider definitionProvider;

  @BeforeEach
  void setUp() {
    // Register workspace for test resources
    var testResourcesPath = new File("./src/test/resources").getAbsoluteFile();
    var workspaceFolder = new WorkspaceFolder(testResourcesPath.toURI().toString(), "test-workspace");
    serverContextProvider.addWorkspace(workspaceFolder);
  }

  /**
   * Воспроизводит ScopeNotActiveException из Sentry:
   * didOpen вызывает openDocument → rebuildDocument синхронно на LSP-треде без workspace-контекста,
   * из-за чего workspace-scoped proxy beans не резолвятся в @EventListener при DocumentContextContentChangedEvent.
   */
  @Test
  void didOpen_setsWorkspaceContextForEventListeners() throws IOException {
    var capturedWorkspaceUri = new AtomicReference<URI>();
    var listener = new SmartApplicationListener() {
      @Override
      public boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
        return DocumentContextContentChangedEvent.class.isAssignableFrom(eventType);
      }

      @Override
      public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
      }

      @Override
      public void onApplicationEvent(ApplicationEvent event) {
        capturedWorkspaceUri.compareAndSet(null, WorkspaceContextHolder.get());
      }
    };
    applicationContext.addApplicationListener(listener);

    try {
      textDocumentService.didOpen(new DidOpenTextDocumentParams(getTextDocumentItem()));

      var expectedWorkspaceUri = Absolute.uri(new File("./src/test/resources").getAbsoluteFile().toURI());
      assertThat(capturedWorkspaceUri.get())
        .as("Workspace context must be set when DocumentContextContentChangedEvent fires during didOpen "
          + "(otherwise workspace-scoped beans like AnnotationRepository cannot be resolved)")
        .isNotNull()
        .isEqualTo(expectedWorkspaceUri);
    } finally {
      applicationContext.removeApplicationListener(listener);
    }
  }

  @Test
  void didOpen() throws IOException {
    doOpen();
  }

  @Test
  void didChange() throws IOException {

    final File testFile = getTestFile();

    DidChangeTextDocumentParams params = new DidChangeTextDocumentParams();

    params.setTextDocument(new VersionedTextDocumentIdentifier(testFile.toURI().toString(), 1));

    String fileContent = FileUtils.readFileToString(testFile, StandardCharsets.UTF_8);
    TextDocumentContentChangeEvent changeEvent = new TextDocumentContentChangeEvent(fileContent);

    List<TextDocumentContentChangeEvent> contentChanges = new ArrayList<>();
    contentChanges.add(changeEvent);
    params.setContentChanges(contentChanges);

    textDocumentService.didChange(params);
  }

  @Test
  void didChangeIncremental() throws IOException {
    // given
    var textDocumentItem = getTextDocumentItem();
    var didOpenParams = new DidOpenTextDocumentParams(textDocumentItem);
    textDocumentService.didOpen(didOpenParams);

    var maybeDocument = serverContextProvider.getDocumentUnsafe(textDocumentItem.getUri());
    assertThat(maybeDocument).isPresent();
    var documentContext = maybeDocument.get();

    // when - incremental change: insert text at position
    var params = new DidChangeTextDocumentParams();
    var uriString = textDocumentItem.getUri();
    params.setTextDocument(new VersionedTextDocumentIdentifier(uriString, 2));

    var range = Ranges.create(0, 0, 0, 0);
    var changeEvent = new TextDocumentContentChangeEvent(range, "// Комментарий\n");
    List<TextDocumentContentChangeEvent> contentChanges = new ArrayList<>();
    contentChanges.add(changeEvent);
    params.setContentChanges(contentChanges);

    // then - should not throw exception
    textDocumentService.didChange(params);

    await().atMost(Duration.ofSeconds(2))
      .untilAsserted(() -> assertThat(documentContext.getContent()).startsWith("// Комментарий"));
  }

  @Test
  void didChangeIncrementalMultipleChanges() throws IOException {
    // given
    var textDocumentItem = getTextDocumentItem();
    var didOpenParams = new DidOpenTextDocumentParams(textDocumentItem);
    textDocumentService.didOpen(didOpenParams);

    var maybeDocument = serverContextProvider.getDocumentUnsafe(textDocumentItem.getUri());
    assertThat(maybeDocument).isPresent();
    var documentContext = maybeDocument.get();

    // when - multiple incremental changes
    var params = new DidChangeTextDocumentParams();
    var uriString = textDocumentItem.getUri();
    params.setTextDocument(new VersionedTextDocumentIdentifier(uriString, 2));

    List<TextDocumentContentChangeEvent> contentChanges = new ArrayList<>();
    
    // First change: insert at beginning
    var range1 = Ranges.create(0, 0, 0, 0);
    contentChanges.add(new TextDocumentContentChangeEvent(range1, "// Comment 1\n"));
    
    // Second change: replace some text
    var range2 = Ranges.create(1, 0, 1, 10);
    contentChanges.add(new TextDocumentContentChangeEvent(range2, "Replaced"));

    params.setContentChanges(contentChanges);

    // then - should not throw exception
    textDocumentService.didChange(params);

    await().atMost(Duration.ofSeconds(2))
      .untilAsserted(() -> assertThat(documentContext.getContent()).contains("Replaced"));
  }

  @Test
  void didChangeIncrementalDelete() throws IOException {
    // given
    var textDocumentItem = getTextDocumentItem();
    var didOpenParams = new DidOpenTextDocumentParams(textDocumentItem);
    textDocumentService.didOpen(didOpenParams);

    var maybeDocument = serverContextProvider.getDocumentUnsafe(textDocumentItem.getUri());
    assertThat(maybeDocument).isPresent();
    var documentContext = maybeDocument.get();

    // when - incremental change: delete text
    var params = new DidChangeTextDocumentParams();
    var uriString = textDocumentItem.getUri();
    params.setTextDocument(new VersionedTextDocumentIdentifier(uriString, 2));

    var range = Ranges.create(0, 0, 0, 5);
    var changeEvent = new TextDocumentContentChangeEvent(range, "");
    List<TextDocumentContentChangeEvent> contentChanges = new ArrayList<>();
    contentChanges.add(changeEvent);
    params.setContentChanges(contentChanges);

    // then - should not throw exception
    textDocumentService.didChange(params);

    await().atMost(Duration.ofSeconds(2))
      .untilAsserted(() -> assertThat(documentContext.getContent()).doesNotStartWith(textDocumentItem.getText().substring(0, 5)));
  }

  @Test
  void didClose() {
    DidCloseTextDocumentParams params = new DidCloseTextDocumentParams();
    params.setTextDocument(getTextDocumentIdentifier());
    textDocumentService.didClose(params);
  }

  @Test
  void didCloseWithPendingChanges() throws IOException {
    // given - open a document and make changes
    var textDocumentItem = getTextDocumentItem();
    var didOpenParams = new DidOpenTextDocumentParams(textDocumentItem);
    textDocumentService.didOpen(didOpenParams);

    var maybeDocument = serverContextProvider.getDocumentUnsafe(textDocumentItem.getUri());
    assertThat(maybeDocument).isPresent();
    var documentContext = maybeDocument.get();
    var maybeContext = serverContextProvider.getServerContextUnsafe(Absolute.uri(textDocumentItem.getUri()));
    assertThat(maybeContext).isPresent();
    assertThat(maybeContext.get().isDocumentOpened(documentContext)).isTrue();

    // when - submit multiple changes rapidly and then close immediately
    var params = new DidChangeTextDocumentParams();
    var uri = textDocumentItem.getUri();
    
    for (int i = 0; i < 5; i++) {
      params.setTextDocument(new VersionedTextDocumentIdentifier(uri, 2 + i));
      var range = Ranges.create(0, 0, 0, 0);
      var changeEvent = new TextDocumentContentChangeEvent(range, "// Change " + i + "\n");
      List<TextDocumentContentChangeEvent> contentChanges = new ArrayList<>();
      contentChanges.add(changeEvent);
      params.setContentChanges(contentChanges);
      textDocumentService.didChange(params);
    }

    // then - close should wait for pending changes to complete
    var closeParams = new DidCloseTextDocumentParams();
    closeParams.setTextDocument(new TextDocumentIdentifier(uri));
    textDocumentService.didClose(closeParams);

    // verify the document is closed
    var maybeContext2 = serverContextProvider.getServerContextUnsafe(Absolute.uri(uri));
    assertThat(maybeContext2).isPresent();
    assertThat(maybeContext2.get().isDocumentOpened(documentContext)).isFalse();
  }

  @Test
  void didCloseDuringActiveChange() throws IOException {
    // given - open a document
    var textDocumentItem = getTextDocumentItem();
    var didOpenParams = new DidOpenTextDocumentParams(textDocumentItem);
    textDocumentService.didOpen(didOpenParams);

    var maybeDocument = serverContextProvider.getDocumentUnsafe(textDocumentItem.getUri());
    assertThat(maybeDocument).isPresent();
    var documentContext = maybeDocument.get();
    var maybeContext = serverContextProvider.getServerContextUnsafe(Absolute.uri(textDocumentItem.getUri()));
    assertThat(maybeContext).isPresent();
    assertThat(maybeContext.get().isDocumentOpened(documentContext)).isTrue();

    // when - submit a change
    var params = new DidChangeTextDocumentParams();
    var uri = textDocumentItem.getUri();
    params.setTextDocument(new VersionedTextDocumentIdentifier(uri, 2));
    var range = Ranges.create(0, 0, 0, 0);
    var changeEvent = new TextDocumentContentChangeEvent(range, "// New content\n");
    List<TextDocumentContentChangeEvent> contentChanges = new ArrayList<>();
    contentChanges.add(changeEvent);
    params.setContentChanges(contentChanges);
    textDocumentService.didChange(params);

    // then - close immediately while change may still be processing
    var closeParams = new DidCloseTextDocumentParams();
    closeParams.setTextDocument(new TextDocumentIdentifier(uri));
    textDocumentService.didClose(closeParams);

    // verify the document is closed
    var maybeContext2 = serverContextProvider.getServerContextUnsafe(Absolute.uri(uri));
    assertThat(maybeContext2).isPresent();
    assertThat(maybeContext2.get().isDocumentOpened(documentContext)).isFalse();
  }

  @Test
  void didCloseAwaitTerminationCompletes() throws IOException {
    // given - open a document
    var textDocumentItem = getTextDocumentItem();
    var didOpenParams = new DidOpenTextDocumentParams(textDocumentItem);
    textDocumentService.didOpen(didOpenParams);

    var uri = textDocumentItem.getUri();
    var maybeDocument = serverContextProvider.getDocumentUnsafe(uri);
    assertThat(maybeDocument).isPresent();
    var documentContext = maybeDocument.get();
    var maybeContext = serverContextProvider.getServerContextUnsafe(Absolute.uri(uri));
    assertThat(maybeContext).isPresent();
    assertThat(maybeContext.get().isDocumentOpened(documentContext)).isTrue();

    // when - close the document (which should wait for executor to terminate)
    var closeParams = new DidCloseTextDocumentParams();
    closeParams.setTextDocument(new TextDocumentIdentifier(uri));
    textDocumentService.didClose(closeParams);

    // then - verify the document is properly closed
    // The close should complete successfully even if executor needs time to terminate
    var maybeContext2 = serverContextProvider.getServerContextUnsafe(Absolute.uri(uri));
    assertThat(maybeContext2).isPresent();
    assertThat(maybeContext2.get().isDocumentOpened(documentContext)).isFalse();
  }

  @Test
  void didClosePublishesEmptyDiagnosticsWhenClientDoesNotSupportPullDiagnostics() throws IOException {
    // given - open a document
    var textDocumentItem = getTextDocumentItem();
    var didOpenParams = new DidOpenTextDocumentParams(textDocumentItem);
    textDocumentService.didOpen(didOpenParams);

    // Simulate client without pull diagnostics support
    var capabilities = new ClientCapabilities();
    // No TextDocumentClientCapabilities.diagnostic set
    when(clientCapabilitiesHolder.getCapabilities()).thenReturn(Optional.of(capabilities));
    textDocumentService.handleInitializeEvent(null);

    // Clear any invocations from didOpen
    clearInvocations(diagnosticProvider);

    // when
    var closeParams = new DidCloseTextDocumentParams();
    closeParams.setTextDocument(new TextDocumentIdentifier(textDocumentItem.getUri()));
    textDocumentService.didClose(closeParams);

    // then - publishEmptyDiagnosticList should be called
    verify(diagnosticProvider).publishEmptyDiagnosticList(any());
  }

  @Test
  void didCloseDoesNotPublishEmptyDiagnosticsWhenClientSupportsPullDiagnostics() throws IOException {
    // given - open a document
    var textDocumentItem = getTextDocumentItem();
    var didOpenParams = new DidOpenTextDocumentParams(textDocumentItem);
    textDocumentService.didOpen(didOpenParams);

    // Simulate client with pull diagnostics support
    var capabilities = new ClientCapabilities();
    var textDocumentCapabilities = new TextDocumentClientCapabilities();
    textDocumentCapabilities.setDiagnostic(new DiagnosticCapabilities());
    capabilities.setTextDocument(textDocumentCapabilities);
    when(clientCapabilitiesHolder.getCapabilities()).thenReturn(Optional.of(capabilities));
    textDocumentService.handleInitializeEvent(null);

    // Clear any invocations from didOpen
    clearInvocations(diagnosticProvider);

    // when
    var closeParams = new DidCloseTextDocumentParams();
    closeParams.setTextDocument(new TextDocumentIdentifier(textDocumentItem.getUri()));
    textDocumentService.didClose(closeParams);

    // then - publishEmptyDiagnosticList should NOT be called
    verify(diagnosticProvider, never()).publishEmptyDiagnosticList(any());
  }

  @Test
  void didSave() {
    DidSaveTextDocumentParams params = new DidSaveTextDocumentParams();
    params.setTextDocument(getTextDocumentIdentifier());
    textDocumentService.didSave(params);
  }

  /**
   * Воспроизводит ScopeNotActiveException из Sentry:
   * didSave обращается к workspace-scoped LanguageServerConfiguration без workspace-контекста,
   * из-за чего proxy beans (LanguageServerConfiguration, DiagnosticComputer) не резолвятся.
   */
  @Test
  void didSave_setsWorkspaceContextWhenValidating() throws IOException {
    doOpen();

    var capturedWorkspaceUri = new AtomicReference<URI>();
    doAnswer(invocation -> {
      capturedWorkspaceUri.compareAndSet(null, WorkspaceContextHolder.get());
      return null;
    }).when(diagnosticProvider).computeAndPublishDiagnostics(any());

    var params = new DidSaveTextDocumentParams();
    params.setTextDocument(getTextDocumentIdentifier());
    textDocumentService.didSave(params);

    var expectedWorkspaceUri = Absolute.uri(new File("./src/test/resources").getAbsoluteFile().toURI());
    await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
      assertThat(capturedWorkspaceUri.get())
        .as("Workspace context must be set when DiagnosticProvider.computeAndPublishDiagnostics is called during "
          + "didSave (otherwise workspace-scoped LanguageServerConfiguration cannot be resolved)")
        .isNotNull()
        .isEqualTo(expectedWorkspaceUri)
    );
  }

  /**
   * Воспроизводит валидацию устаревшего содержимого:
   * клиент шлёт didChange и сразу didSave; пока {@code DocumentChangeExecutor} не применил изменения
   * (применение детерминированно заблокировано write lock документа), didSave обязан дождаться
   * свежего содержимого, а не валидировать старый текст.
   */
  @Test
  void didSave_validatesFreshContentAfterPendingDidChange() throws IOException {
    // given - opened document and a client without pull diagnostics support
    var textDocumentItem = getTextDocumentItem();
    textDocumentService.didOpen(new DidOpenTextDocumentParams(textDocumentItem));

    when(clientCapabilitiesHolder.getCapabilities()).thenReturn(Optional.of(new ClientCapabilities()));
    textDocumentService.handleInitializeEvent(null);
    clearInvocations(diagnosticProvider);

    var validatedContent = new AtomicReference<String>();
    doAnswer(invocation -> {
      DocumentContext validatedDocument = invocation.getArgument(0);
      validatedContent.compareAndSet(null, validatedDocument.getContent());
      return null;
    }).when(diagnosticProvider).computeAndPublishDiagnostics(any());

    var uri = Absolute.uri(textDocumentItem.getUri());
    var maybeContext = serverContextProvider.getServerContextUnsafe(uri);
    assertThat(maybeContext).isPresent();
    var documentLock = maybeContext.get().getDocumentLock(uri);

    // when - didChange is still pending (blocked by the write lock) and didSave arrives immediately
    documentLock.writeLock().lock();
    try {
      var changeParams = new DidChangeTextDocumentParams();
      changeParams.setTextDocument(new VersionedTextDocumentIdentifier(textDocumentItem.getUri(), 2));
      changeParams.setContentChanges(List.of(
        new TextDocumentContentChangeEvent(Ranges.create(0, 0, 0, 0), "// fresh change marker\n")
      ));
      textDocumentService.didChange(changeParams);

      var saveParams = new DidSaveTextDocumentParams();
      saveParams.setTextDocument(new TextDocumentIdentifier(textDocumentItem.getUri()));
      textDocumentService.didSave(saveParams);
    } finally {
      documentLock.writeLock().unlock();
    }

    // then - validation must see the content with the pending change applied
    await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
      assertThat(validatedContent.get())
        .as("didSave must validate document content with all pending didChange applied")
        .isNotNull()
        .startsWith("// fresh change marker")
    );
  }

  @Test
  void reset() {
    textDocumentService.reset();
  }

  @Test
  void testDiagnosticsUnknownFile() throws ExecutionException, InterruptedException {
    // when
    var params = new DiagnosticParams(getTextDocumentIdentifier());
    var diagnostics = textDocumentService.diagnostics(params).get();

    // then
    assertThat(diagnostics.getDiagnostics()).isEmpty();
  }

  @Test
  void testDiagnosticsKnownFile() throws ExecutionException, InterruptedException, IOException {
    // given
    var textDocumentItem = getTextDocumentItem();
    var didOpenParams = new DidOpenTextDocumentParams(textDocumentItem);
    textDocumentService.didOpen(didOpenParams);

    // when
    var params = new DiagnosticParams(getTextDocumentIdentifier());
    var diagnostics = textDocumentService.diagnostics(params).get();

    // then
    assertThat(diagnostics.getDiagnostics()).isNotEmpty();
  }

  @Test
  void testDiagnosticsKnownFileFilteredRange() throws ExecutionException, InterruptedException, IOException {
    // given
    var textDocumentItem = getTextDocumentItem();
    var didOpenParams = new DidOpenTextDocumentParams(textDocumentItem);
    textDocumentService.didOpen(didOpenParams);

    // when
    var params = new DiagnosticParams(getTextDocumentIdentifier());
    params.setRange(Ranges.create(1, 0, 2, 0));
    var diagnostics = textDocumentService.diagnostics(params).get();

    // then
    assertThat(diagnostics.getDiagnostics()).hasSize(2);
  }

  @Test
  void testStandardDiagnosticUnknownFile() throws ExecutionException, InterruptedException {
    // when
    var params = new DocumentDiagnosticParams(getTextDocumentIdentifier());
    var diagnosticReport = textDocumentService.diagnostic(params).get();

    // then
    assertThat(diagnosticReport).isNotNull();
    assertThat(diagnosticReport.getLeft()).isNotNull();
    assertThat(diagnosticReport.getLeft().getItems()).isEmpty();
  }

  @Test
  void testStandardDiagnosticKnownFile() throws ExecutionException, InterruptedException, IOException {
    // given
    var textDocumentItem = getTextDocumentItem();
    var didOpenParams = new DidOpenTextDocumentParams(textDocumentItem);
    textDocumentService.didOpen(didOpenParams);

    // when
    var params = new DocumentDiagnosticParams(getTextDocumentIdentifier());
    var diagnosticReport = textDocumentService.diagnostic(params).get();

    // then
    assertThat(diagnosticReport).isNotNull();
    assertThat(diagnosticReport.getLeft()).isNotNull();
    assertThat(diagnosticReport.getLeft().getItems()).isNotEmpty();
  }

  @Test
  void testRename() {
    var params = new RenameParams();
    params.setTextDocument(getTextDocumentIdentifier());
    params.setPosition(new Position(0, 16));

    var result = textDocumentService.rename(params);

    assertThat(result).isNotNull();
  }

  @Test
  void testRenamePrepare() {
    var params = new PrepareRenameParams();
    params.setTextDocument(getTextDocumentIdentifier());
    params.setPosition(new Position(0, 16));

    var result = textDocumentService.prepareRename(params);

    assertThat(result).isNotNull();
  }

  @Test
  void testCancellationSupport() throws IOException {
    // given
    var textDocumentItem = getTextDocumentItem();
    var didOpenParams = new DidOpenTextDocumentParams(textDocumentItem);
    textDocumentService.didOpen(didOpenParams);

    // when - create a future that supports cancellation
    var params = new DocumentDiagnosticParams(getTextDocumentIdentifier());
    var future = textDocumentService.diagnostic(params);

    // then - verify that the future supports cancellation (can be cancelled)
    // The CompletableFutures.computeAsync returns a future that can be cancelled
    var wasCancelled = future.cancel(true);

    // If the future completed before cancel was called, it returns false
    // If cancelled successfully, it returns true
    // Either way, we verify the future is in a terminal state
    assertThat(future.isDone()).isTrue();

    // If cancellation was successful, verify the cancelled state
    if (wasCancelled) {
      assertThat(future.isCancelled()).isTrue();
    }
  }
  
  @Test
  void testImplementation() throws ExecutionException, InterruptedException {
    var params = new ImplementationParams();
    params.setTextDocument(getTextDocumentIdentifier());
    params.setPosition(new Position(0, 0));

    var result = textDocumentService.implementation(params).get();

    assertThat(result).isNotNull();
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).isEmpty();
  }

  @Test
  void prepareTypeHierarchyRoutesForOsClass() throws Exception {
    // Открываем всю цепочку, чтобы super/subtypes резолвились в непустой результат.
    openOsDocument("./src/test/resources/type-hierarchy/Животное.os");
    openOsDocument("./src/test/resources/type-hierarchy/Кошка.os");
    openOsDocument("./src/test/resources/type-hierarchy/Собака.os");
    var item = openOsDocument("./src/test/resources/type-hierarchy/Млекопитающее.os");
    var docId = new TextDocumentIdentifier(item.getUri());

    var prepared = textDocumentService
      .prepareTypeHierarchy(new TypeHierarchyPrepareParams(docId, new Position(0, 0))).get();

    assertThat(prepared).isNotNull().isNotEmpty();

    var hierarchyItem = prepared.get(0);
    var supertypes = textDocumentService
      .typeHierarchySupertypes(new TypeHierarchySupertypesParams(hierarchyItem)).get();
    var subtypes = textDocumentService
      .typeHierarchySubtypes(new TypeHierarchySubtypesParams(hierarchyItem)).get();

    assertThat(supertypes).isNotNull().isNotEmpty();
    assertThat(subtypes).isNotNull().isNotEmpty();
  }

  @Test
  void prepareTypeHierarchyReturnsNullForNonHierarchyOsFile() throws Exception {
    // Плоский .os-класс без наследования/реализаций — иерархии нет, ожидаем null.
    var item = openOsDocument("./src/test/resources/standalone-class.os");
    var docId = new TextDocumentIdentifier(item.getUri());

    var prepared = textDocumentService
      .prepareTypeHierarchy(new TypeHierarchyPrepareParams(docId, new Position(0, 0))).get();

    assertThat(prepared).isNull();
  }

  @Test
  void implementationRoutesForOsInterface() throws Exception {
    var item = openOsDocument("./src/test/resources/oscript-libraries/interface-lib/src/МойИнтерфейс.os");
    var docId = new TextDocumentIdentifier(item.getUri());

    var result = textDocumentService
      .implementation(new ImplementationParams(docId, new Position(0, 0))).get();

    assertThat(result).isNotNull();
    assertThat(result.isLeft()).isTrue();
    // Реализации (Реализация1/Реализация2) проиндексированы как library-классы interface-lib.
    assertThat(result.getLeft()).isNotEmpty();
  }

  private TextDocumentItem openOsDocument(String path) throws IOException {
    File file = new File(path);
    String uri = Absolute.uri(file).toString();
    String content = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
    var item = new TextDocumentItem(uri, "bsl", 1, content);
    textDocumentService.didOpen(new DidOpenTextDocumentParams(item));
    return item;
  }

  /**
   * Регрессионный тест: {@code withFreshDocumentContextInternal} должен устанавливать
   * workspace context на рабочем потоке {@code text-document-service-X},
   * чтобы workspace-scoped бины были доступны из supplier'а LSP-методов.
   * <p>
   * Проверяется сценарий без {@code DocumentChangeExecutor} (документ закрыт):
   * в этом случае {@code thenCompose} выполняется на LSP4J-потоке, у которого
   * нет workspace context — без фикса контекст не устанавливается.
   */
  @Test
  void hover_setsWorkspaceContextOnWorkerThread() throws Exception {
    // open then close — removes DocumentChangeExecutor from map
    // so thenCompose runs on the calling (LSP4J) thread with no workspace context
    doOpen();
    var closeParams = new DidCloseTextDocumentParams();
    closeParams.setTextDocument(getTextDocumentIdentifier());
    textDocumentService.didClose(closeParams);

    // Simulate LSP4J handler thread: no workspace context on calling thread
    var savedContext = WorkspaceContextHolder.get();
    WorkspaceContextHolder.clear();
    try {
      var capturedUri = new AtomicReference<URI>();
      doAnswer(invocation -> {
        capturedUri.set(WorkspaceContextHolder.get());
        // Не зовём реальный метод — он на закрытом документе обращается к
        // tokenizer'у через TypeService и упирается в null; тест-кейс не про
        // это, а про установку WorkspaceContextHolder на воркере.
        return Optional.empty();
      }).when(hoverProvider).getHover(any(), any());

      var params = new HoverParams(getTextDocumentIdentifier(), new Position(0, 0));
      textDocumentService.hover(params).get();

      assertThat(capturedUri.get())
        .as("WorkspaceContextHolder must be set on text-document-service worker thread")
        .isNotNull();
    } finally {
      if (savedContext != null) {
        WorkspaceContextHolder.set(savedContext);
      }
    }
  }

  // Tests for unknown file handling (dryRun - no didOpen before call)

  @Test
  void hoverUnknownFile() throws Exception {
    var params = new HoverParams(getTextDocumentIdentifier(), new Position(0, 0));
    var result = textDocumentService.hover(params).get();
    assertThat(result).isNull();
  }

  @Test
  void documentHighlightUnknownFile() throws Exception {
    var params = new DocumentHighlightParams(getTextDocumentIdentifier(), new Position(0, 0));
    var result = textDocumentService.documentHighlight(params).get();
    assertThat(result).isEmpty();
  }

  @Test
  void definitionUnknownFile() throws Exception {
    var params = new DefinitionParams(getTextDocumentIdentifier(), new Position(0, 0));
    var result = textDocumentService.definition(params).get();
    assertThat(result.isRight()).isTrue();
    assertThat(result.getRight()).isEmpty();
  }

  @Test
  void definitionDelegatesToProvider() throws Exception {
    // given - открытый документ; провайдер сам решает формат ответа (linkSupport),
    // сервис лишь делегирует ему запрос.
    var textDocumentItem = getTextDocumentItem();
    textDocumentService.didOpen(new DidOpenTextDocumentParams(textDocumentItem));

    var locationLink = new LocationLink(
      textDocumentItem.getUri(),
      Ranges.create(0, 0, 0, 10),
      Ranges.create(0, 10, 0, 20),
      Ranges.create(1, 4, 1, 8)
    );
    doReturn(Either.forRight(List.of(locationLink))).when(definitionProvider).getDefinition(any(), any());

    // when
    var params = new DefinitionParams(getTextDocumentIdentifier(), new Position(1, 4));
    var result = textDocumentService.definition(params).get();

    // then - сервис прозрачно возвращает результат провайдера
    assertThat(result.isRight()).isTrue();
    assertThat(result.getRight()).hasSize(1);
    assertThat(result.getRight().get(0)).isEqualTo(locationLink);
  }

  @Test
  void referencesUnknownFile() throws Exception {
    var params = new ReferenceParams(getTextDocumentIdentifier(), new Position(0, 0), new ReferenceContext(false));
    var result = textDocumentService.references(params).get();
    assertThat(result).isEmpty();
  }

  @Test
  void documentSymbolUnknownFile() throws Exception {
    var params = new DocumentSymbolParams(getTextDocumentIdentifier());
    var result = textDocumentService.documentSymbol(params).get();
    assertThat(result).isNull();
  }

  @Test
  void codeActionUnknownFile() throws Exception {
    var params = new CodeActionParams(getTextDocumentIdentifier(), Ranges.create(0, 0, 0, 0), new CodeActionContext(List.of()));
    var result = textDocumentService.codeAction(params).get();
    assertThat(result).isNull();
  }

  @Test
  void codeLensUnknownFile() throws Exception {
    var params = new CodeLensParams(getTextDocumentIdentifier());
    var result = textDocumentService.codeLens(params).get();
    assertThat(result).isEmpty();
  }

  @Test
  void formattingUnknownFile() throws Exception {
    var params = new DocumentFormattingParams(getTextDocumentIdentifier(), new FormattingOptions());
    var result = textDocumentService.formatting(params).get();
    assertThat(result).isNull();
  }

  @Test
  void rangeFormattingUnknownFile() throws Exception {
    var params = new DocumentRangeFormattingParams(getTextDocumentIdentifier(), new FormattingOptions(), Ranges.create(0, 0, 0, 0));
    var result = textDocumentService.rangeFormatting(params).get();
    assertThat(result).isNull();
  }

  @Test
  void foldingRangeUnknownFile() throws Exception {
    var params = new FoldingRangeRequestParams(getTextDocumentIdentifier());
    var result = textDocumentService.foldingRange(params).get();
    assertThat(result).isNull();
  }

  @Test
  void prepareCallHierarchyUnknownFile() throws Exception {
    var params = new CallHierarchyPrepareParams(getTextDocumentIdentifier(), new Position(0, 0));
    var result = textDocumentService.prepareCallHierarchy(params).get();
    assertThat(result).isNull();
  }

  @Test
  void callHierarchyIncomingCallsUnknownFile() throws Exception {
    var item = new CallHierarchyItem();
    item.setUri(getTextDocumentIdentifier().getUri());
    var params = new CallHierarchyIncomingCallsParams(item);
    var result = textDocumentService.callHierarchyIncomingCalls(params).get();
    assertThat(result).isEmpty();
  }

  @Test
  void callHierarchyOutgoingCallsUnknownFile() throws Exception {
    var item = new CallHierarchyItem();
    item.setUri(getTextDocumentIdentifier().getUri());
    var params = new CallHierarchyOutgoingCallsParams(item);
    var result = textDocumentService.callHierarchyOutgoingCalls(params).get();
    assertThat(result).isEmpty();
  }

  @Test
  void prepareTypeHierarchyUnknownFile() throws Exception {
    var params = new TypeHierarchyPrepareParams(getTextDocumentIdentifier(), new Position(0, 0));
    var result = textDocumentService.prepareTypeHierarchy(params).get();
    assertThat(result).isNull();
  }

  @Test
  void typeHierarchySupertypesUnknownFile() throws Exception {
    var params = new TypeHierarchySupertypesParams(unknownTypeHierarchyItem());
    var result = textDocumentService.typeHierarchySupertypes(params).get();
    assertThat(result).isEmpty();
  }

  @Test
  void typeHierarchySubtypesUnknownFile() throws Exception {
    var params = new TypeHierarchySubtypesParams(unknownTypeHierarchyItem());
    var result = textDocumentService.typeHierarchySubtypes(params).get();
    assertThat(result).isEmpty();
  }

  private TypeHierarchyItem unknownTypeHierarchyItem() {
    var zeroRange = new Range(new Position(0, 0), new Position(0, 0));
    return new TypeHierarchyItem(
      "Unknown",
      SymbolKind.Class,
      getTextDocumentIdentifier().getUri(),
      zeroRange,
      zeroRange
    );
  }

  @Test
  void semanticTokensFullUnknownFile() throws Exception {
    var params = new SemanticTokensParams(getTextDocumentIdentifier());
    var result = textDocumentService.semanticTokensFull(params).get();
    assertThat(result).isNull();
  }

  @Test
  void semanticTokensFullDeltaUnknownFile() throws Exception {
    var params = new SemanticTokensDeltaParams(getTextDocumentIdentifier(), "");
    var result = textDocumentService.semanticTokensFullDelta(params).get();
    assertThat(result).isNull();
  }

  @Test
  void semanticTokensRangeUnknownFile() throws Exception {
    var params = new SemanticTokensRangeParams(getTextDocumentIdentifier(), Ranges.create(0, 0, 0, 0));
    var result = textDocumentService.semanticTokensRange(params).get();
    assertThat(result).isNull();
  }

  @Test
  void selectionRangeUnknownFile() throws Exception {
    var params = new SelectionRangeParams(getTextDocumentIdentifier(), List.of(new Position(0, 0)));
    var result = textDocumentService.selectionRange(params).get();
    assertThat(result).isEmpty();
  }

  @Test
  void documentColorUnknownFile() throws Exception {
    var params = new DocumentColorParams(getTextDocumentIdentifier());
    var result = textDocumentService.documentColor(params).get();
    assertThat(result).isEmpty();
  }

  @Test
  void colorPresentationUnknownFile() throws Exception {
    var params = new ColorPresentationParams(getTextDocumentIdentifier(), new org.eclipse.lsp4j.Color(0, 0, 0, 0), Ranges.create(0, 0, 0, 0));
    var result = textDocumentService.colorPresentation(params).get();
    assertThat(result).isEmpty();
  }

  @Test
  void inlayHintUnknownFile() throws Exception {
    var params = new InlayHintParams(getTextDocumentIdentifier(), Ranges.create(0, 0, 0, 0));
    var result = textDocumentService.inlayHint(params).get();
    assertThat(result).isEmpty();
  }

  @Test
  void documentLinkUnknownFile() throws Exception {
    var params = new DocumentLinkParams(getTextDocumentIdentifier());
    var result = textDocumentService.documentLink(params).get();
    assertThat(result).isNull();
  }

  @Test
  void completionUnknownFile() throws Exception {
    // given
    var params = new CompletionParams(getTextDocumentIdentifier(), new Position(0, 0));

    // when
    var result = textDocumentService.completion(params).get();

    // then — CompletionList пустой, когда документа в индексе нет.
    assertThat(result).isNotNull();
    assertThat(result.isRight()).isTrue();
    assertThat(result.getRight().getItems()).isEmpty();
  }

  @Test
  void signatureHelpUnknownFile() throws Exception {
    // given
    var params = new SignatureHelpParams(getTextDocumentIdentifier(), new Position(0, 0));

    // when
    var result = textDocumentService.signatureHelp(params).get();

    // then
    assertThat(result).isNotNull();
    assertThat(result.getSignatures()).isEmpty();
  }

  @Test
  void completionForOpenedFileDelegatesToProvider() throws Exception {
    // given
    doOpen();
    var params = new CompletionParams(getTextDocumentIdentifier(), new Position(0, 0));

    // when
    var result = textDocumentService.completion(params).get();

    // then — провайдер вернул CompletionList (нерасширяемый), supplier не упал.
    assertThat(result).isNotNull();
    assertThat(result.isRight()).isTrue();
    assertThat(result.getRight()).isNotNull();
  }

  @Test
  void signatureHelpForOpenedFileDelegatesToProvider() throws Exception {
    // given
    doOpen();
    var params = new SignatureHelpParams(getTextDocumentIdentifier(), new Position(0, 0));

    // when
    var result = textDocumentService.signatureHelp(params).get();

    // then — без активного вызова на позиции (0,0) сигнатур не будет.
    assertThat(result).isNotNull();
    assertThat(result.getSignatures()).isEmpty();
  }

  private File getTestFile() {
    return new File("./src/test/resources/BSLTextDocumentServiceTest.bsl");
  }

  private TextDocumentItem getTextDocumentItem() throws IOException {
    File file = getTestFile();
    String uri = Absolute.uri(file).toString();

    String fileContent = FileUtils.readFileToString(file, StandardCharsets.UTF_8);

    return new TextDocumentItem(uri, "bsl", 1, fileContent);
  }

  private TextDocumentIdentifier getTextDocumentIdentifier() {
    // TODO: Переделать на TestUtils.getTextDocumentIdentifier();
    File file = getTestFile();
    String uri = Absolute.uri(file).toString();

    return new TextDocumentIdentifier(uri);
  }

  private void doOpen() throws IOException {
    DidOpenTextDocumentParams params = new DidOpenTextDocumentParams();
    params.setTextDocument(getTextDocumentItem());
    textDocumentService.didOpen(params);
  }

  // Tests for getOffset method

  @Test
  void getOffset_emptyContent() {
    // Empty content should return 0 for any position
    assertThat(BSLTextDocumentService.getOffset("", 0, 0)).isZero();
    assertThat(BSLTextDocumentService.getOffset("", 0, 5)).isZero();
    assertThat(BSLTextDocumentService.getOffset("", 1, 0)).isZero();
    assertThat(BSLTextDocumentService.getOffset("", 5, 10)).isZero();
  }

  @Test
  void getOffset_singleLineContent() {
    var content = "Hello, World!";
    // Line 0 with various character positions
    assertThat(BSLTextDocumentService.getOffset(content, 0, 0)).isZero();
    assertThat(BSLTextDocumentService.getOffset(content, 0, 5)).isEqualTo(5);
    assertThat(BSLTextDocumentService.getOffset(content, 0, 13)).isEqualTo(13);
    // Character position beyond line length should be capped
    assertThat(BSLTextDocumentService.getOffset(content, 0, 100)).isEqualTo(13);
    // Line beyond content should return end of content
    assertThat(BSLTextDocumentService.getOffset(content, 1, 0)).isEqualTo(13);
  }

  @Test
  void getOffset_multiLineWithLF() {
    var content = "Line1\nLine2\nLine3";
    // Line 0
    assertThat(BSLTextDocumentService.getOffset(content, 0, 0)).isZero();
    assertThat(BSLTextDocumentService.getOffset(content, 0, 3)).isEqualTo(3);
    // Line 1 starts at position 6 (after "Line1\n")
    assertThat(BSLTextDocumentService.getOffset(content, 1, 0)).isEqualTo(6);
    assertThat(BSLTextDocumentService.getOffset(content, 1, 3)).isEqualTo(9);
    // Line 2 starts at position 12 (after "Line1\nLine2\n")
    assertThat(BSLTextDocumentService.getOffset(content, 2, 0)).isEqualTo(12);
    assertThat(BSLTextDocumentService.getOffset(content, 2, 5)).isEqualTo(17);
    // Line beyond content
    assertThat(BSLTextDocumentService.getOffset(content, 3, 0)).isEqualTo(17);
  }

  @Test
  void getOffset_multiLineWithCRLF() {
    var content = "Line1\r\nLine2\r\nLine3";
    // Line 0
    assertThat(BSLTextDocumentService.getOffset(content, 0, 0)).isZero();
    assertThat(BSLTextDocumentService.getOffset(content, 0, 3)).isEqualTo(3);
    // Line 1 starts at position 7 (after "Line1\r\n")
    assertThat(BSLTextDocumentService.getOffset(content, 1, 0)).isEqualTo(7);
    assertThat(BSLTextDocumentService.getOffset(content, 1, 3)).isEqualTo(10);
    // Line 2 starts at position 14 (after "Line1\r\nLine2\r\n")
    assertThat(BSLTextDocumentService.getOffset(content, 2, 0)).isEqualTo(14);
    assertThat(BSLTextDocumentService.getOffset(content, 2, 5)).isEqualTo(19);
  }

  @Test
  void getOffset_multiLineWithCR() {
    var content = "Line1\rLine2\rLine3";
    // Line 0
    assertThat(BSLTextDocumentService.getOffset(content, 0, 0)).isZero();
    // Line 1 starts at position 6 (after "Line1\r")
    assertThat(BSLTextDocumentService.getOffset(content, 1, 0)).isEqualTo(6);
    // Line 2 starts at position 12 (after "Line1\rLine2\r")
    assertThat(BSLTextDocumentService.getOffset(content, 2, 0)).isEqualTo(12);
  }

  @Test
  void getOffset_onlyLineBreaks() {
    // Content with only LF line breaks
    var lfOnly = "\n\n\n";
    assertThat(BSLTextDocumentService.getOffset(lfOnly, 0, 0)).isZero();
    assertThat(BSLTextDocumentService.getOffset(lfOnly, 1, 0)).isEqualTo(1);
    assertThat(BSLTextDocumentService.getOffset(lfOnly, 2, 0)).isEqualTo(2);
    assertThat(BSLTextDocumentService.getOffset(lfOnly, 3, 0)).isEqualTo(3);
    assertThat(BSLTextDocumentService.getOffset(lfOnly, 4, 0)).isEqualTo(3);

    // Content with only CRLF line breaks
    var crlfOnly = "\r\n\r\n";
    assertThat(BSLTextDocumentService.getOffset(crlfOnly, 0, 0)).isZero();
    assertThat(BSLTextDocumentService.getOffset(crlfOnly, 1, 0)).isEqualTo(2);
    assertThat(BSLTextDocumentService.getOffset(crlfOnly, 2, 0)).isEqualTo(4);
    assertThat(BSLTextDocumentService.getOffset(crlfOnly, 3, 0)).isEqualTo(4);
  }

  @Test
  void getOffset_characterBeyondLineLength() {
    var content = "AB\nCD\nEF";
    // Line 0 has length 2, character 10 should be capped to content length
    assertThat(BSLTextDocumentService.getOffset(content, 0, 10)).isEqualTo(8);
    // Line 1 starts at 3, character 10 would be 13, capped to 8
    assertThat(BSLTextDocumentService.getOffset(content, 1, 10)).isEqualTo(8);
    // Line 2 starts at 6, character 10 would be 16, capped to 8
    assertThat(BSLTextDocumentService.getOffset(content, 2, 10)).isEqualTo(8);
  }

  @Test
  void getOffset_lineBeyondDocumentLength() {
    var content = "Only one line";
    // Line 0 exists
    assertThat(BSLTextDocumentService.getOffset(content, 0, 0)).isZero();
    // Lines 1, 5, 100 don't exist, should return end of content
    assertThat(BSLTextDocumentService.getOffset(content, 1, 0)).isEqualTo(13);
    assertThat(BSLTextDocumentService.getOffset(content, 5, 0)).isEqualTo(13);
    assertThat(BSLTextDocumentService.getOffset(content, 100, 0)).isEqualTo(13);
  }

  @Test
  void getOffset_mixedLineEndings() {
    // Mixed: LF, then CRLF, then CR
    var content = "A\nB\r\nC\rD";
    // Line 0: starts at 0
    assertThat(BSLTextDocumentService.getOffset(content, 0, 0)).isZero();
    // Line 1: starts at 2 (after "A\n")
    assertThat(BSLTextDocumentService.getOffset(content, 1, 0)).isEqualTo(2);
    // Line 2: starts at 5 (after "A\nB\r\n")
    assertThat(BSLTextDocumentService.getOffset(content, 2, 0)).isEqualTo(5);
    // Line 3: starts at 7 (after "A\nB\r\nC\r")
    assertThat(BSLTextDocumentService.getOffset(content, 3, 0)).isEqualTo(7);
  }
}
