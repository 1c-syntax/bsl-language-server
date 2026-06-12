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
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.RenameCapabilities;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class BSLLanguageServerTest {

  @Autowired
  private BSLLanguageServer server;

  @Autowired
  private GlobalLanguageServerConfiguration globalConfiguration;

  @BeforeEach
  void setUp() {
    new MockUp<System>() {
      @Mock
      public void exit(int value) {
        throw new RuntimeException(String.valueOf(value));
      }
    };
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
   * singleton-state (флаг shutdownWasCalled на BSLLanguageServer, плюс ThreadPoolTaskExecutor,
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
