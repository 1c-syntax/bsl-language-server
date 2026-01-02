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

import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.jsonrpc.DiagnosticParams;
import com.github._1c_syntax.bsl.languageserver.providers.DiagnosticProvider;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.utils.Absolute;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.DiagnosticCapabilities;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentDiagnosticParams;
import org.eclipse.lsp4j.ImplementationParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PrepareRenameParams;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@CleanupContextBeforeClassAndAfterClass
class BSLTextDocumentServiceTest {

  @Autowired
  private BSLTextDocumentService textDocumentService;
  @MockitoSpyBean
  private ServerContext serverContext;
  @MockitoSpyBean
  private DiagnosticProvider diagnosticProvider;
  @MockitoSpyBean
  private ClientCapabilitiesHolder clientCapabilitiesHolder;


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

    var documentContext = serverContext.getDocumentUnsafe(textDocumentItem.getUri());
    assertThat(documentContext).isNotNull();

    // when - incremental change: insert text at position
    var params = new DidChangeTextDocumentParams();
    var uri = textDocumentItem.getUri();
    params.setTextDocument(new VersionedTextDocumentIdentifier(uri, 2));

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

    var documentContext = serverContext.getDocumentUnsafe(textDocumentItem.getUri());
    assertThat(documentContext).isNotNull();

    // when - multiple incremental changes
    var params = new DidChangeTextDocumentParams();
    var uri = textDocumentItem.getUri();
    params.setTextDocument(new VersionedTextDocumentIdentifier(uri, 2));

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

    var documentContext = serverContext.getDocumentUnsafe(textDocumentItem.getUri());
    assertThat(documentContext).isNotNull();

    // when - incremental change: delete text
    var params = new DidChangeTextDocumentParams();
    var uri = textDocumentItem.getUri();
    params.setTextDocument(new VersionedTextDocumentIdentifier(uri, 2));

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
    assertThat(result.isRight()).isTrue();
    assertThat(result.getRight()).isEmpty();
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
