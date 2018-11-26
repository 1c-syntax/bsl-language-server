/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018
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

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BSLTextDocumentServiceTest {

  @Test
  void completion() throws ExecutionException, InterruptedException {
    // given
    BSLTextDocumentService textDocumentService = new BSLTextDocumentService();
    CompletionParams position = new CompletionParams();

    // when
    CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion = textDocumentService.completion(position);

    // then
    Either<List<CompletionItem>, CompletionList> listCompletionListEither = completion.get();
    List<CompletionItem> completionItems = listCompletionListEither.getLeft();

    boolean allMatch = completionItems.stream().allMatch(completionItem -> "Hello World".equals(completionItem.getLabel()));
    assertTrue(allMatch, "Must contain Hello World!");

  }
}
