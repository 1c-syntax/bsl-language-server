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
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.utils.Absolute;
import mockit.Mock;
import mockit.MockUp;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.DidChangeWatchedFilesCapabilities;
import org.eclipse.lsp4j.DidChangeWatchedFilesRegistrationOptions;
import org.eclipse.lsp4j.FileSystemWatcher;
import org.eclipse.lsp4j.GeneralClientCapabilities;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.PositionEncodingKind;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.RelativePattern;
import org.eclipse.lsp4j.RenameCapabilities;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.WorkspaceClientCapabilities;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class BSLLanguageServerTest {

  @Autowired
  private BSLLanguageServer server;

  @Autowired
  private GlobalLanguageServerConfiguration globalConfiguration;

  @Autowired
  private LanguageClientHolder languageClientHolder;

  @BeforeEach
  void setUp() {
    new MockUp<System>() {
      @Mock
      public void exit(int value) {
        throw new RuntimeException(String.valueOf(value));
      }
    };
  }

  @AfterEach
  void tearDown() {
    languageClientHolder.connect(null);
  }

  @Test
  void initialize() throws ExecutionException, InterruptedException {
    // given
    InitializeParams params = new InitializeParams();

    WorkspaceFolder workspaceFolder = new WorkspaceFolder(Absolute.path(PATH_TO_METADATA).toUri().toString(), "test");
    List<WorkspaceFolder> workspaceFolders = List.of(workspaceFolder);
    params.setWorkspaceFolders(workspaceFolders);

    // when
    InitializeResult initialize = server.initialize(params).get();

    // then
    assertThat(initialize.getCapabilities().getTextDocumentSync().getRight().getChange())
      .isEqualTo(TextDocumentSyncKind.Incremental);
    assertThat(initialize.getCapabilities().getTypeHierarchyProvider()).isNotNull();
    assertThat(initialize.getCapabilities().getImplementationProvider()).isNotNull();
    assertThat(initialize.getCapabilities().getLinkedEditingRangeProvider()).isNotNull();
  }

  @ParameterizedTest
  @EnumSource(value = TextDocumentSyncKind.class, names = {"Full", "None"})
  void initializeUsesTextDocumentSyncKindFromConfiguration(TextDocumentSyncKind syncKind, @TempDir Path tempDir)
    throws ExecutionException, InterruptedException, IOException {
    // given
    var configFile = tempDir.resolve(".bsl-language-server.json").toFile();
    Files.writeString(configFile.toPath(), """
      {
        "capabilities": {
          "textDocumentSync": {
            "change": "%s"
          }
        }
      }
      """.formatted(syncKind));
    globalConfiguration.update(configFile);

    var workspaceFolder = new WorkspaceFolder(Absolute.path(PATH_TO_METADATA).toUri().toString(), "test");
    var params = new InitializeParams();
    params.setWorkspaceFolders(List.of(workspaceFolder));

    try {
      // when
      InitializeResult initialize = server.initialize(params).get();

      // then
      assertThat(initialize.getCapabilities().getTextDocumentSync().getRight().getChange())
        .isEqualTo(syncKind);
    } finally {
      globalConfiguration.reset();
    }
  }

  @Test
  void initializeDeclaresUtf16PositionEncoding() throws ExecutionException, InterruptedException {
    // given
    InitializeParams params = new InitializeParams();

    WorkspaceFolder workspaceFolder = new WorkspaceFolder(Absolute.path(PATH_TO_METADATA).toUri().toString(), "test");
    params.setWorkspaceFolders(List.of(workspaceFolder));

    // when
    InitializeResult initialize = server.initialize(params).get();

    // then
    assertThat(initialize.getCapabilities().getPositionEncoding())
      .isEqualTo(PositionEncodingKind.UTF16);
  }

  @Test
  void initializeDeclaresUtf16PositionEncodingWhenClientNegotiatesIt()
    throws ExecutionException, InterruptedException {
    // given
    InitializeParams params = new InitializeParams();

    WorkspaceFolder workspaceFolder = new WorkspaceFolder(Absolute.path(PATH_TO_METADATA).toUri().toString(), "test");
    params.setWorkspaceFolders(List.of(workspaceFolder));

    var clientCapabilities = new ClientCapabilities();
    var general = new GeneralClientCapabilities();
    general.setPositionEncodings(List.of(PositionEncodingKind.UTF16));
    clientCapabilities.setGeneral(general);
    params.setCapabilities(clientCapabilities);

    // when
    InitializeResult initialize = server.initialize(params).get();

    // then
    assertThat(initialize.getCapabilities().getPositionEncoding())
      .isEqualTo(PositionEncodingKind.UTF16);
  }

  @Test
  void initializeRename() throws ExecutionException, InterruptedException {
    // given
    InitializeParams params = new InitializeParams();

    WorkspaceFolder workspaceFolder = new WorkspaceFolder(Absolute.path(PATH_TO_METADATA).toUri().toString(), "test");
    List<WorkspaceFolder> workspaceFolders = List.of(workspaceFolder);
    params.setWorkspaceFolders(workspaceFolders);

    var capabilities = new ClientCapabilities();
    params.setCapabilities(capabilities);
    capabilities.setTextDocument(new TextDocumentClientCapabilities());
    var textDocument = capabilities.getTextDocument();
    textDocument.setRename(new RenameCapabilities());
    textDocument.getRename().setPrepareSupport(true);
    // when
    InitializeResult initialize = server.initialize(params).get();

    // then
    assertThat(initialize.getCapabilities().getRenameProvider().isRight()).isTrue();
  }

  @Test
  void initialized() {
    // given
    InitializeParams initParams = new InitializeParams();
    WorkspaceFolder workspaceFolder = new WorkspaceFolder(Absolute.path(PATH_TO_METADATA).toUri().toString(), "test");
    List<WorkspaceFolder> workspaceFolders = List.of(workspaceFolder);
    initParams.setWorkspaceFolders(workspaceFolders);

    // initialize first
    server.initialize(initParams);

    // when
    InitializedParams params = new InitializedParams();
    // then - should not throw
    server.initialized(params);
  }

  @Test
  void initializedRegistersFileWatchersWhenDynamicRegistrationSupported() {
    // given
    var client = mock(LanguageClient.class);
    when(client.registerCapability(any()))
      .thenReturn(CompletableFuture.completedFuture(null));
    languageClientHolder.connect(client);

    var initParams = new InitializeParams();
    var workspaceFolder = new WorkspaceFolder(Absolute.path(PATH_TO_METADATA).toUri().toString(), "test");
    initParams.setWorkspaceFolders(List.of(workspaceFolder));

    var capabilities = new ClientCapabilities();
    var workspace = new WorkspaceClientCapabilities();
    workspace.setDidChangeWatchedFiles(new DidChangeWatchedFilesCapabilities(true));
    capabilities.setWorkspace(workspace);
    initParams.setCapabilities(capabilities);

    server.initialize(initParams);

    // when
    server.initialized(new InitializedParams());

    // then
    var captor = ArgumentCaptor.forClass(RegistrationParams.class);
    verify(client).registerCapability(captor.capture());

    var registrations = captor.getValue().getRegistrations();
    assertThat(registrations).hasSize(1);

    var registration = registrations.get(0);
    assertThat(registration.getMethod()).isEqualTo("workspace/didChangeWatchedFiles");

    var options = (DidChangeWatchedFilesRegistrationOptions) registration.getRegisterOptions();
    assertThat(options.getWatchers())
      .extracting(FileSystemWatcher::getGlobPattern)
      .extracting(globPattern -> globPattern.getLeft())
      .containsExactlyInAnyOrder("**/*.bsl", "**/*.os");
  }

  @Test
  void initializedRegistersRelativePatternWatchersPerWorkspaceFolder(@TempDir Path firstRoot, @TempDir Path secondRoot) {
    // given
    var client = mock(LanguageClient.class);
    when(client.registerCapability(any()))
      .thenReturn(CompletableFuture.completedFuture(null));
    languageClientHolder.connect(client);

    var firstFolderUri = Absolute.uri(firstRoot.toUri()).toString();
    var secondFolderUri = Absolute.uri(secondRoot.toUri()).toString();

    var initParams = new InitializeParams();
    initParams.setWorkspaceFolders(List.of(
      new WorkspaceFolder(firstFolderUri, "first"),
      new WorkspaceFolder(secondFolderUri, "second")
    ));

    var capabilities = new ClientCapabilities();
    var workspace = new WorkspaceClientCapabilities();
    var watchedFiles = new DidChangeWatchedFilesCapabilities(true);
    watchedFiles.setRelativePatternSupport(true);
    workspace.setDidChangeWatchedFiles(watchedFiles);
    capabilities.setWorkspace(workspace);
    initParams.setCapabilities(capabilities);

    server.initialize(initParams);

    // when
    server.initialized(new InitializedParams());

    // then
    var captor = ArgumentCaptor.forClass(RegistrationParams.class);
    verify(client).registerCapability(captor.capture());

    var registrations = captor.getValue().getRegistrations();
    assertThat(registrations).hasSize(1);

    var options = (DidChangeWatchedFilesRegistrationOptions) registrations.get(0).getRegisterOptions();
    assertThat(options.getWatchers())
      .allSatisfy(watcher -> assertThat(watcher.getGlobPattern().isRight()).isTrue())
      .extracting(FileSystemWatcher::getGlobPattern)
      .extracting(globPattern -> {
        RelativePattern relativePattern = globPattern.getRight();
        return tuple(relativePattern.getBaseUri().getRight(), relativePattern.getPattern());
      })
      .containsExactlyInAnyOrder(
        tuple(firstFolderUri, "**/*.bsl"),
        tuple(firstFolderUri, "**/*.os"),
        tuple(secondFolderUri, "**/*.bsl"),
        tuple(secondFolderUri, "**/*.os")
      );
  }

  @Test
  void initializedFallsBackToGlobalGlobWithoutRelativePatternSupport() {
    // given
    var client = mock(LanguageClient.class);
    when(client.registerCapability(any()))
      .thenReturn(CompletableFuture.completedFuture(null));
    languageClientHolder.connect(client);

    var initParams = new InitializeParams();
    var workspaceFolder = new WorkspaceFolder(Absolute.path(PATH_TO_METADATA).toUri().toString(), "test");
    initParams.setWorkspaceFolders(List.of(workspaceFolder));

    var capabilities = new ClientCapabilities();
    var workspace = new WorkspaceClientCapabilities();
    // dynamicRegistration declared, but relativePatternSupport is absent
    workspace.setDidChangeWatchedFiles(new DidChangeWatchedFilesCapabilities(true));
    capabilities.setWorkspace(workspace);
    initParams.setCapabilities(capabilities);

    server.initialize(initParams);

    // when
    server.initialized(new InitializedParams());

    // then
    var captor = ArgumentCaptor.forClass(RegistrationParams.class);
    verify(client).registerCapability(captor.capture());

    var options = (DidChangeWatchedFilesRegistrationOptions)
      captor.getValue().getRegistrations().get(0).getRegisterOptions();
    assertThat(options.getWatchers())
      .allSatisfy(watcher -> assertThat(watcher.getGlobPattern().isLeft()).isTrue())
      .extracting(FileSystemWatcher::getGlobPattern)
      .extracting(globPattern -> globPattern.getLeft())
      .containsExactlyInAnyOrder("**/*.bsl", "**/*.os");
  }

  @Test
  void initializedDoesNotRegisterFileWatchersWithoutDynamicRegistration() {
    // given
    var client = mock(LanguageClient.class);
    languageClientHolder.connect(client);

    var initParams = new InitializeParams();
    var workspaceFolder = new WorkspaceFolder(Absolute.path(PATH_TO_METADATA).toUri().toString(), "test");
    initParams.setWorkspaceFolders(List.of(workspaceFolder));
    // no workspace.didChangeWatchedFiles.dynamicRegistration declared

    server.initialize(initParams);

    // when
    server.initialized(new InitializedParams());

    // then
    verify(client, never()).registerCapability(any());
  }

  @Test
  void initializeDeclaresFileOperationsCapabilities() throws ExecutionException, InterruptedException {
    // given
    var params = new InitializeParams();
    var workspaceFolder = new WorkspaceFolder(Absolute.path(PATH_TO_METADATA).toUri().toString(), "test");
    params.setWorkspaceFolders(List.of(workspaceFolder));

    // when
    InitializeResult initialize = server.initialize(params).get();

    // then
    var fileOperations = initialize.getCapabilities().getWorkspace().getFileOperations();
    assertThat(fileOperations).isNotNull();
    assertThat(fileOperations.getDidCreate()).isNotNull();
    assertThat(fileOperations.getDidRename()).isNotNull();
    assertThat(fileOperations.getDidDelete()).isNotNull();

    assertThat(fileOperations.getDidDelete().getFilters())
      .extracting(filter -> filter.getPattern().getGlob())
      .containsExactlyInAnyOrder("**/*.bsl", "**/*.os", "**/*");
  }

  @Test
  @SuppressWarnings("deprecation")
  void initializeWithRootUri() throws ExecutionException, InterruptedException {
    // given
    InitializeParams params = new InitializeParams();
    params.setRootUri(Absolute.path(PATH_TO_METADATA).toUri().toString());
    // workspaceFolders not set, should fallback to rootUri

    // when
    InitializeResult initialize = server.initialize(params).get();

    // then
    assertThat(initialize.getCapabilities().getTextDocumentSync().getRight().getChange())
      .isEqualTo(TextDocumentSyncKind.Incremental);
  }

  @Test
  @SuppressWarnings("deprecation")
  void initializeWithRootPath() throws ExecutionException, InterruptedException {
    // given
    InitializeParams params = new InitializeParams();
    params.setRootPath(Absolute.path(PATH_TO_METADATA).toString());
    // workspaceFolders and rootUri not set, should fallback to rootPath

    // when
    InitializeResult initialize = server.initialize(params).get();

    // then
    assertThat(initialize.getCapabilities().getTextDocumentSync().getRight().getChange())
      .isEqualTo(TextDocumentSyncKind.Incremental);
  }

  @Test
  @SuppressWarnings("deprecation")
  void initializeWithEmptyWorkspaceFoldersAndRootUri() throws ExecutionException, InterruptedException {
    // given
    InitializeParams params = new InitializeParams();
    params.setWorkspaceFolders(List.of()); // Empty list
    params.setRootUri(Absolute.path(PATH_TO_METADATA).toUri().toString());
    // Empty workspaceFolders, should fallback to rootUri

    // when
    InitializeResult initialize = server.initialize(params).get();

    // then
    assertThat(initialize.getCapabilities().getTextDocumentSync().getRight().getChange())
      .isEqualTo(TextDocumentSyncKind.Incremental);
  }

  @Test
  void initializeWithNoWorkspaceInfo() throws ExecutionException, InterruptedException {
    // given
    InitializeParams params = new InitializeParams();
    // No workspaceFolders, rootUri, or rootPath - should handle gracefully

    // when
    InitializeResult initialize = server.initialize(params).get();

    // then
    // Should not throw, but no workspace will be configured
    assertThat(initialize.getCapabilities().getTextDocumentSync().getRight().getChange())
      .isEqualTo(TextDocumentSyncKind.Incremental);
  }

  /**
   * Группа тестов shutdown/exit изолирована в @Nested-классе с fullRefresh: их методы рвут
   * singleton-state (флаг shutdownWasCalled на BSLLanguageServer, плюс AsyncTaskExecutor,
   * закрываемый textDocumentService.reset() в shutdown()), которое lite-cleanup не пересоздаёт.
   * Полный Spring refresh между методами этой группы — единственный способ получить свежие
   * BSLLanguageServer и executor.
   */
  @Nested
  @CleanupContextBeforeClassAndAfterEachTestMethod(fullRefresh = true)
  class ShutdownAndExitTests {

    @Test
    void shutdown() throws ExecutionException, InterruptedException {
      CompletableFuture<Object> shutdownFuture = server.shutdown();

      assertThat(shutdownFuture.get()).isEqualTo(true);
    }

    @Test
    void exitWithoutShutdown() {
      // when-then
      assertThatThrownBy(() -> server.exit())
        .isInstanceOf(RuntimeException.class)
        .hasMessage("1");
    }

    @Test
    void exitWithShutdown() {
      // given
      server.shutdown();

      // when-then
      assertThatThrownBy(() -> server.exit())
        .isInstanceOf(RuntimeException.class)
        .hasMessage("0");
    }
  }

}
