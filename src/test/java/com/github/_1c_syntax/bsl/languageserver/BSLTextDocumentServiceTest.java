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
package com.github._1c_syntax.bsl.languageserver;

import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.jsonrpc.DiagnosticParams;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentDiagnosticParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PrepareRenameParams;
import org.eclipse.lsp4j.RenameParams;
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
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@CleanupContextBeforeClassAndAfterClass
class BSLTextDocumentServiceTest {

  @Autowired
  private BSLTextDocumentService textDocumentService;
  @MockitoSpyBean
  private ServerContext serverContext;

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

    var documentContext = serverContext.getDocument(textDocumentItem.getUri());
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

    var documentContext = serverContext.getDocument(textDocumentItem.getUri());
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

    var documentContext = serverContext.getDocument(textDocumentItem.getUri());
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

  private File getTestFile() {
    return new File("./src/test/resources/BSLTextDocumentServiceTest.bsl");
  }

  private TextDocumentItem getTextDocumentItem() throws IOException {
    File file = getTestFile();
    String uri = file.toURI().toString();

    String fileContent = FileUtils.readFileToString(file, StandardCharsets.UTF_8);

    return new TextDocumentItem(uri, "bsl", 1, fileContent);
  }

  private TextDocumentIdentifier getTextDocumentIdentifier() {
    // TODO: Переделать на TestUtils.getTextDocumentIdentifier();
    File file = getTestFile();
    String uri = file.toURI().toString();

    return new TextDocumentIdentifier(uri);
  }

  private void doOpen() throws IOException {
    DidOpenTextDocumentParams params = new DidOpenTextDocumentParams();
    params.setTextDocument(getTextDocumentItem());
    textDocumentService.didOpen(params);
  }
}
