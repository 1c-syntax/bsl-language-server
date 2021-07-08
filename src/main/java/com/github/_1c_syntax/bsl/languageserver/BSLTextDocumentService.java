/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2021
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
package com.github._1c_syntax.bsl.languageserver;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.configuration.diagnostics.ComputeTrigger;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.jsonrpc.DiagnosticParams;
import com.github._1c_syntax.bsl.languageserver.jsonrpc.Diagnostics;
import com.github._1c_syntax.bsl.languageserver.jsonrpc.ProtocolExtension;
import com.github._1c_syntax.bsl.languageserver.providers.CallHierarchyProvider;
import com.github._1c_syntax.bsl.languageserver.providers.CodeActionProvider;
import com.github._1c_syntax.bsl.languageserver.providers.CodeLensProvider;
import com.github._1c_syntax.bsl.languageserver.providers.DefinitionProvider;
import com.github._1c_syntax.bsl.languageserver.providers.DiagnosticProvider;
import com.github._1c_syntax.bsl.languageserver.providers.DocumentLinkProvider;
import com.github._1c_syntax.bsl.languageserver.providers.DocumentSymbolProvider;
import com.github._1c_syntax.bsl.languageserver.providers.FoldingRangeProvider;
import com.github._1c_syntax.bsl.languageserver.providers.FormatProvider;
import com.github._1c_syntax.bsl.languageserver.providers.HoverProvider;
import com.github._1c_syntax.bsl.languageserver.providers.ReferencesProvider;
import com.github._1c_syntax.bsl.languageserver.providers.SelectionRangeProvider;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
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
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
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
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.SelectionRange;
import org.eclipse.lsp4j.SelectionRangeParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

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

  @Override
  public CompletableFuture<Hover> hover(HoverParams params) {
    DocumentContext documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(null);
    }
    return CompletableFuture.supplyAsync(() ->
      hoverProvider.getHover(documentContext, params).orElse(null)
    );
  }

  @Override
  public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(
    DefinitionParams params
  ) {
    DocumentContext documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(Either.forRight(Collections.emptyList()));
    }

    return CompletableFuture.supplyAsync(() ->
      Either.forRight(definitionProvider.getDefinition(documentContext, params))
    );
  }

  @Override
  public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
    DocumentContext documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(Collections.emptyList());
    }

    return CompletableFuture.supplyAsync(() -> referencesProvider.getReferences(documentContext, params));
  }

  @Override
  public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(
    DocumentSymbolParams params
  ) {
    DocumentContext documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(null);
    }

    return CompletableFuture.supplyAsync(() -> documentSymbolProvider.getDocumentSymbols(documentContext));
  }

  @Override
  public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params) {
    DocumentContext documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(null);
    }

    return CompletableFuture.supplyAsync(() -> codeActionProvider.getCodeActions(params, documentContext));
  }

  @Override
  public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params) {
    DocumentContext documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(null);
    }

    return CompletableFuture.supplyAsync(() -> codeLensProvider.getCodeLens(documentContext));
  }

  @Override
  public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
    DocumentContext documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(null);
    }

    List<TextEdit> edits = formatProvider.getFormatting(params, documentContext);
    return CompletableFuture.completedFuture(edits);
  }

  @Override
  public CompletableFuture<List<? extends TextEdit>> rangeFormatting(DocumentRangeFormattingParams params) {
    DocumentContext documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(null);
    }

    List<TextEdit> edits = formatProvider.getRangeFormatting(params, documentContext);
    return CompletableFuture.completedFuture(edits);
  }

  @Override
  public CompletableFuture<List<FoldingRange>> foldingRange(FoldingRangeRequestParams params) {
    DocumentContext documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(null);
    }

    return CompletableFuture.supplyAsync(() -> foldingRangeProvider.getFoldingRange(documentContext));
  }

  @Override
  public CompletableFuture<List<CallHierarchyItem>> prepareCallHierarchy(CallHierarchyPrepareParams params) {
    // При возврате пустого списка VSCode падает. По протоколу разрешен возврат null.
    DocumentContext documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(null);
    }

    return CompletableFuture.supplyAsync(() -> {
      List<CallHierarchyItem> callHierarchyItems = callHierarchyProvider.prepareCallHierarchy(documentContext, params);
      if (callHierarchyItems.isEmpty()) {
        return null;
      }
      return callHierarchyItems;
    });
  }

  @Override
  public CompletableFuture<List<CallHierarchyIncomingCall>> callHierarchyIncomingCalls(
    CallHierarchyIncomingCallsParams params
  ) {
    DocumentContext documentContext = context.getDocument(params.getItem().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(Collections.emptyList());
    }

    return CompletableFuture.supplyAsync(() -> callHierarchyProvider.incomingCalls(documentContext, params));
  }

  @Override
  public CompletableFuture<List<CallHierarchyOutgoingCall>> callHierarchyOutgoingCalls(
    CallHierarchyOutgoingCallsParams params
  ) {
    DocumentContext documentContext = context.getDocument(params.getItem().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(Collections.emptyList());
    }

    return CompletableFuture.supplyAsync(() -> callHierarchyProvider.outgoingCalls(documentContext, params));
  }

  @Override
  public CompletableFuture<List<SelectionRange>> selectionRange(SelectionRangeParams params) {
    var documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(Collections.emptyList());
    }

    return CompletableFuture.supplyAsync(() -> selectionRangeProvider.getSelectionRange(documentContext, params));
  }

  @Override
  public void didOpen(DidOpenTextDocumentParams params) {
    DocumentContext documentContext = context.addDocument(params.getTextDocument());
    if (configuration.getDiagnosticsOptions().getComputeTrigger() != ComputeTrigger.NEVER) {
      validate(documentContext);
    }
  }

  @Override
  public void didChange(DidChangeTextDocumentParams params) {

    // TODO: Place to optimize -> migrate to #TextDocumentSyncKind.INCREMENTAL and build changed parse tree
    DocumentContext documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return;
    }

    documentContext.rebuild(params.getContentChanges().get(0).getText(), params.getTextDocument().getVersion());

    if (configuration.getDiagnosticsOptions().getComputeTrigger() == ComputeTrigger.ONTYPE) {
      validate(documentContext);
    }
  }

  @Override
  public void didClose(DidCloseTextDocumentParams params) {
    DocumentContext documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return;
    }

    documentContext.clearSecondaryData();

    diagnosticProvider.publishEmptyDiagnosticList(documentContext);
  }

  @Override
  public void didSave(DidSaveTextDocumentParams params) {
    DocumentContext documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return;
    }

    if (configuration.getDiagnosticsOptions().getComputeTrigger() != ComputeTrigger.NEVER) {
      validate(documentContext);
    }
  }

  @Override
  public CompletableFuture<List<DocumentLink>> documentLink(DocumentLinkParams params) {
    DocumentContext documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(null);
    }

    return CompletableFuture.supplyAsync(() -> documentLinkProvider.getDocumentLinks(documentContext));
  }

  @Override
  public CompletableFuture<Diagnostics> diagnostics(DiagnosticParams params) {
    DocumentContext documentContext = context.getDocument(params.getTextDocument().getUri());
    if (documentContext == null) {
      return CompletableFuture.completedFuture(Diagnostics.EMPTY);
    }

    return CompletableFuture.supplyAsync(() -> {
      var diagnostics = documentContext.getDiagnostics();

      var range = params.getRange();
      if (range != null) {
        diagnostics = diagnostics.stream()
          .filter(diagnostic -> Ranges.containsRange(range, diagnostic.getRange()))
          .collect(Collectors.toList());
      }
      return new Diagnostics(diagnostics, documentContext.getVersion());
    });
  }

  public void reset() {
    context.clear();
  }

  private void validate(DocumentContext documentContext) {
    diagnosticProvider.computeAndPublishDiagnostics(documentContext);
  }

}
