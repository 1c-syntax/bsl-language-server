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

import com.github._1c_syntax.bsl.languageserver.configuration.GlobalLanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.bsl.languageserver.jsonrpc.DiagnosticParams;
import com.github._1c_syntax.bsl.languageserver.jsonrpc.Diagnostics;
import com.github._1c_syntax.bsl.languageserver.jsonrpc.ProtocolExtension;
import com.github._1c_syntax.bsl.languageserver.providers.CommandProvider;
import com.github._1c_syntax.bsl.languageserver.providers.DocumentSymbolProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp4j.CallHierarchyRegistrationOptions;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionOptions;
import org.eclipse.lsp4j.CodeLensOptions;
import org.eclipse.lsp4j.ColorProviderOptions;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.DefinitionOptions;
import org.eclipse.lsp4j.DidChangeWatchedFilesCapabilities;
import org.eclipse.lsp4j.DidChangeWatchedFilesRegistrationOptions;
import org.eclipse.lsp4j.ImplementationRegistrationOptions;
import org.eclipse.lsp4j.DiagnosticRegistrationOptions;
import org.eclipse.lsp4j.DocumentFormattingOptions;
import org.eclipse.lsp4j.DocumentHighlightOptions;
import org.eclipse.lsp4j.DocumentLinkOptions;
import org.eclipse.lsp4j.DocumentOnTypeFormattingOptions;
import org.eclipse.lsp4j.DocumentRangeFormattingOptions;
import org.eclipse.lsp4j.DocumentSymbolOptions;
import org.eclipse.lsp4j.ExecuteCommandOptions;
import org.eclipse.lsp4j.FileOperationFilter;
import org.eclipse.lsp4j.FileOperationOptions;
import org.eclipse.lsp4j.FileOperationPattern;
import org.eclipse.lsp4j.FileOperationPatternKind;
import org.eclipse.lsp4j.FileOperationsServerCapabilities;
import org.eclipse.lsp4j.FileSystemWatcher;
import org.eclipse.lsp4j.FoldingRangeProviderOptions;
import org.eclipse.lsp4j.HoverOptions;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.InlayHintRegistrationOptions;
import org.eclipse.lsp4j.ReferenceOptions;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.RenameCapabilities;
import org.eclipse.lsp4j.RenameOptions;
import org.eclipse.lsp4j.SaveOptions;
import org.eclipse.lsp4j.SelectionRangeRegistrationOptions;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.lsp4j.SemanticTokensServerFull;
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions;
import org.eclipse.lsp4j.SignatureHelpOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.ServerInfo;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextDocumentSyncOptions;
import org.eclipse.lsp4j.TypeHierarchyRegistrationOptions;
import org.eclipse.lsp4j.WatchKind;
import org.eclipse.lsp4j.WorkspaceClientCapabilities;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.WorkspaceFoldersOptions;
import org.eclipse.lsp4j.WorkspaceServerCapabilities;
import org.eclipse.lsp4j.WorkspaceSymbolOptions;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Основной класс BSL Language Server.
 * <p>
 * Реализует интерфейс {@link LanguageServer} из LSP4J и обеспечивает
 * обработку запросов инициализации, настройку возможностей сервера
 * и координацию работы сервисов документов и рабочей области.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BSLLanguageServer implements LanguageServer, ProtocolExtension {

  private final BSLTextDocumentService textDocumentService;
  private final BSLWorkspaceService workspaceService;
  private final CommandProvider commandProvider;
  private final ClientCapabilitiesHolder clientCapabilitiesHolder;
  private final LanguageClientHolder languageClientHolder;
  private final ServerContextProvider serverContextProvider;
  private final GlobalLanguageServerConfiguration globalConfiguration;
  private final ServerInfo serverInfo;
  private final SemanticTokensLegend legend;
  @Qualifier("populateContextExecutor")
  private final ExecutorService populateContextExecutor;

  private boolean shutdownWasCalled;

  @Override
  public CompletableFuture<InitializeResult> initialize(InitializeParams params) {

    clientCapabilitiesHolder.setCapabilities(params.getCapabilities());
    clientCapabilitiesHolder.setClientInfo(params.getClientInfo());

    setConfigurationRoot(params);

    var capabilities = new ServerCapabilities();
    capabilities.setTextDocumentSync(getTextDocumentSyncOptions());
    capabilities.setDocumentRangeFormattingProvider(getDocumentRangeFormattingProvider());
    capabilities.setDocumentFormattingProvider(getDocumentFormattingProvider());
    capabilities.setDocumentOnTypeFormattingProvider(getDocumentOnTypeFormattingProvider());
    capabilities.setFoldingRangeProvider(getFoldingRangeProvider());
    capabilities.setDocumentSymbolProvider(getDocumentSymbolProvider());
    capabilities.setCodeActionProvider(getCodeActionProvider());
    capabilities.setCodeLensProvider(getCodeLensProvider());
    capabilities.setDocumentLinkProvider(getDocumentLinkProvider());
    capabilities.setWorkspaceSymbolProvider(getWorkspaceProvider());
    capabilities.setHoverProvider(getHoverProvider());
    capabilities.setCompletionProvider(getCompletionProvider());
    capabilities.setSignatureHelpProvider(getSignatureHelpProvider());
    capabilities.setDocumentHighlightProvider(getDocumentHighlightProvider());
    capabilities.setReferencesProvider(getReferencesProvider());
    capabilities.setDefinitionProvider(getDefinitionProvider());
    capabilities.setImplementationProvider(getImplementationProvider());
    capabilities.setCallHierarchyProvider(getCallHierarchyProvider());
    capabilities.setTypeHierarchyProvider(getTypeHierarchyProvider());
    capabilities.setSelectionRangeProvider(getSelectionRangeProvider());
    capabilities.setColorProvider(getColorProvider());
    capabilities.setRenameProvider(getRenameProvider(params));
    capabilities.setInlayHintProvider(getInlayHintProvider());
    capabilities.setExecuteCommandProvider(getExecuteCommandProvider());
    capabilities.setDiagnosticProvider(getDiagnosticProvider());
    capabilities.setSemanticTokensProvider(getSemanticTokensProvider());
    capabilities.setWorkspace(getWorkspaceCapabilities());

    var result = new InitializeResult(capabilities, serverInfo);

    return CompletableFuture.completedFuture(result);
  }

  private void setConfigurationRoot(InitializeParams params) {
    var workspaceFolders = params.getWorkspaceFolders();

    if (workspaceFolders == null || workspaceFolders.isEmpty()) {
      var rootUri = resolveRootUri(params);
      if (rootUri == null) {
        return;
      }
      workspaceFolders = List.of(new WorkspaceFolder(rootUri, "root"));
    }

    // Добавляем все workspace folders
    workspaceFolders.forEach(serverContextProvider::addWorkspace);
  }

  private @Nullable String resolveRootUri(InitializeParams params) {
    var rootUri = params.getRootUri();
    if (rootUri != null && !rootUri.isEmpty()) {
      return rootUri;
    }

    var rootPath = params.getRootPath();
    if (rootPath == null || rootPath.isEmpty()) {
      LOGGER.debug("No workspace folders, rootUri, or rootPath provided in initialize params");
      return null;
    }


    try {
      return new File(rootPath).getCanonicalFile().toURI().toString();
    } catch (IOException e) {
      LOGGER.debug("Can't convert rootPath to URI: {}", rootPath, e);
      return null;
    }
  }

  @Override
  public void initialized(InitializedParams params) {
    registerFileWatchers();

    // Populate all workspace contexts
    var allContexts = serverContextProvider.getAllContexts();
    var tasks = allContexts.entrySet().stream()
      .map((Map.Entry<URI, ServerContext> entry) -> {
        try (var ctx = WorkspaceContextHolder.forUri(entry.getKey())) {
          return CompletableFuture.runAsync(
            entry.getValue()::populateContext,
            populateContextExecutor
          );
        }
      })
      .toArray(CompletableFuture[]::new);

    CompletableFuture.allOf(tasks)
      .whenComplete((Void unused, @Nullable Throwable throwable) -> {
        if (throwable != null) {
          LOGGER.error("Error populating workspace contexts", throwable);
        }
      });
  }

  /**
   * Динамически регистрирует у клиента наблюдателей за файлами рабочей области.
   * <p>
   * Регистрация выполняется только если клиент заявил поддержку
   * {@code workspace.didChangeWatchedFiles.dynamicRegistration}. Универсальные клиенты
   * (neovim, helix и др.) без статической конфигурации наблюдателей сами ничего не присылают
   * в {@code workspace/didChangeWatchedFiles}, из-за чего индекс устаревает при операциях
   * вне редактора (git checkout, выгрузка из конфигуратора). Регистрируются наблюдатели на
   * {@code **&#47;*.bsl} и {@code **&#47;*.os}; за конфигурационным файлом следит сам сервер
   * (см. {@code ConfigurationFileSystemWatcher}), поэтому клиентский наблюдатель за ним не нужен.
   * <p>
   * Если клиент не заявил динамическую регистрацию или не подключён, метод ничего не делает.
   */
  private void registerFileWatchers() {
    if (!hasDidChangeWatchedFilesDynamicRegistration()) {
      return;
    }

    languageClientHolder.execIfConnected(client -> {
      var watchKind = WatchKind.Create | WatchKind.Change | WatchKind.Delete;
      var watchers = List.of(
        new FileSystemWatcher(Either.forLeft("**/*.bsl"), watchKind),
        new FileSystemWatcher(Either.forLeft("**/*.os"), watchKind)
      );

      var registrationOptions = new DidChangeWatchedFilesRegistrationOptions(watchers);
      var registration = new Registration(
        "bsl-language-server-watched-files",
        "workspace/didChangeWatchedFiles",
        registrationOptions
      );

      client.registerCapability(new RegistrationParams(List.of(registration)));
    });
  }

  private boolean hasDidChangeWatchedFilesDynamicRegistration() {
    return clientCapabilitiesHolder.getCapabilities()
      .map(ClientCapabilities::getWorkspace)
      .map(WorkspaceClientCapabilities::getDidChangeWatchedFiles)
      .map(DidChangeWatchedFilesCapabilities::getDynamicRegistration)
      .orElse(false);
  }

  @Override
  public CompletableFuture<Object> shutdown() {
    shutdownWasCalled = true;
    textDocumentService.reset();
    serverContextProvider.clear();
    return CompletableFuture.completedFuture(Boolean.TRUE);
  }

  @Override
  public void exit() {
    int status = shutdownWasCalled ? 0 : 1;
    System.exit(status);
  }

  /**
   * {@inheritDoc}
   * <p>
   * См. {@link BSLTextDocumentService#diagnostics(DiagnosticParams)}
   */
  @Override
  public CompletableFuture<Diagnostics> diagnostics(DiagnosticParams params) {
    return textDocumentService.diagnostics(params);
  }

  @Override
  public TextDocumentService getTextDocumentService() {
    return textDocumentService;
  }

  @Override
  public WorkspaceService getWorkspaceService() {
    return workspaceService;
  }

  /**
   * Формирует настройки синхронизации текстовых документов на основе глобальной конфигурации сервера.
   *
   * @return настройки синхронизации текстовых документов
   */
  private TextDocumentSyncOptions getTextDocumentSyncOptions() {
    var textDocumentSync = new TextDocumentSyncOptions();

    textDocumentSync.setOpenClose(Boolean.TRUE);
    textDocumentSync.setChange(getConfiguredSyncKind());
    textDocumentSync.setWillSave(Boolean.FALSE);
    textDocumentSync.setWillSaveWaitUntil(Boolean.FALSE);

    var save = new SaveOptions();
    save.setIncludeText(Boolean.FALSE);

    textDocumentSync.setSave(save);

    return textDocumentSync;
  }

  /**
   * Возвращает тип синхронизации документов, заданный в глобальной конфигурации сервера
   * (по умолчанию Incremental).
   *
   * @return тип синхронизации текстовых документов
   */
  private TextDocumentSyncKind getConfiguredSyncKind() {
    return globalConfiguration.getCapabilities()
      .getTextDocumentSync()
      .getChange();
  }

  private static CodeActionOptions getCodeActionProvider() {
    var codeActionOptions = new CodeActionOptions();
    codeActionOptions.setWorkDoneProgress(Boolean.FALSE);
    codeActionOptions.setResolveProvider(Boolean.FALSE);

    var codeActionKinds = List.of(
      CodeActionKind.QuickFix,
      CodeActionKind.Refactor,
      CodeActionKind.SourceFixAll
    );

    codeActionOptions.setCodeActionKinds(codeActionKinds);

    return codeActionOptions;
  }

  private static DocumentSymbolOptions getDocumentSymbolProvider() {
    var documentSymbolOptions = new DocumentSymbolOptions();
    documentSymbolOptions.setWorkDoneProgress(Boolean.FALSE);
    documentSymbolOptions.setLabel(DocumentSymbolProvider.LABEL);
    return documentSymbolOptions;
  }

  private static FoldingRangeProviderOptions getFoldingRangeProvider() {
    var foldingRangeProviderOptions = new FoldingRangeProviderOptions();
    foldingRangeProviderOptions.setWorkDoneProgress(Boolean.FALSE);

    return foldingRangeProviderOptions;
  }

  private static DocumentFormattingOptions getDocumentFormattingProvider() {
    var documentFormattingOptions = new DocumentFormattingOptions();
    documentFormattingOptions.setWorkDoneProgress(Boolean.FALSE);
    return documentFormattingOptions;
  }

  private static DocumentRangeFormattingOptions getDocumentRangeFormattingProvider() {
    var documentRangeFormattingOptions = new DocumentRangeFormattingOptions();
    documentRangeFormattingOptions.setWorkDoneProgress(Boolean.FALSE);
    return documentRangeFormattingOptions;
  }

  private static DocumentOnTypeFormattingOptions getDocumentOnTypeFormattingProvider() {
    var options = new DocumentOnTypeFormattingOptions();
    options.setFirstTriggerCharacter("\n");
    options.setMoreTriggerCharacter(List.of(";"));
    return options;
  }

  private static CodeLensOptions getCodeLensProvider() {
    var codeLensOptions = new CodeLensOptions();
    codeLensOptions.setResolveProvider(Boolean.TRUE);
    codeLensOptions.setWorkDoneProgress(Boolean.FALSE);
    return codeLensOptions;
  }

  private static DocumentLinkOptions getDocumentLinkProvider() {
    var documentLinkOptions = new DocumentLinkOptions();
    documentLinkOptions.setResolveProvider(Boolean.FALSE);
    return documentLinkOptions;
  }

  private static HoverOptions getHoverProvider() {
    var hoverOptions = new HoverOptions();
    hoverOptions.setWorkDoneProgress(Boolean.FALSE);
    return hoverOptions;
  }

  private static CompletionOptions getCompletionProvider() {
    var completionOptions = new CompletionOptions();
    completionOptions.setResolveProvider(Boolean.TRUE);
    completionOptions.setTriggerCharacters(List.of("."));
    return completionOptions;
  }

  private static SignatureHelpOptions getSignatureHelpProvider() {
    var signatureHelpOptions = new SignatureHelpOptions();
    signatureHelpOptions.setTriggerCharacters(List.of("(", ","));
    signatureHelpOptions.setRetriggerCharacters(List.of(","));
    return signatureHelpOptions;
  }

  private static DocumentHighlightOptions getDocumentHighlightProvider() {
    var documentHighlightOptions = new DocumentHighlightOptions();
    documentHighlightOptions.setWorkDoneProgress(Boolean.FALSE);
    return documentHighlightOptions;
  }

  private static DefinitionOptions getDefinitionProvider() {
    var definitionOptions = new DefinitionOptions();
    definitionOptions.setWorkDoneProgress(Boolean.FALSE);
    return definitionOptions;
  }

  private static ImplementationRegistrationOptions getImplementationProvider() {
    var implementationRegistrationOptions = new ImplementationRegistrationOptions();
    implementationRegistrationOptions.setWorkDoneProgress(Boolean.FALSE);
    return implementationRegistrationOptions;
  }

  private static ReferenceOptions getReferencesProvider() {
    var referenceOptions = new ReferenceOptions();
    referenceOptions.setWorkDoneProgress(Boolean.FALSE);
    return referenceOptions;
  }

  private static CallHierarchyRegistrationOptions getCallHierarchyProvider() {
    var callHierarchyRegistrationOptions = new CallHierarchyRegistrationOptions();
    callHierarchyRegistrationOptions.setWorkDoneProgress(Boolean.FALSE);
    return callHierarchyRegistrationOptions;
  }

  private static TypeHierarchyRegistrationOptions getTypeHierarchyProvider() {
    var typeHierarchyRegistrationOptions = new TypeHierarchyRegistrationOptions();
    typeHierarchyRegistrationOptions.setWorkDoneProgress(Boolean.FALSE);
    return typeHierarchyRegistrationOptions;
  }

  private static WorkspaceSymbolOptions getWorkspaceProvider() {
    var workspaceSymbolOptions = new WorkspaceSymbolOptions();
    workspaceSymbolOptions.setWorkDoneProgress(Boolean.FALSE);
    return workspaceSymbolOptions;
  }

  private static SelectionRangeRegistrationOptions getSelectionRangeProvider() {
    var selectionRangeRegistrationOptions = new SelectionRangeRegistrationOptions();
    selectionRangeRegistrationOptions.setWorkDoneProgress(Boolean.FALSE);
    return selectionRangeRegistrationOptions;
  }

  private static ColorProviderOptions getColorProvider() {
    var colorProviderOptions = new ColorProviderOptions();
    colorProviderOptions.setWorkDoneProgress(Boolean.FALSE);
    return colorProviderOptions;
  }

  private static Either<Boolean, RenameOptions> getRenameProvider(InitializeParams params) {

    if (hasRenamePrepareSupport(params)) {

      var renameOptions = new RenameOptions();
      renameOptions.setWorkDoneProgress(Boolean.FALSE);
      renameOptions.setPrepareProvider(Boolean.TRUE);

      return Either.forRight(renameOptions);

    } else {

      return Either.forLeft(Boolean.TRUE);

    }

  }

  private static boolean hasRenamePrepareSupport(InitializeParams params) {
    return Optional.of(params)
      .map(InitializeParams::getCapabilities)
      .map(ClientCapabilities::getTextDocument)
      .map(TextDocumentClientCapabilities::getRename)
      .map(RenameCapabilities::getPrepareSupport)
      .orElse(false);
  }

  private static InlayHintRegistrationOptions getInlayHintProvider() {
    var inlayHintOptions = new InlayHintRegistrationOptions();
    inlayHintOptions.setResolveProvider(Boolean.FALSE);
    inlayHintOptions.setWorkDoneProgress(Boolean.FALSE);
    return inlayHintOptions;
  }

  private static DiagnosticRegistrationOptions getDiagnosticProvider() {
    var diagnosticOptions = new DiagnosticRegistrationOptions();
    diagnosticOptions.setWorkDoneProgress(Boolean.FALSE);
    diagnosticOptions.setInterFileDependencies(Boolean.TRUE);
    diagnosticOptions.setWorkspaceDiagnostics(Boolean.FALSE);
    return diagnosticOptions;
  }

  private ExecuteCommandOptions getExecuteCommandProvider() {
    var executeCommandOptions = new ExecuteCommandOptions();
    executeCommandOptions.setCommands(commandProvider.getCommandIds());
    executeCommandOptions.setWorkDoneProgress(Boolean.FALSE);
    return executeCommandOptions;
  }

  private SemanticTokensWithRegistrationOptions getSemanticTokensProvider() {
    var semanticTokensProvider = new SemanticTokensWithRegistrationOptions(legend);

    var fullOptions = new SemanticTokensServerFull();
    fullOptions.setDelta(Boolean.TRUE);
    semanticTokensProvider.setFull(fullOptions);

    semanticTokensProvider.setRange(Boolean.TRUE);
    return semanticTokensProvider;
  }

  private static WorkspaceServerCapabilities getWorkspaceCapabilities() {
    var workspaceCapabilities = new WorkspaceServerCapabilities();

    var workspaceFoldersOptions = new WorkspaceFoldersOptions();
    workspaceFoldersOptions.setSupported(Boolean.TRUE);
    workspaceFoldersOptions.setChangeNotifications(Boolean.TRUE);

    workspaceCapabilities.setWorkspaceFolders(workspaceFoldersOptions);
    workspaceCapabilities.setFileOperations(getFileOperationsCapabilities());
    return workspaceCapabilities;
  }

  /**
   * Формирует возможности сервера по обработке файловых операций рабочей области
   * ({@code didCreate}/{@code didRename}/{@code didDelete}). Фильтры покрывают BSL- и
   * OneScript-файлы ({@code **&#47;*.bsl}, {@code **&#47;*.os}), а также каталоги, чтобы
   * получать события переименования и удаления папок целиком.
   *
   * @return возможности сервера по файловым операциям
   */
  private static FileOperationsServerCapabilities getFileOperationsCapabilities() {
    var fileOperations = new FileOperationsServerCapabilities();

    var options = new FileOperationOptions(getFileOperationFilters());
    fileOperations.setDidCreate(options);
    fileOperations.setDidRename(options);
    fileOperations.setDidDelete(options);

    return fileOperations;
  }

  private static List<FileOperationFilter> getFileOperationFilters() {
    var folderPattern = new FileOperationPattern("**/*");
    folderPattern.setMatches(FileOperationPatternKind.Folder);

    return List.of(
      new FileOperationFilter(new FileOperationPattern("**/*.bsl")),
      new FileOperationFilter(new FileOperationPattern("**/*.os")),
      new FileOperationFilter(folderPattern)
    );
  }

}
