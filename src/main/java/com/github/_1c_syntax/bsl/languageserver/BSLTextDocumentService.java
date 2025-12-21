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

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.configuration.diagnostics.ComputeTrigger;
import com.github._1c_syntax.bsl.languageserver.context.DocumentChangeExecutor;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.events.LanguageServerInitializeRequestReceivedEvent;
import com.github._1c_syntax.bsl.languageserver.jsonrpc.DiagnosticParams;
import com.github._1c_syntax.bsl.languageserver.jsonrpc.Diagnostics;
import com.github._1c_syntax.bsl.languageserver.jsonrpc.ProtocolExtension;
import com.github._1c_syntax.bsl.languageserver.providers.CallHierarchyProvider;
import com.github._1c_syntax.bsl.languageserver.providers.CodeActionProvider;
import com.github._1c_syntax.bsl.languageserver.providers.CodeLensProvider;
import com.github._1c_syntax.bsl.languageserver.providers.ColorProvider;
import com.github._1c_syntax.bsl.languageserver.providers.DefinitionProvider;
import com.github._1c_syntax.bsl.languageserver.providers.DiagnosticProvider;
import com.github._1c_syntax.bsl.languageserver.providers.DocumentLinkProvider;
import com.github._1c_syntax.bsl.languageserver.providers.DocumentSymbolProvider;
import com.github._1c_syntax.bsl.languageserver.providers.FoldingRangeProvider;
import com.github._1c_syntax.bsl.languageserver.providers.FormatProvider;
import com.github._1c_syntax.bsl.languageserver.providers.HoverProvider;
import com.github._1c_syntax.bsl.languageserver.providers.InlayHintProvider;
import com.github._1c_syntax.bsl.languageserver.providers.ReferencesProvider;
import com.github._1c_syntax.bsl.languageserver.providers.RenameProvider;
import com.github._1c_syntax.bsl.languageserver.providers.SelectionRangeProvider;
import com.github._1c_syntax.bsl.languageserver.providers.SemanticTokensProvider;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp4j.CallHierarchyIncomingCall;
import org.eclipse.lsp4j.CallHierarchyIncomingCallsParams;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.eclipse.lsp4j.CallHierarchyOutgoingCall;
import org.eclipse.lsp4j.CallHierarchyOutgoingCallsParams;
import org.eclipse.lsp4j.CallHierarchyPrepareParams;
import org.eclipse.lsp4j.ClientCapabilities;
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
import org.eclipse.lsp4j.DocumentLink;
import org.eclipse.lsp4j.DocumentLinkParams;
import org.eclipse.lsp4j.DocumentRangeFormattingParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeRequestParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.Location;
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
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.Either3;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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

  private final ServerContext context;
  private final LanguageServerConfiguration configuration;
  private final DiagnosticProvider diagnosticProvider;
  private final CodeActionProvider codeActionProvider;
  private final CodeLensProvider codeLensProvider;
  private final DocumentLinkProvider documentLinkProvider;
  private final DocumentSymbolProvider documentSymbolProvider;
  private final FoldingRangeProvider foldingRangeProvider;
  private final FormatProvider formatProvider;
  private final HoverProvider hoverProvider;
  private final ReferencesProvider referencesProvider;
  private final DefinitionProvider definitionProvider;
  private final CallHierarchyProvider callHierarchyProvider;
  private final SelectionRangeProvider selectionRangeProvider;
  private final ColorProvider colorProvider;
  private final RenameProvider renameProvider;
  private final InlayHintProvider inlayHintProvider;
  private final ClientCapabilitiesHolder clientCapabilitiesHolder;
  private final SemanticTokensProvider semanticTokensProvider;

  private final ExecutorService executorService = Executors.newCachedThreadPool(new CustomizableThreadFactory("text-document-service-"));

  // Executors per document URI to serialize didChange operations and avoid race conditions
  private final Map<URI, DocumentChangeExecutor> documentExecutors = new ConcurrentHashMap<>();

  private boolean clientSupportsPullDiagnostics;

  @PreDestroy
  private void onDestroy() {
    // Shutdown all document executors
    documentExecutors.values().forEach(DocumentChangeExecutor::shutdown);
    documentExecutors.clear();

    executorService.shutdown();
  }

  @Override
  public CompletableFuture<@Nullable Hover> hover(HoverParams params) {
    var documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(null);
    }

    return withFreshDocumentContext(
      documentContext,
      () -> hoverProvider.getHover(documentContext, params).orElse(null)
    );
  }

  @Override
  public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(
    DefinitionParams params
  ) {
    var documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(Either.forRight(Collections.emptyList()));
    }

    return withFreshDocumentContext(
      documentContext,
      () -> Either.forRight(definitionProvider.getDefinition(documentContext, params))
    );
  }

  @Override
  public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
    var documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(Collections.emptyList());
    }

    return withFreshDocumentContext(
      documentContext,
      () -> referencesProvider.getReferences(documentContext, params)
    );
  }

  @Override
  public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(
    DocumentSymbolParams params
  ) {
    var documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(null);
    }

    return withFreshDocumentContext(
      documentContext,
      () -> documentSymbolProvider.getDocumentSymbols(documentContext).stream()
        .map(Either::<SymbolInformation, DocumentSymbol>forRight)
        .toList()
    );
  }

  @Override
  public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params) {
    var documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(null);
    }

    return withFreshDocumentContext(
      documentContext,
      () -> codeActionProvider.getCodeActions(params, documentContext)
    );
  }

  @Override
  public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params) {
    var documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(Collections.emptyList());
    }

    return withFreshDocumentContext(
      documentContext,
      () -> codeLensProvider.getCodeLens(documentContext)
    );
  }

  @Override
  public CompletableFuture<CodeLens> resolveCodeLens(CodeLens unresolved) {
    var data = codeLensProvider.extractData(unresolved);
    var documentContext = context.getDocument(data.getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(unresolved);
    }
    return withFreshDocumentContext(
      documentContext,
      () -> codeLensProvider.resolveCodeLens(documentContext, unresolved, data)
    );
  }

  @Override
  public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
    var documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(null);
    }

    return withFreshDocumentContext(
      documentContext,
      () -> formatProvider.getFormatting(params, documentContext)
    );
  }

  @Override
  public CompletableFuture<List<? extends TextEdit>> rangeFormatting(DocumentRangeFormattingParams params) {
    var documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(null);
    }

    return withFreshDocumentContext(
      documentContext,
      () -> formatProvider.getRangeFormatting(params, documentContext)
    );
  }

  @Override
  public CompletableFuture<List<FoldingRange>> foldingRange(FoldingRangeRequestParams params) {
    var documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(null);
    }

    return withFreshDocumentContext(
      documentContext,
      () -> foldingRangeProvider.getFoldingRange(documentContext)
    );
  }

  @Override
  public CompletableFuture<@Nullable List<CallHierarchyItem>> prepareCallHierarchy(CallHierarchyPrepareParams params) {
    // При возврате пустого списка VSCode падает. По протоколу разрешен возврат null.
    var documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(null);
    }

    return withFreshDocumentContext(
      documentContext,
      () -> {
        List<CallHierarchyItem> callHierarchyItems = callHierarchyProvider.prepareCallHierarchy(documentContext, params);
        if (callHierarchyItems.isEmpty()) {
          return null;
        }
        return callHierarchyItems;
      }
    );
  }

  @Override
  public CompletableFuture<SemanticTokens> semanticTokensFull(SemanticTokensParams params) {
    var documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(null);
    }

    return withFreshDocumentContext(
      documentContext,
      () -> semanticTokensProvider.getSemanticTokensFull(documentContext, params)
    );
  }

  @Override
  public CompletableFuture<Either<SemanticTokens, SemanticTokensDelta>> semanticTokensFullDelta(
    SemanticTokensDeltaParams params
  ) {
    var documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(null);
    }

    return withFreshDocumentContext(
      documentContext,
      () -> semanticTokensProvider.getSemanticTokensFullDelta(documentContext, params)
    );
  }

  @Override
  public CompletableFuture<List<CallHierarchyIncomingCall>> callHierarchyIncomingCalls(
    CallHierarchyIncomingCallsParams params
  ) {
    var documentContext = context.getDocument(params.getItem().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(Collections.emptyList());
    }

    return withFreshDocumentContext(
      documentContext,
      () -> callHierarchyProvider.incomingCalls(documentContext, params)
    );
  }

  @Override
  public CompletableFuture<List<CallHierarchyOutgoingCall>> callHierarchyOutgoingCalls(
    CallHierarchyOutgoingCallsParams params
  ) {
    var documentContext = context.getDocument(params.getItem().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(Collections.emptyList());
    }

    return withFreshDocumentContext(
      documentContext,
      () -> callHierarchyProvider.outgoingCalls(documentContext, params)
    );
  }

  @Override
  public CompletableFuture<List<@Nullable SelectionRange>> selectionRange(SelectionRangeParams params) {
    var documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(Collections.emptyList());
    }

    return withFreshDocumentContext(
      documentContext,
      () -> selectionRangeProvider.getSelectionRange(documentContext, params)
    );
  }

  @Override
  public CompletableFuture<List<ColorInformation>> documentColor(DocumentColorParams params) {
    var documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(Collections.emptyList());
    }

    return withFreshDocumentContext(
      documentContext,
      () -> colorProvider.getDocumentColor(documentContext)
    );
  }

  @Override
  public CompletableFuture<List<ColorPresentation>> colorPresentation(ColorPresentationParams params) {
    var documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(Collections.emptyList());
    }

    return withFreshDocumentContext(
      documentContext,
      () -> colorProvider.getColorPresentation(documentContext, params)
    );
  }

  @Override
  public CompletableFuture<List<InlayHint>> inlayHint(InlayHintParams params) {
    var documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(Collections.emptyList());
    }

    return withFreshDocumentContext(
      documentContext,
      () -> inlayHintProvider.getInlayHint(documentContext, params)
    );
  }

  @Override
  public void didOpen(DidOpenTextDocumentParams params) {
    var textDocumentItem = params.getTextDocument();
    var documentContext = context.addDocument(URI.create(textDocumentItem.getUri()));

    // Create single-threaded executor for this document to serialize didChange operations
    var uri = documentContext.getUri();
    documentExecutors.computeIfAbsent(uri, key ->
      new DocumentChangeExecutor(
        documentContext,
        BSLTextDocumentService::applyTextDocumentChanges,
        this::processDocumentChange,
        "doc-" + documentContext.getUri() + "-"
      )
    );

    context.openDocument(documentContext, textDocumentItem.getText(), textDocumentItem.getVersion());

    if (configuration.getDiagnosticsOptions().getComputeTrigger() != ComputeTrigger.NEVER) {
      validate(documentContext);
    }
  }

  @Override
  public void didChange(DidChangeTextDocumentParams params) {
    var documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return;
    }

    var uri = documentContext.getUri();

    // Get executor for this document
    var executor = documentExecutors.get(uri);
    if (executor == null) {
      // Document not opened or already closed
      return;
    }

    var version = params.getTextDocument().getVersion();

    if (version == null) {
      LOGGER.warn("Received didChange without version for {}", uri);
      return;
    }

    // Submit change operation to document's executor to serialize operations.
    executor.submit(version, params.getContentChanges());
  }

  @Override
  public void didClose(DidCloseTextDocumentParams params) {
    var documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return;
    }

    var uri = documentContext.getUri();

    // Remove and shutdown the executor for this document, waiting for all pending changes
    var docExecutor = documentExecutors.remove(uri);
    if (docExecutor != null) {
      docExecutor.shutdown();
      try {
        // Wait for all queued changes to complete (with timeout to avoid hanging)
        if (!docExecutor.awaitTermination(AWAIT_CLOSE, TimeUnit.SECONDS)) {
          docExecutor.shutdownNow();
        }
      } catch (InterruptedException e) {
        docExecutor.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }

    // Clear cached semantic tokens for this document
    semanticTokensProvider.clearCache(uri);

    context.closeDocument(documentContext);

    diagnosticProvider.publishEmptyDiagnosticList(documentContext);
  }

  @Override
  public void didSave(DidSaveTextDocumentParams params) {
    var documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return;
    }

    if (configuration.getDiagnosticsOptions().getComputeTrigger() != ComputeTrigger.NEVER) {
      validate(documentContext);
    }
  }

  @Override
  public CompletableFuture<List<DocumentLink>> documentLink(DocumentLinkParams params) {
    var documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(null);
    }

    return withFreshDocumentContext(
      documentContext,
      () -> documentLinkProvider.getDocumentLinks(documentContext)
    );
  }

  @Override
  public CompletableFuture<Diagnostics> diagnostics(DiagnosticParams params) {
    var documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(Diagnostics.EMPTY);
    }

    return withFreshDocumentContext(
      documentContext,
      () -> {
        var diagnostics = documentContext.getDiagnostics();

        var range = params.getRange();
        if (range != null) {
          diagnostics = diagnostics.stream()
            .filter(diagnostic -> Ranges.containsRange(range, diagnostic.getRange()))
            .toList();
        }
        return new Diagnostics(diagnostics, documentContext.getVersion());
      }
    );
  }

  @Override
  public CompletableFuture<DocumentDiagnosticReport> diagnostic(DocumentDiagnosticParams params) {
    var documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(
        new DocumentDiagnosticReport(new RelatedFullDocumentDiagnosticReport(Collections.emptyList()))
      );
    }

    return withFreshDocumentContext(
      documentContext,
      () -> diagnosticProvider.getDiagnostic(documentContext)
    );
  }

  @Override
  public CompletableFuture<Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>> prepareRename(PrepareRenameParams params) {
    var documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(null);
    }

    return withFreshDocumentContext(
      documentContext,
      () -> Either3.forFirst(renameProvider.getPrepareRename(documentContext, params))
    );
  }

  @Override
  public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
    var documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(null);
    }

    return withFreshDocumentContext(
      documentContext,
      () -> renameProvider.getRename(documentContext, params)
    );
  }

  public void reset() {
    context.clear();
  }

  /**
   * Обработчик события {@link LanguageServerInitializeRequestReceivedEvent}.
   * <p>
   * Проверяет поддержку клиентом pull-модели диагностик.
   *
   * @param ignored Событие
   */
  @EventListener
  public void handleInitializeEvent(LanguageServerInitializeRequestReceivedEvent ignored) {
    clientSupportsPullDiagnostics = clientCapabilitiesHolder.getCapabilities()
      .map(ClientCapabilities::getTextDocument)
      .map(TextDocumentClientCapabilities::getDiagnostic)
      .isPresent();
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

    // Perform direct string replacement to preserve original line endings
    return content.substring(0, startOffset) + newText + content.substring(endOffset);
  }

  /**
   * Вычисляет абсолютную позицию символа в тексте по номеру строки и позиции в строке.
   * Использует indexOf для быстрого поиска переносов строк.
   *
   * @param content содержимое документа
   * @param line номер строки (0-based)
   * @param character позиция символа в строке (0-based)
   * @return абсолютная позиция символа в тексте
   */
  protected static int getOffset(String content, int line, int character) {
    if (line == 0) {
      return character;
    }

    var offset = 0;
    var currentLine = 0;
    var searchFrom = 0;

    while (currentLine < line) {
      int nlPos = content.indexOf('\n', searchFrom);
      int crPos = content.indexOf('\r', searchFrom);

      if (nlPos == -1 && crPos == -1) {
        // No more line breaks found
        break;
      }

      int nextLineBreak;
      if (nlPos == -1) {
        nextLineBreak = crPos;
      } else if (crPos == -1) {
        nextLineBreak = nlPos;
      } else {
        nextLineBreak = Math.min(nlPos, crPos);
      }

      currentLine++;

      // Handle \r\n as a single line ending
      if (content.charAt(nextLineBreak) == '\r'
          && nextLineBreak + 1 < content.length()
          && content.charAt(nextLineBreak + 1) == '\n') {
        offset = nextLineBreak + 2;
        searchFrom = nextLineBreak + 2;
      } else {
        offset = nextLineBreak + 1;
        searchFrom = nextLineBreak + 1;
      }
    }

    return offset + character;
  }

  private void processDocumentChange(
    DocumentContext documentContext,
    String newContent,
    Integer version
  ) {
    context.rebuildDocument(
      documentContext,
      newContent,
      version
    );

    if (configuration.getDiagnosticsOptions().getComputeTrigger() == ComputeTrigger.ONTYPE) {
      validate(documentContext);
    }
  }

  private <T> CompletableFuture<@Nullable T> withFreshDocumentContext(
    DocumentContext documentContext,
    Supplier<@Nullable T> supplier
  ) {
    var executor = documentExecutors.get(documentContext.getUri());
    CompletableFuture<Void> waitFuture;
    if (executor != null) {
      waitFuture = executor.awaitLatest();
    } else {
      waitFuture = CompletableFuture.completedFuture(null);
    }

    return waitFuture.thenApplyAsync(
      ignored -> supplier.get(),
      executorService
    );
  }
}
