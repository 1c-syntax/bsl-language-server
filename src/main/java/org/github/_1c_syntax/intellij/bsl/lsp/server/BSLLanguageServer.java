package org.github._1c_syntax.intellij.bsl.lsp.server;

import com.google.common.collect.Lists;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

import java.util.concurrent.CompletableFuture;

public class BSLLanguageServer implements LanguageServer, LanguageClientAware {

  private LanguageClient client;
  private TextDocumentService textDocumentService;
  private WorkspaceService workspaceService;

  public BSLLanguageServer() {
    textDocumentService = new BSLTextDocumentService();
    workspaceService = new BSLWorkspaceService();
  }

  @Override
  public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
    ServerCapabilities capabilities = new ServerCapabilities();
    capabilities.setTextDocumentSync(TextDocumentSyncKind.Full);
    capabilities.setCompletionProvider(new CompletionOptions(false, Lists.newArrayList(".")));

    InitializeResult result = new InitializeResult(capabilities);

    return CompletableFuture.completedFuture(result);
  }

  @Override
  public CompletableFuture<Object> shutdown() {
    return CompletableFuture.completedFuture(Boolean.TRUE);
  }

  @Override
  public void exit() {

  }

  @Override
  public TextDocumentService getTextDocumentService() {
    return textDocumentService;
  }

  @Override
  public WorkspaceService getWorkspaceService() {
    return workspaceService;
  }

  @Override
  public void connect(LanguageClient client) {
    this.client = client;
  }
}
