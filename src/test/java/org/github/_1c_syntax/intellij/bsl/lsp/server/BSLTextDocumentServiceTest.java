/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
 * Alexey Sosnoviy <labotamy@yandex.ru>, Nikita Gryzlov <nixel2007@gmail.com>
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
package org.github._1c_syntax.intellij.bsl.lsp.server;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

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
    CompletableFuture<CompletionItem> completionItem = textDocumentService.resolveCompletionItem(null);
    assertThat(completionItem).isNull();
  }

  @Test
  void hover() {
  }

  @Test
  void signatureHelp() {
    CompletableFuture<SignatureHelp> signatureHelp = textDocumentService.signatureHelp(null);
    assertThat(signatureHelp).isNull();
  }

  @Test
  void definition() {
    CompletableFuture<List<? extends Location>> definition = textDocumentService.definition(null);
    assertThat(definition).isNull();
  }

  @Test
  void references() {
    CompletableFuture<List<? extends Location>> references = textDocumentService.references(null);
    assertThat(references).isNull();
  }

  @Test
  void documentHighlight() {
    CompletableFuture<List<? extends DocumentHighlight>> documentHighlight = textDocumentService.documentHighlight(null);
    assertThat(documentHighlight).isNull();
  }

  @Test
  void documentSymbol() {
    CompletableFuture<List<? extends SymbolInformation>> documentSymbol = textDocumentService.documentSymbol(null);
    assertThat(documentSymbol).isNull();
  }

  @Test
  void codeAction() {
    CompletableFuture<List<? extends Command>> commands = textDocumentService.codeAction(null);
    assertThat(commands).isNull();
  }

  @Test
  void codeLens() {
    CompletableFuture<List<? extends CodeLens>> codeLens = textDocumentService.codeLens(null);
    assertThat(codeLens).isNull();
  }

  @Test
  void resolveCodeLens() {
    CompletableFuture<CodeLens> codeLens = textDocumentService.resolveCodeLens(null);
    assertThat(codeLens).isNull();
  }

  @Test
  void formatting() {
    CompletableFuture<List<? extends TextEdit>> formatting = textDocumentService.formatting(null);
    assertThat(formatting).isNull();
  }

  @Test
  void rangeFormatting() {
    CompletableFuture<List<? extends TextEdit>> rangeFormatting = textDocumentService.rangeFormatting(null);
    assertThat(rangeFormatting).isNull();
  }

  @Test
  void onTypeFormatting() {
    CompletableFuture<List<? extends TextEdit>> result = textDocumentService.onTypeFormatting(null);
    assertThat(result).isNull();
  }

  @Test
  void rename() {
    CompletableFuture<WorkspaceEdit> result = textDocumentService.rename(null);
    assertThat(result).isNull();

  }

  @Test
  void didOpen() {
    textDocumentService.didOpen(null);
  }

  @Test
  void didChange() {
    textDocumentService.didChange(null);
  }

  @Test
  void didClose() {
    textDocumentService.didClose(null);
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
}