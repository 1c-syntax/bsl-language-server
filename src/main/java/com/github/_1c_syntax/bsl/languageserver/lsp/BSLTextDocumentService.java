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
package com.github._1c_syntax.bsl.languageserver.lsp;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.configuration.diagnostics.ComputeTrigger;
import com.github._1c_syntax.bsl.languageserver.context.DocumentChangeExecutor;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContentUnavailableException;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.DocumentState;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentRemovedEvent;
import com.github._1c_syntax.bsl.languageserver.events.LanguageServerInitializedEvent;
import com.github._1c_syntax.bsl.languageserver.jsonrpc.DiagnosticParams;
import com.github._1c_syntax.bsl.languageserver.jsonrpc.Diagnostics;
import com.github._1c_syntax.bsl.languageserver.jsonrpc.ProtocolExtension;
import com.github._1c_syntax.bsl.languageserver.providers.CallHierarchyProvider;
import com.github._1c_syntax.bsl.languageserver.providers.CodeActionProvider;
import com.github._1c_syntax.bsl.languageserver.providers.CodeLensProvider;
import com.github._1c_syntax.bsl.languageserver.providers.ColorProvider;
import com.github._1c_syntax.bsl.languageserver.providers.DefinitionProvider;
import com.github._1c_syntax.bsl.languageserver.providers.DiagnosticProvider;
import com.github._1c_syntax.bsl.languageserver.providers.DocumentHighlightProvider;
import com.github._1c_syntax.bsl.languageserver.providers.DocumentLinkProvider;
import com.github._1c_syntax.bsl.languageserver.providers.DocumentSymbolProvider;
import com.github._1c_syntax.bsl.languageserver.providers.FoldingRangeProvider;
import com.github._1c_syntax.bsl.languageserver.providers.FormatProvider;
import com.github._1c_syntax.bsl.languageserver.providers.CompletionProvider;
import com.github._1c_syntax.bsl.languageserver.providers.HoverProvider;
import com.github._1c_syntax.bsl.languageserver.providers.ImplementationProvider;
import com.github._1c_syntax.bsl.languageserver.providers.InlayHintProvider;
import com.github._1c_syntax.bsl.languageserver.providers.LinkedEditingRangeProvider;
import com.github._1c_syntax.bsl.languageserver.providers.ReferencesProvider;
import com.github._1c_syntax.bsl.languageserver.providers.RenameProvider;
import com.github._1c_syntax.bsl.languageserver.providers.SelectionRangeProvider;
import com.github._1c_syntax.bsl.languageserver.providers.SemanticTokensProvider;
import com.github._1c_syntax.bsl.languageserver.providers.SignatureHelpProvider;
import com.github._1c_syntax.bsl.languageserver.providers.TypeHierarchyProvider;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.utils.Absolute;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp4j.CallHierarchyIncomingCall;
import org.eclipse.lsp4j.CallHierarchyIncomingCallsParams;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.eclipse.lsp4j.CallHierarchyOutgoingCall;
import org.eclipse.lsp4j.CallHierarchyOutgoingCallsParams;
import org.eclipse.lsp4j.CallHierarchyPrepareParams;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.ColorInformation;
import org.eclipse.lsp4j.ColorPresentation;
import org.eclipse.lsp4j.ColorPresentationParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentColorParams;
import org.eclipse.lsp4j.DocumentDiagnosticParams;
import org.eclipse.lsp4j.DocumentDiagnosticReport;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightParams;
import org.eclipse.lsp4j.DocumentLink;
import org.eclipse.lsp4j.DocumentLinkParams;
import org.eclipse.lsp4j.DocumentOnTypeFormattingParams;
import org.eclipse.lsp4j.DocumentRangeFormattingParams;
import org.eclipse.lsp4j.DocumentRangesFormattingParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeRequestParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.ImplementationParams;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LinkedEditingRangeParams;
import org.eclipse.lsp4j.LinkedEditingRanges;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.PrepareRenameDefaultBehavior;
import org.eclipse.lsp4j.PrepareRenameParams;
import org.eclipse.lsp4j.PrepareRenameResult;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.RelatedFullDocumentDiagnosticReport;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.SelectionRange;
import org.eclipse.lsp4j.SelectionRangeParams;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensDelta;
import org.eclipse.lsp4j.SemanticTokensDeltaParams;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.SemanticTokensRangeParams;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureHelpParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.TypeHierarchyItem;
import org.eclipse.lsp4j.TypeHierarchyPrepareParams;
import org.eclipse.lsp4j.TypeHierarchySubtypesParams;
import org.eclipse.lsp4j.TypeHierarchySupertypesParams;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.Either3;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Сервис обработки запросов, связанных с текстовым документом.
 * <p>
 * Реализует интерфейс {@link TextDocumentService} из LSP4J и обрабатывает
 * все запросы, связанные с открытием, изменением, закрытием документов,
 * а также предоставляет функции навигации, редактирования и анализа кода.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BSLTextDocumentService implements TextDocumentService, ProtocolExtension {

  private static final long AWAIT_CLOSE = 30;
  private static final long AWAIT_FORCE_TERMINATION = 1;

  private final ServerContextProvider serverContextProvider;
  private final DiagnosticProvider diagnosticProvider;
  private final CodeActionProvider codeActionProvider;
  private final CodeLensProvider codeLensProvider;
  private final DocumentLinkProvider documentLinkProvider;
  private final DocumentSymbolProvider documentSymbolProvider;
  private final FoldingRangeProvider foldingRangeProvider;
  private final FormatProvider formatProvider;
  private final HoverProvider hoverProvider;
  private final ImplementationProvider implementationProvider;
  private final CompletionProvider completionProvider;
  private final ReferencesProvider referencesProvider;
  private final DefinitionProvider definitionProvider;
  private final CallHierarchyProvider callHierarchyProvider;
  private final TypeHierarchyProvider typeHierarchyProvider;
  private final SelectionRangeProvider selectionRangeProvider;
  private final ColorProvider colorProvider;
  private final RenameProvider renameProvider;
  private final InlayHintProvider inlayHintProvider;
  private final SemanticTokensProvider semanticTokensProvider;
  private final SignatureHelpProvider signatureHelpProvider;
  private final DocumentHighlightProvider documentHighlightProvider;
  private final LinkedEditingRangeProvider linkedEditingRangeProvider;
  private final LanguageServerConfiguration configuration;

  @Qualifier("textDocumentServiceExecutor")
  private final AsyncTaskExecutor taskExecutor;

  // Executors per document URI to serialize didChange operations and avoid race conditions
  private final Map<URI, DocumentChangeExecutor> documentExecutors = new ConcurrentHashMap<>();

  private boolean clientSupportsPullDiagnostics;

  @PreDestroy
  private void onDestroy() {
    // Shutdown all document executors
    documentExecutors.values().forEach(DocumentChangeExecutor::shutdown);
    documentExecutors.clear();
  }

  @Override
  public CompletableFuture<@Nullable Hover> hover(HoverParams params) {
    return onDocument(params.getTextDocument().getUri(), null,
      documentContext -> hoverProvider.getHover(documentContext, params).orElse(null));
  }

  @Override
  public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams params) {
    return onDocument(params.getTextDocument().getUri(),
      Either.forRight(new CompletionList(false, Collections.emptyList())),
      documentContext -> Either.forRight(completionProvider.getCompletion(documentContext, params)));
  }

  @Override
  public CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem unresolved) {
    var data = completionProvider.extractData(unresolved);
    if (data == null) {
      // item без data — резолвить нечем, возвращаем как есть.
      return CompletableFuture.completedFuture(unresolved);
    }
    // resolveCompletionItem обращается к workspace-scoped typeService/globalScopeProvider,
    // поэтому резолв должен выполняться в workspace-контексте документа.
    return onDocument(data.getUri().toString(), unresolved,
      documentContext -> completionProvider.resolveCompletionItem(unresolved, data));
  }

  @Override
  public CompletableFuture<SignatureHelp> signatureHelp(SignatureHelpParams params) {
    return onDocument(params.getTextDocument().getUri(), new SignatureHelp(Collections.emptyList(), null, null),
      documentContext -> signatureHelpProvider.getSignatureHelp(documentContext, params));
  }

  @Override
  public CompletableFuture<@Nullable List<? extends DocumentHighlight>> documentHighlight(DocumentHighlightParams params) {
    return onDocument(params.getTextDocument().getUri(), Collections.emptyList(),
      documentContext -> documentHighlightProvider.getDocumentHighlight(documentContext, params));
  }

  @Override
  public CompletableFuture<@Nullable LinkedEditingRanges> linkedEditingRange(LinkedEditingRangeParams params) {
    return onDocument(params.getTextDocument().getUri(), null,
      documentContext -> linkedEditingRangeProvider.getLinkedEditingRanges(documentContext, params));
  }

  @Override
  public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(
    DefinitionParams params
  ) {
    return onDocument(params.getTextDocument().getUri(), Either.forRight(Collections.emptyList()),
      documentContext -> definitionProvider.getDefinition(documentContext, params));
  }

  @Override
  public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> implementation(
    ImplementationParams params
  ) {
    return onDocument(params.getTextDocument().getUri(), Either.forLeft(Collections.emptyList()),
      documentContext -> implementationProvider.getImplementations(documentContext, params));
  }

  @Override
  public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
    return onDocument(params.getTextDocument().getUri(), Collections.emptyList(),
      documentContext -> referencesProvider.getReferences(documentContext, params));
  }

  @Override
  public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(
    DocumentSymbolParams params
  ) {
    return onDocument(params.getTextDocument().getUri(), null,
      documentContext -> documentSymbolProvider.getDocumentSymbols(documentContext).stream()
        .map(Either::<SymbolInformation, DocumentSymbol>forRight)
        .toList());
  }

  @Override
  public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params) {
    return onDocument(params.getTextDocument().getUri(), null,
      documentContext -> codeActionProvider.getCodeActions(params, documentContext));
  }

  @Override
  public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params) {
    return onDocument(params.getTextDocument().getUri(), Collections.emptyList(),
      documentContext -> codeLensProvider.getCodeLens(documentContext));
  }

  @Override
  public CompletableFuture<CodeLens> resolveCodeLens(CodeLens unresolved) {
    var data = codeLensProvider.extractData(unresolved);
    if (data == null) {
      // Линза без данных — резолвить нечем, возвращаем как есть.
      return CompletableFuture.completedFuture(unresolved);
    }
    return onDocument(data.getUri().toString(), unresolved,
      documentContext -> codeLensProvider.resolveCodeLens(documentContext, unresolved, data));
  }

  @Override
  public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
    return onDocument(params.getTextDocument().getUri(), null,
      documentContext -> formatProvider.getFormatting(params, documentContext));
  }

  @Override
  public CompletableFuture<List<? extends TextEdit>> rangeFormatting(DocumentRangeFormattingParams params) {
    return onDocument(params.getTextDocument().getUri(), null,
      documentContext -> formatProvider.getRangeFormatting(params, documentContext));
  }

  @Override
  public CompletableFuture<List<? extends TextEdit>> rangesFormatting(DocumentRangesFormattingParams params) {
    return onDocument(params.getTextDocument().getUri(), null,
      documentContext -> formatProvider.getRangesFormatting(params, documentContext));
  }

  @Override
  public CompletableFuture<List<? extends TextEdit>> onTypeFormatting(DocumentOnTypeFormattingParams params) {
    return onDocument(params.getTextDocument().getUri(), Collections.emptyList(),
      documentContext -> formatProvider.getOnTypeFormatting(params, documentContext));
  }

  @Override
  public CompletableFuture<List<FoldingRange>> foldingRange(FoldingRangeRequestParams params) {
    return onDocument(params.getTextDocument().getUri(), null,
      documentContext -> foldingRangeProvider.getFoldingRange(documentContext));
  }

  @Override
  public CompletableFuture<@Nullable List<CallHierarchyItem>> prepareCallHierarchy(CallHierarchyPrepareParams params) {
    // При возврате пустого списка VSCode падает. По протоколу разрешен возврат null.
    return onDocument(params.getTextDocument().getUri(), null, documentContext -> {
      List<CallHierarchyItem> callHierarchyItems = callHierarchyProvider.prepareCallHierarchy(documentContext, params);
      return callHierarchyItems.isEmpty() ? null : callHierarchyItems;
    });
  }

  @Override
  public CompletableFuture<SemanticTokens> semanticTokensFull(SemanticTokensParams params) {
    return onDocument(params.getTextDocument().getUri(), null,
      documentContext -> semanticTokensProvider.getSemanticTokensFull(documentContext, params));
  }

  @Override
  public CompletableFuture<Either<SemanticTokens, SemanticTokensDelta>> semanticTokensFullDelta(
    SemanticTokensDeltaParams params
  ) {
    return onDocument(params.getTextDocument().getUri(), null,
      documentContext -> semanticTokensProvider.getSemanticTokensFullDelta(documentContext, params));
  }

  @Override
  public CompletableFuture<SemanticTokens> semanticTokensRange(SemanticTokensRangeParams params) {
    return onDocument(params.getTextDocument().getUri(), null,
      documentContext -> semanticTokensProvider.getSemanticTokensRange(documentContext, params));
  }

  @Override
  public CompletableFuture<List<CallHierarchyIncomingCall>> callHierarchyIncomingCalls(
    CallHierarchyIncomingCallsParams params
  ) {
    return onDocument(params.getItem().getUri(), Collections.emptyList(),
      documentContext -> callHierarchyProvider.incomingCalls(documentContext, params));
  }

  @Override
  public CompletableFuture<List<CallHierarchyOutgoingCall>> callHierarchyOutgoingCalls(
    CallHierarchyOutgoingCallsParams params
  ) {
    return onDocument(params.getItem().getUri(), Collections.emptyList(),
      documentContext -> callHierarchyProvider.outgoingCalls(documentContext, params));
  }

  @Override
  public CompletableFuture<@Nullable List<TypeHierarchyItem>> prepareTypeHierarchy(TypeHierarchyPrepareParams params) {
    // При возврате пустого списка VSCode падает. По протоколу разрешен возврат null.
    return onDocument(params.getTextDocument().getUri(), null, documentContext -> {
      List<TypeHierarchyItem> typeHierarchyItems = typeHierarchyProvider.prepareTypeHierarchy(documentContext, params);
      return typeHierarchyItems.isEmpty() ? null : typeHierarchyItems;
    });
  }

  @Override
  public CompletableFuture<List<TypeHierarchyItem>> typeHierarchySupertypes(TypeHierarchySupertypesParams params) {
    return onDocument(params.getItem().getUri(), Collections.emptyList(),
      documentContext -> typeHierarchyProvider.supertypes(documentContext, params));
  }

  @Override
  public CompletableFuture<List<TypeHierarchyItem>> typeHierarchySubtypes(TypeHierarchySubtypesParams params) {
    return onDocument(params.getItem().getUri(), Collections.emptyList(),
      documentContext -> typeHierarchyProvider.subtypes(documentContext, params));
  }

  @Override
  public CompletableFuture<List<@Nullable SelectionRange>> selectionRange(SelectionRangeParams params) {
    return onDocument(params.getTextDocument().getUri(), Collections.emptyList(),
      documentContext -> selectionRangeProvider.getSelectionRange(documentContext, params));
  }

  @Override
  public CompletableFuture<List<ColorInformation>> documentColor(DocumentColorParams params) {
    return onDocument(params.getTextDocument().getUri(), Collections.emptyList(),
      documentContext -> colorProvider.getDocumentColor(documentContext));
  }

  @Override
  public CompletableFuture<List<ColorPresentation>> colorPresentation(ColorPresentationParams params) {
    return onDocument(params.getTextDocument().getUri(), Collections.emptyList(),
      documentContext -> colorProvider.getColorPresentation(documentContext, params));
  }

  @Override
  public CompletableFuture<List<InlayHint>> inlayHint(InlayHintParams params) {
    return onDocument(params.getTextDocument().getUri(), Collections.emptyList(),
      documentContext -> inlayHintProvider.getInlayHint(documentContext, params));
  }

  @Override
  public CompletableFuture<InlayHint> resolveInlayHint(InlayHint unresolved) {
    var data = inlayHintProvider.extractData(unresolved);
    if (data == null) {
      // Хинт без данных — резолвить нечем, возвращаем как есть.
      return CompletableFuture.completedFuture(unresolved);
    }
    return onDocument(data.getUri().toString(), unresolved,
      documentContext -> inlayHintProvider.resolveInlayHint(documentContext, unresolved, data));
  }

  @Override
  public void didOpen(DidOpenTextDocumentParams params) {
    var textDocumentItem = params.getTextDocument();
    var uri = Absolute.uri(textDocumentItem.getUri());
    var serverContext = getContextForDocument(textDocumentItem.getUri());
    var lock = serverContext.getDocumentLock(uri);
    lock.writeLock().lock();

    try {
      WorkspaceContextHolder.run(serverContext.getWorkspaceUri(), () -> {
        var documentContext = serverContext.addDocument(uri);

        // Create single-threaded executor for this document to serialize didChange operations
        documentExecutors.computeIfAbsent(uri, key ->
          new DocumentChangeExecutor(
            documentContext,
            BSLTextDocumentService::applyTextDocumentChanges,
            this::processDocumentChange,
            "doc-" + documentContext.getUri() + "-"
          )
        );

        serverContext.openDocument(documentContext, textDocumentItem.getText(), textDocumentItem.getVersion());

        if (configuration.getDiagnosticsOptions().getComputeTrigger() != ComputeTrigger.NEVER) {
          validate(documentContext);
        }
      });
    } finally {
      lock.writeLock().unlock();
    }

  }

  @Override
  public void didChange(DidChangeTextDocumentParams params) {
    var maybeDocument = serverContextProvider.getDocumentUnsafe(params.getTextDocument().getUri());
    if (maybeDocument.isEmpty()) {
      LOGGER.warn("Received didChange, but document is not found in context. uri={}", params.getTextDocument().getUri());
      return;
    }
    var documentContext = maybeDocument.get();

    var uri = documentContext.getUri();
    var serverContext = getContextForDocument(params.getTextDocument().getUri());

    // Acquire read lock to ensure document is not being modified by addDocument/removeDocument
    var lock = serverContext.getDocumentLock(uri);
    lock.readLock().lock();
    try {
      // Get executor for this document
      var executor = documentExecutors.get(uri);
      if (executor == null) {
        // Document not opened or already closed
        LOGGER.warn("Received didChange, but document executor is not created yet. uri={}", uri);
        return;
      }

      var version = params.getTextDocument().getVersion();

      if (version == null) {
        LOGGER.warn("Received didChange without version for {}", uri);
        return;
      }

      // Submit change operation to document's executor to serialize operations.
      executor.submit(version, params.getContentChanges());
    } finally {
      lock.readLock().unlock();
    }
  }

  @Override
  public void didClose(DidCloseTextDocumentParams params) {
    var maybeDocument = serverContextProvider.getDocumentUnsafe(params.getTextDocument().getUri());
    if (maybeDocument.isEmpty()) {
      return;
    }
    var documentContext = maybeDocument.get();

    var uri = documentContext.getUri();
    var serverContext = getContextForDocument(params.getTextDocument().getUri());

    // Remove and shutdown the executor for this document, waiting for all pending changes
    var docExecutor = documentExecutors.remove(uri);
    if (docExecutor != null) {
      docExecutor.shutdown();
      try {
        // Wait for all queued changes to complete (with timeout to avoid hanging)
        if (!docExecutor.awaitTermination(AWAIT_CLOSE, TimeUnit.SECONDS)) {
          docExecutor.shutdownNow();
          // Must wait for worker thread to finish even after shutdownNow,
          // because finally block in worker may still be executing flushPendingChanges
          boolean terminated = docExecutor.awaitTermination(AWAIT_FORCE_TERMINATION, TimeUnit.SECONDS);
          if (!terminated) {
            LOGGER.warn(
              "Document executor for URI {} did not terminate within the additional timeout after shutdownNow()",
              uri
            );
          }
        }
      } catch (InterruptedException e) {
        docExecutor.shutdownNow();
        // Wait briefly for worker to finish after interrupt
        try {
          boolean terminated = docExecutor.awaitTermination(AWAIT_FORCE_TERMINATION, TimeUnit.SECONDS);
          if (!terminated) {
            LOGGER.warn(
              "Document executor for URI {} did not terminate within {} seconds after interrupt during document close",
              uri,
              AWAIT_FORCE_TERMINATION
            );
          }
        } catch (InterruptedException ignored) {
          LOGGER.warn(
            "Interrupted again while waiting for document executor for URI {} to terminate after shutdownNow",
            uri
          );
        }
        Thread.currentThread().interrupt();
      }
    }

    // didClose приходит с потока диспетчеризации LSP4J, на котором workspace-контекст
    // не установлен, — выставляем его явно (как в didOpen), чтобы workspace-scoped
    // @EventListener-подписчики ServerContextDocumentClosedEvent корректно резолвились.
    // Текст освобождается под блокировкой записи: запросы, которые уже читают документ,
    // дорабатывают, а не теряют текст посреди вычисления.
    var lock = serverContext.getDocumentLock(uri);
    lock.writeLock().lock();
    try {
      WorkspaceContextHolder.run(serverContext.getWorkspaceUri(), () -> {
        serverContext.closeDocument(documentContext);
        // Пока документ был открыт, удаление его файла не выгружало документ: им владел
        // клиент. Теперь владельца нет, и документ без файла остался бы в индексах.
        if (isFileDeleted(uri)) {
          serverContext.removeDocument(uri);
        }
      });
    } finally {
      lock.writeLock().unlock();
    }

    if (!clientSupportsPullDiagnostics) {
      diagnosticProvider.publishEmptyDiagnosticList(documentContext);
    }
  }

  /**
   * Удалён ли файл документа с диска.
   *
   * @param uri URI документа.
   * @return {@code true}, если файла по URI точно нет; для URI не из файловой системы
   *     и для пути, который не удалось проверить, — {@code false}.
   */
  private static boolean isFileDeleted(URI uri) {
    if (!"file".equals(uri.getScheme())) {
      return false;
    }
    try {
      return Files.notExists(Path.of(uri));
    } catch (IllegalArgumentException e) {
      // Так бывает с file-URI с authority (UNC) вне Windows: путь по нему не строится.
      LOGGER.debug("Can't check whether file of document exists: {}", uri, e);
      return false;
    }
  }

  @Override
  public void didSave(DidSaveTextDocumentParams params) {
    onDocument(params.getTextDocument().getUri(), null, documentContext -> {
      if (configuration.getDiagnosticsOptions().getComputeTrigger() != ComputeTrigger.NEVER) {
        validate(documentContext);
      }
      return null;
    });
  }

  @Override
  public CompletableFuture<List<DocumentLink>> documentLink(DocumentLinkParams params) {
    return onDocument(params.getTextDocument().getUri(), null,
      documentContext -> documentLinkProvider.getDocumentLinks(documentContext));
  }

  @Override
  public CompletableFuture<Diagnostics> diagnostics(DiagnosticParams params) {
    return onDocument(params.getTextDocument().getUri(), Diagnostics.EMPTY, documentContext -> {
      var diagnostics = documentContext.getDiagnostics();

      var range = params.getRange();
      if (range != null) {
        diagnostics = diagnostics.stream()
          .filter(diagnostic -> Ranges.containsRange(range, diagnostic.getRange()))
          .toList();
      }
      return new Diagnostics(diagnostics, documentContext.getVersion());
    });
  }

  @Override
  public CompletableFuture<DocumentDiagnosticReport> diagnostic(DocumentDiagnosticParams params) {
    return onDocument(params.getTextDocument().getUri(),
      new DocumentDiagnosticReport(new RelatedFullDocumentDiagnosticReport(Collections.emptyList())),
      documentContext -> diagnosticProvider.getDiagnostic(documentContext));
  }

  @Override
  public CompletableFuture<Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>> prepareRename(PrepareRenameParams params) {
    return onDocument(params.getTextDocument().getUri(), null,
      documentContext -> renameProvider.getPrepareRename(documentContext, params));
  }

  @Override
  public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
    return onDocument(params.getTextDocument().getUri(), null,
      documentContext -> renameProvider.getRename(documentContext, params));
  }

  public void reset() {
    serverContextProvider.clear();
  }

  /**
   * Обработчик события {@link LanguageServerInitializedEvent}.
   * <p>
   * Кэширует поддержку клиентом pull-модели диагностик, влияющую на способ публикации
   * диагностик при закрытии документа.
   *
   * @param ignored Событие
   */
  @EventListener
  public void handleInitializeEvent(LanguageServerInitializedEvent ignored) {
    clientSupportsPullDiagnostics = diagnosticProvider.supportsPullDiagnostics();
  }

  /**
   * Останавливает executor для удалённого документа, чтобы не оставлять stale-reference
   * на старый {@link DocumentContext} в карте {@link #documentExecutors} — иначе следующий
   * {@code didOpen} того же URI получит из {@code computeIfAbsent} executor с прежним
   * {@code DocumentContext}, и {@code didChange} применит изменения к чужому документу.
   */
  @EventListener
  public void onDocumentRemoved(ServerContextDocumentRemovedEvent event) {
    var docExecutor = documentExecutors.remove(event.getUri());
    if (docExecutor != null) {
      docExecutor.shutdown();
    }
  }

  private void validate(DocumentContext documentContext) {
    if (clientSupportsPullDiagnostics) {
      return;
    }
    diagnosticProvider.computeAndPublishDiagnostics(documentContext);
  }

  /**
   * Применяет список изменений текста к исходному содержимому документа.
   * Поддерживает как полные обновления (без range), так и инкрементальные изменения (с range).
   *
   * @param content текущее содержимое документа
   * @param changes список изменений для применения
   * @return обновленное содержимое документа
   */
  protected static String applyTextDocumentChanges(String content, List<TextDocumentContentChangeEvent> changes) {
    var currentContent = content;
    for (var change : changes) {
      if (change.getRange() == null) {
        // Full document update
        currentContent = change.getText();
      } else {
        // Incremental update
        currentContent = applyIncrementalChange(currentContent, change);
      }
    }
    return currentContent;
  }

  /**
   * Применяет одно инкрементальное изменение к содержимому документа.
   * Использует прямую замену по позициям символов для оптимизации и сохранения оригинальных переносов строк.
   *
   * @param content текущее содержимое документа
   * @param change изменение для применения
   * @return обновленное содержимое документа
   */
  protected static String applyIncrementalChange(String content, TextDocumentContentChangeEvent change) {
    var range = change.getRange();
    var newText = change.getText();

    var startLine = range.getStart().getLine();
    var startChar = range.getStart().getCharacter();
    var endLine = range.getEnd().getLine();
    var endChar = range.getEnd().getCharacter();

    // Convert line/character positions to absolute character offsets
    int startOffset = getOffset(content, startLine, startChar);
    int endOffset = getOffset(content, endLine, endChar);

    // Use StringBuilder with pre-calculated capacity to avoid intermediate allocations
    int newLength = startOffset + newText.length() + (content.length() - endOffset);
    var sb = new StringBuilder(newLength);
    sb.append(content, 0, startOffset);
    sb.append(newText);
    sb.append(content, endOffset, content.length());
    return sb.toString();
  }

  /**
   * Вычисляет абсолютную позицию символа в тексте по номеру строки и позиции в строке.
   * Использует однопроходное сканирование символов для быстрого поиска позиции.
   *
   * @param content содержимое документа
   * @param line номер строки (0-based)
   * @param character позиция символа в строке (0-based)
   * @return абсолютная позиция символа в тексте
   */
  protected static int getOffset(String content, int line, int character) {
    var contentLength = content.length();

    if (line == 0) {
      return Math.min(character, contentLength);
    }

    var currentLine = 0;

    for (var i = 0; i < contentLength && currentLine < line; i++) {
      var c = content.charAt(i);
      if (c == '\n') {
        currentLine++;
        if (currentLine == line) {
          // Next line starts at i+1, add character offset
          return Math.min(i + 1 + character, contentLength);
        }
      } else if (c == '\r') {
        currentLine++;
        // Handle \r\n as a single line ending - skip the \n
        if (i + 1 < contentLength && content.charAt(i + 1) == '\n') {
          i++;
        }
        if (currentLine == line) {
          // Next line starts at i+1 (after \r or \r\n), add character offset
          return Math.min(i + 1 + character, contentLength);
        }
      }
    }

    // Fallback: requested line beyond content, return end of content
    return contentLength;
  }

  private void processDocumentChange(
    DocumentContext documentContext,
    String newContent,
    Integer version
  ) {
    var serverContext = getContextForDocument(documentContext.getUri().toString());
    serverContext.rebuildDocument(
      documentContext,
      newContent,
      version
    );

    if (configuration.getDiagnosticsOptions().getComputeTrigger() == ComputeTrigger.ONTYPE) {
      validate(documentContext);
    }
  }

  /**
   * Обработать запрос к документу: найти его по URI, дождаться применения его правок и выполнить
   * обработку (см. {@link #withFreshDocumentContext}).
   *
   * @param uri       URI документа из параметров запроса
   * @param ifMissing ответ, если документ серверу неизвестен
   * @param request   обработка запроса над найденным документом
   * @param <T>       тип ответа
   * @return ответ на запрос
   */
  private <T extends @Nullable Object> CompletableFuture<T> onDocument(
    String uri,
    T ifMissing,
    Function<DocumentContext, T> request
  ) {
    var maybeDocument = serverContextProvider.getDocumentUnsafe(uri);
    if (maybeDocument.isEmpty()) {
      return CompletableFuture.completedFuture(ifMissing);
    }
    var documentContext = maybeDocument.get();
    return withFreshDocumentContext(documentContext, () -> request.apply(documentContext));
  }

  /**
   * Выполнить обработку над актуальным состоянием документа: после применения его отложенных
   * правок, под блокировкой чтения документа и в контексте его рабочей области.
   */
  private <T extends @Nullable Object> CompletableFuture<T> withFreshDocumentContext(
    DocumentContext documentContext,
    Supplier<T> supplier
  ) {
    var executor = documentExecutors.get(documentContext.getUri());
    CompletableFuture<Void> waitFuture;
    if (executor != null) {
      waitFuture = executor.awaitLatest();
    } else {
      waitFuture = CompletableFuture.completedFuture(null);
    }

    return waitFuture.thenCompose(ignored ->
      CompletableFutures.computeAsync(
        taskExecutor,
        cancelChecker -> {
          cancelChecker.checkCanceled();
          var serverContext = getContextForDocument(documentContext.getUri().toString());
          var lock = serverContext.getDocumentLock(documentContext.getUri());
          lock.readLock().lock();
          try (var workspaceContext = WorkspaceContextHolder.forUri(serverContext.getWorkspaceUri())) {
            return supplier.get();
          } catch (DocumentContentUnavailableException e) {
            // Посреди вычисления текст не пропадёт: didClose освобождает его под блокировкой
            // записи. Но запрос мог начаться уже после закрытия документа, а ему нужен текст.
            // Запросам, которым текст не нужен (иерархии по закрытым файлам), это не мешает.
            throw toResponseError(serverContext, documentContext, e);
          } finally {
            lock.readLock().unlock();
          }
        }
      )
    );
  }

  /**
   * Ответ клиенту на запрос, которому понадобился текст документа, а текста нет.
   *
   * @param serverContext   контекст сервера документа.
   * @param documentContext документ запроса.
   * @param e               обращение к тексту документа без текста.
   * @return {@link ResponseErrorException} с кодом {@link ResponseErrorCode#ContentModified}, если
   *     текста нет у документа запроса и тот закрыт или не открывался; иначе исходная ошибка —
   *     текста нет у другого документа либо документ числится с текстом.
   */
  private static RuntimeException toResponseError(
    ServerContext serverContext,
    DocumentContext documentContext,
    DocumentContentUnavailableException e
  ) {
    if (!e.getUri().equals(documentContext.getUri())
      || serverContext.getDocumentState(documentContext) == DocumentState.WITH_CONTENT) {
      return e;
    }
    LOGGER.debug("Request to document without content: {}", e.getUri());
    return new ResponseErrorException(new ResponseError(
      ResponseErrorCode.ContentModified,
      e.getMessage(),
      null
    ));
  }

  /**
   * Получить контекст сервера для документа.
   *
   * @param uriString строковое представление URI документа
   * @return контекст сервера
   */
  private ServerContext getContextForDocument(String uriString) {
    var uri = Absolute.uri(uriString);
    return serverContextProvider.resolveContextForDocument(uri);
  }
}
