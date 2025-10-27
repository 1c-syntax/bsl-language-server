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
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
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
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.Either3;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Сервис обработки запросов, связанных с текстовым документом.
 * <p>
 * Реализует интерфейс {@link TextDocumentService} из LSP4J и обрабатывает
 * все запросы, связанные с открытием, изменением, закрытием документов,
 * а также предоставляет функции навигации, редактирования и анализа кода.
 */
@Component
@RequiredArgsConstructor
public class BSLTextDocumentService implements TextDocumentService, ProtocolExtension {

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

  private final ExecutorService executorService = Executors.newCachedThreadPool(new CustomizableThreadFactory("text-document-service-"));
  
  private boolean clientSupportsPullDiagnostics;

  @PreDestroy
  private void onDestroy() {
    executorService.shutdown();
  }

  @Override
  public CompletableFuture<Hover> hover(HoverParams params) {
    var documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(null);
    }
    return CompletableFuture.supplyAsync(
      () -> hoverProvider.getHover(documentContext, params).orElse(null),
      executorService
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

    return CompletableFuture.supplyAsync(
      () -> Either.forRight(definitionProvider.getDefinition(documentContext, params)),
      executorService
    );
  }

  @Override
  public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
    var documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(Collections.emptyList());
    }

    return CompletableFuture.supplyAsync(
      () -> referencesProvider.getReferences(documentContext, params),
      executorService
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

    return CompletableFuture.supplyAsync(
      () -> documentSymbolProvider.getDocumentSymbols(documentContext).stream()
        .map(Either::<SymbolInformation, DocumentSymbol>forRight)
        .toList(),
      executorService
    );
  }

  @Override
  public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params) {
    var documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(null);
    }

    return CompletableFuture.supplyAsync(
      () -> codeActionProvider.getCodeActions(params, documentContext),
      executorService
    );
  }

  @Override
  public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params) {
    var documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(Collections.emptyList());
    }

    return CompletableFuture.supplyAsync(
      () -> codeLensProvider.getCodeLens(documentContext),
      executorService
    );
  }

  @Override
  public CompletableFuture<CodeLens> resolveCodeLens(CodeLens unresolved) {
    var data = codeLensProvider.extractData(unresolved);
    var documentContext = context.getDocument(data.getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(unresolved);
    }
    return CompletableFuture.supplyAsync(
      () -> codeLensProvider.resolveCodeLens(documentContext, unresolved, data),
      executorService
    );
  }

  @Override
  public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
    var documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(null);
    }

    return CompletableFuture.supplyAsync(
      () -> formatProvider.getFormatting(params, documentContext),
      executorService
    );
  }

  @Override
  public CompletableFuture<List<? extends TextEdit>> rangeFormatting(DocumentRangeFormattingParams params) {
    var documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(null);
    }

    return CompletableFuture.supplyAsync(
      () -> formatProvider.getRangeFormatting(params, documentContext),
      executorService
    );
  }

  @Override
  public CompletableFuture<List<FoldingRange>> foldingRange(FoldingRangeRequestParams params) {
    var documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(null);
    }

    return CompletableFuture.supplyAsync(
      () -> foldingRangeProvider.getFoldingRange(documentContext),
      executorService
    );
  }

  @Override
  public CompletableFuture<List<CallHierarchyItem>> prepareCallHierarchy(CallHierarchyPrepareParams params) {
    // При возврате пустого списка VSCode падает. По протоколу разрешен возврат null.
    var documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(null);
    }

    return CompletableFuture.supplyAsync(
      () -> {
        List<CallHierarchyItem> callHierarchyItems = callHierarchyProvider.prepareCallHierarchy(documentContext, params);
        if (callHierarchyItems.isEmpty()) {
          return null;
        }
        return callHierarchyItems;
      },
      executorService
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

    return CompletableFuture.supplyAsync(
      () -> callHierarchyProvider.incomingCalls(documentContext, params),
      executorService
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

    return CompletableFuture.supplyAsync(
      () -> callHierarchyProvider.outgoingCalls(documentContext, params),
      executorService
    );
  }

  @Override
  public CompletableFuture<List<SelectionRange>> selectionRange(SelectionRangeParams params) {
    var documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(Collections.emptyList());
    }

    return CompletableFuture.supplyAsync(
      () -> selectionRangeProvider.getSelectionRange(documentContext, params),
      executorService
    );
  }

  @Override
  public CompletableFuture<List<ColorInformation>> documentColor(DocumentColorParams params) {
    var documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(Collections.emptyList());
    }

    return CompletableFuture.supplyAsync(
      () -> colorProvider.getDocumentColor(documentContext),
      executorService
    );
  }

  @Override
  public CompletableFuture<List<ColorPresentation>> colorPresentation(ColorPresentationParams params) {
    var documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(Collections.emptyList());
    }

    return CompletableFuture.supplyAsync(
      () -> colorProvider.getColorPresentation(documentContext, params),
      executorService
    );
  }

  @Override
  public CompletableFuture<List<InlayHint>> inlayHint(InlayHintParams params) {
    var documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(Collections.emptyList());
    }

    return CompletableFuture.supplyAsync(
      () -> inlayHintProvider.getInlayHint(documentContext, params),
      executorService
    );
  }

  @Override
  public void didOpen(DidOpenTextDocumentParams params) {
    var textDocumentItem = params.getTextDocument();
    var documentContext = context.addDocument(URI.create(textDocumentItem.getUri()));

    context.openDocument(documentContext, textDocumentItem.getText(), textDocumentItem.getVersion());

    if (configuration.getDiagnosticsOptions().getComputeTrigger() != ComputeTrigger.NEVER) {
      validate(documentContext);
    }
  }

  @Override
  public void didChange(DidChangeTextDocumentParams params) {

    // TODO: Place to optimize -> migrate to #TextDocumentSyncKind.INCREMENTAL and build changed parse tree
    var documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return;
    }

    context.rebuildDocument(
      documentContext,
      params.getContentChanges().get(0).getText(),
      params.getTextDocument().getVersion()
    );

    if (configuration.getDiagnosticsOptions().getComputeTrigger() == ComputeTrigger.ONTYPE) {
      validate(documentContext);
    }
  }

  @Override
  public void didClose(DidCloseTextDocumentParams params) {
    var documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return;
    }

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

    return CompletableFuture.supplyAsync(
      () -> documentLinkProvider.getDocumentLinks(documentContext),
      executorService
    );
  }

  @Override
  public CompletableFuture<Diagnostics> diagnostics(DiagnosticParams params) {
    var documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(Diagnostics.EMPTY);
    }

    return CompletableFuture.supplyAsync(() -> {
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
    var documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(
        new DocumentDiagnosticReport(new RelatedFullDocumentDiagnosticReport(Collections.emptyList()))
      );
    }
    
    return CompletableFuture.supplyAsync(
      () -> diagnosticProvider.getDiagnostic(documentContext),
      executorService
    );
  }

  @Override
  public CompletableFuture<Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>> prepareRename(PrepareRenameParams params) {
    var documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(null);
    }

    return CompletableFuture.supplyAsync(
      () -> Either3.forFirst(renameProvider.getPrepareRename(documentContext, params)),
      executorService
    );
  }

  @Override
  public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
    var documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(null);
    }

    return CompletableFuture.supplyAsync(
      () -> renameProvider.getRename(documentContext, params),
      executorService
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
   * @param event Событие
   */
  @EventListener
  public void handleInitializeEvent(LanguageServerInitializeRequestReceivedEvent event) {
    var capabilities = event.getParams().getCapabilities();
    if (capabilities != null && capabilities.getTextDocument() != null) {
      clientSupportsPullDiagnostics = capabilities.getTextDocument().getDiagnostic() != null;
    }
  }

  private void validate(DocumentContext documentContext) {
    if (clientSupportsPullDiagnostics) {
      return;
    }
    diagnosticProvider.computeAndPublishDiagnostics(documentContext);
  }

}
