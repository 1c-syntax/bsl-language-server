/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
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
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.jsonrpc.DiagnosticParams;
import com.github._1c_syntax.bsl.languageserver.jsonrpc.Diagnostics;
import com.github._1c_syntax.bsl.languageserver.jsonrpc.ProtocolExtension;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp4j.CodeLensOptions;
import org.eclipse.lsp4j.DocumentLinkOptions;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.SaveOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.ServerInfo;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextDocumentSyncOptions;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class BSLLanguageServer implements LanguageServer, ProtocolExtension {

  private final LanguageServerConfiguration configuration;
  private final BSLTextDocumentService textDocumentService;
  private final BSLWorkspaceService workspaceService;
  private final ServerContext context;
  private final ServerInfo serverInfo;
  private boolean shutdownWasCalled;

  @Override
  public CompletableFuture<InitializeResult> initialize(InitializeParams params) {

    setConfigurationRoot(params);
    CompletableFuture.runAsync(context::populateContext);

    ServerCapabilities capabilities = new ServerCapabilities();
    capabilities.setTextDocumentSync(getTextDocumentSyncOptions());
    capabilities.setDocumentRangeFormattingProvider(Boolean.TRUE);
    capabilities.setDocumentFormattingProvider(Boolean.TRUE);
    capabilities.setFoldingRangeProvider(Boolean.TRUE);
    capabilities.setDocumentSymbolProvider(Boolean.TRUE);
    capabilities.setCodeActionProvider(Boolean.TRUE);
    capabilities.setCodeLensProvider(new CodeLensOptions());
    capabilities.setDocumentLinkProvider(new DocumentLinkOptions());
    capabilities.setWorkspaceSymbolProvider(Boolean.TRUE);
    capabilities.setReferencesProvider(Boolean.TRUE);
    capabilities.setDefinitionProvider(Boolean.TRUE);
    capabilities.setHoverProvider(Boolean.TRUE);
    // todo: static registration options
    capabilities.setCallHierarchyProvider(Boolean.TRUE);

    InitializeResult result = new InitializeResult(capabilities, serverInfo);

    return CompletableFuture.completedFuture(result);
  }

  private void setConfigurationRoot(InitializeParams params) {
    if (params.getRootUri() == null) {
      return;
    }

    Path rootPath;
    try {
      rootPath = new File(new URI(params.getRootUri()).getPath()).getCanonicalFile().toPath();
    } catch (URISyntaxException | IOException e) {
      LOGGER.error("Can't read root URI from initialization params.", e);
      return;
    }

    Path configurationRoot = LanguageServerConfiguration.getCustomConfigurationRoot(
      configuration,
      rootPath);
    context.setConfigurationRoot(configurationRoot);
  }

  @Override
  public CompletableFuture<Object> shutdown() {
    shutdownWasCalled = true;
    textDocumentService.reset();
    context.clear();
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

  private static TextDocumentSyncOptions getTextDocumentSyncOptions() {
    TextDocumentSyncOptions textDocumentSync = new TextDocumentSyncOptions();

    textDocumentSync.setOpenClose(Boolean.TRUE);
    textDocumentSync.setChange(TextDocumentSyncKind.Full);
    textDocumentSync.setWillSave(Boolean.FALSE);
    textDocumentSync.setWillSaveWaitUntil(Boolean.FALSE);

    SaveOptions save = new SaveOptions();
    save.setIncludeText(Boolean.FALSE);

    textDocumentSync.setSave(save);

    return textDocumentSync;
  }
}
