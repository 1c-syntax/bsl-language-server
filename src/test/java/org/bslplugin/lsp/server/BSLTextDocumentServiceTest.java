package org.bslplugin.lsp.server;

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
