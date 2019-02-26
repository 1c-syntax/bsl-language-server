/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
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
package org.github._1c_syntax.bsl.languageserver;

import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class BSLTextDocumentServiceTest {

  private BSLTextDocumentService textDocumentService = new BSLTextDocumentService();

  @Test
  void completion() throws ExecutionException, InterruptedException {
    // given
    CompletionParams position = new CompletionParams();

    // when
    CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion = textDocumentService.completion(position);

    // then
    Either<List<CompletionItem>, CompletionList> listCompletionListEither = completion.get();
    List<CompletionItem> completionItems = listCompletionListEither.getLeft();

    assertThat(completionItems).allMatch(completionItem -> "Hello World".equals(completionItem.getLabel()));
  }

  @Test
  void resolveCompletionItem() {
    Throwable thrown = catchThrowable(() -> textDocumentService.resolveCompletionItem(null));
    assertThat(thrown).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void hoverEmpty() throws IOException, ExecutionException, InterruptedException {
    // given
    doOpen();

    TextDocumentPositionParams params = new TextDocumentPositionParams();
    params.setTextDocument(getTextDocumentIdentifier());
    params.setPosition(new Position(0, 0));

    // when
    CompletableFuture<Hover> hover = textDocumentService.hover(params);

    // then
    Hover hoverValue = hover.get();
    assertThat(hoverValue).isNull();
  }

  @Test
  void hoverSubName() throws IOException, ExecutionException, InterruptedException {
    // given
    doOpen();

    TextDocumentPositionParams params = new TextDocumentPositionParams();
    params.setTextDocument(getTextDocumentIdentifier());
    params.setPosition(new Position(0, 20));

    // when
    CompletableFuture<Hover> hover = textDocumentService.hover(params);

    // then
    Hover hoverValue = hover.get();
    assertThat(hoverValue.getContents().getRight().getValue()).isEqualTo("ИмяПроцедуры");
    assertThat(hoverValue.getRange().getStart()).isEqualTo(new Position(0, 10));
    assertThat(hoverValue.getRange().getEnd()).isEqualTo(new Position(0, 22));
  }

  @Test
  void signatureHelp() {
    Throwable thrown = catchThrowable(() -> textDocumentService.signatureHelp(null));
    assertThat(thrown).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void definition() {
    Throwable thrown = catchThrowable(() -> textDocumentService.definition(null));
    assertThat(thrown).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void references() {
    Throwable thrown = catchThrowable(() -> textDocumentService.references(null));
    assertThat(thrown).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void documentHighlight() {
    Throwable thrown = catchThrowable(() -> textDocumentService.documentHighlight(null));
    assertThat(thrown).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void documentSymbol() {
    Throwable thrown = catchThrowable(() -> textDocumentService.documentSymbol(null));
    assertThat(thrown).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void codeAction() {
    Throwable thrown = catchThrowable(() -> textDocumentService.codeAction(null));
    assertThat(thrown).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void codeLens() {
    Throwable thrown = catchThrowable(() -> textDocumentService.codeLens(null));
    assertThat(thrown).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void resolveCodeLens() {
    Throwable thrown = catchThrowable(() -> textDocumentService.resolveCodeLens(null));
    assertThat(thrown).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void formatting() {
    // TODO:
//    Throwable thrown = catchThrowable(() -> textDocumentService.formatting(null));
//    assertThat(thrown).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void rangeFormatting() {
    // TODO:
//    Throwable thrown = catchThrowable(() -> textDocumentService.rangeFormatting(null));
//    assertThat(thrown).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void onTypeFormatting() {
    Throwable thrown = catchThrowable(() -> textDocumentService.onTypeFormatting(null));
    assertThat(thrown).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void rename() {
    Throwable thrown = catchThrowable(() -> textDocumentService.rename(null));
    assertThat(thrown).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void didOpen() throws IOException {
    doOpen();
  }

  @Test
  void didChange() throws IOException {
    DidChangeTextDocumentParams params = new DidChangeTextDocumentParams();

    params.setTextDocument(new VersionedTextDocumentIdentifier());

    String fileContent = FileUtils.readFileToString(getTestFile(), StandardCharsets.UTF_8);
    TextDocumentContentChangeEvent changeEvent = new TextDocumentContentChangeEvent(fileContent);

    List<TextDocumentContentChangeEvent> contentChanges = new ArrayList<>();
    contentChanges.add(changeEvent);
    params.setContentChanges(contentChanges);

    textDocumentService.didChange(params);
  }

  @Test
  void didClose() {
    DidCloseTextDocumentParams params = new DidCloseTextDocumentParams();
    params.setTextDocument(getTextDocumentIdentifier());
    textDocumentService.didClose(params);
  }

  @Test
  void didSave() {
    textDocumentService.didSave(null);
  }

  @Test
  void connect() {
    textDocumentService.connect(null);
  }

  @Test
  void reset() {
    textDocumentService.reset();
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