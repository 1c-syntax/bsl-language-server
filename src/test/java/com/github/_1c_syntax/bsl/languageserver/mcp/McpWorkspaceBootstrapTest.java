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
package com.github._1c_syntax.bsl.languageserver.mcp;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.bsl.languageserver.utils.BSLFiles;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Юнит-тесты владения рабочими папками: явная регистрация и MCP roots не должны отбирать
 * папку друг у друга, а неудачная индексация — оставлять её зарегистрированной наполовину.
 * <p>
 * {@code ServerContextProvider} подменён, но ведёт себя как настоящий: держит карту папок и
 * реестр имён — иначе проверять состояние набора не на чем.
 */
class McpWorkspaceBootstrapTest {

  private static final Path CLI_DIR = Absolute.path("src/test/resources/cli");
  private static final Path MCP_DIR = Absolute.path("src/test/resources/mcp");

  private final LanguageServerConfiguration configuration = mock(LanguageServerConfiguration.class);
  private final ServerContextProvider serverContextProvider = mock(ServerContextProvider.class);
  private final ServerContext serverContext = mock(ServerContext.class);
  private final Map<URI, ServerContext> contexts = new LinkedHashMap<>();

  private final McpWorkspaceBootstrap bootstrap =
    new McpWorkspaceBootstrap(configuration, serverContextProvider);

  @BeforeEach
  void setUp() {
    when(serverContextProvider.getAllContexts()).thenReturn(contexts);
    when(serverContextProvider.addWorkspace(any(URI.class), nullable(String.class)))
      .thenAnswer(invocation -> {
        URI workspaceUri = invocation.getArgument(0);
        String name = invocation.getArgument(1);
        WorkspaceContextHolder.registerWorkspace(
          workspaceUri, name == null ? workspaceUri.toString() : name);
        contexts.put(workspaceUri, serverContext);
        return serverContext;
      });
    doAnswer(invocation -> {
      WorkspaceFolder folder = invocation.getArgument(0);
      // Ровно как настоящий removeWorkspace: ищет запись, повторно приводя URI к каноническому
      // виду. Через URI.create стаб был бы добрее реальности и прятал бы потерю идентичности.
      var workspaceUri = Absolute.uri(folder.getUri());
      contexts.remove(workspaceUri);
      WorkspaceContextHolder.unregisterWorkspace(workspaceUri);
      return null;
    }).when(serverContextProvider).removeWorkspace(any(WorkspaceFolder.class));
  }

  @AfterEach
  void tearDown() {
    // Реестр имён статический: не почистим — утечёт в соседние тесты этого же форка.
    List.copyOf(contexts.keySet()).forEach(WorkspaceContextHolder::unregisterWorkspace);
    contexts.clear();
    WorkspaceContextHolder.clear();
  }

  @Test
  void failedIndexingLeavesNoHalfRegisteredFolder() {
    doThrow(new IllegalStateException("boom")).when(serverContext).populateContext(anyCollection());

    assertThatThrownBy(() -> bootstrap.register(CLI_DIR, null))
      .isInstanceOf(IllegalStateException.class);

    assertThat(contexts).doesNotContainKey(CLI_DIR.toUri());
  }

  @Test
  void folderIsIndexedAgainAfterFailedRegistration() {
    doThrow(new IllegalStateException("boom")).when(serverContext).populateContext(anyCollection());
    assertThatThrownBy(() -> bootstrap.register(CLI_DIR, null)).isInstanceOf(IllegalStateException.class);

    doNothing().when(serverContext).populateContext(anyCollection());

    // Повторная попытка обязана снова индексировать: иначе клиенту вернулось бы
    // «уже зарегистрирована» для папки, которой в контексте нет.
    assertThat(bootstrap.register(CLI_DIR, null)).isFalse();
    assertThat(contexts).containsKey(CLI_DIR.toUri());
  }

  @Test
  void secondRegistrationDoesNotReindex() {
    assertThat(bootstrap.register(CLI_DIR, null)).isFalse();
    assertThat(bootstrap.register(CLI_DIR, null)).isTrue();
  }

  @Test
  void explicitlyRegisteredFolderSurvivesDisappearingRoot() {
    bootstrap.syncRoots(List.of(CLI_DIR));
    bootstrap.register(CLI_DIR, null);

    bootstrap.syncRoots(List.of());

    assertThat(contexts).containsKey(CLI_DIR.toUri());
  }

  @Test
  void rootOwnedFolderSurvivesUnregisterTool() {
    bootstrap.register(CLI_DIR, null);
    bootstrap.syncRoots(List.of(CLI_DIR));

    assertThat(bootstrap.unregister(CLI_DIR.toUri())).isFalse();
    assertThat(contexts).containsKey(CLI_DIR.toUri());

    // Владелец остался один: с исчезновением корня папка уходит.
    bootstrap.syncRoots(List.of());
    assertThat(contexts).doesNotContainKey(CLI_DIR.toUri());
  }

  @Test
  void folderWithoutOtherOwnersIsRemovedByUnregisterTool() {
    bootstrap.register(CLI_DIR, null);

    assertThat(bootstrap.unregister(CLI_DIR.toUri())).isTrue();
    assertThat(contexts).doesNotContainKey(CLI_DIR.toUri());
  }

  @Test
  void folderAddedOutsideMcpIsNotUnregistered() {
    // Так папка попадает в контекст от LSP-клиента: мимо этого бина.
    var lspFolder = addWorkspaceOutsideMcp(CLI_DIR);

    assertThatThrownBy(() -> bootstrap.unregister(lspFolder))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("was not registered through MCP")
      .hasMessageContaining("register_workspace_folder");
    assertThat(contexts).containsKey(lspFolder);
  }

  @Test
  void disappearedRootDoesNotTakeAwayFolderAddedOutsideMcp() {
    var lspFolder = addWorkspaceOutsideMcp(CLI_DIR);

    // Корень лишь сослался на уже существующую папку редактора — забирать её он не вправе.
    bootstrap.syncRoots(List.of(CLI_DIR));
    bootstrap.syncRoots(List.of());

    assertThat(contexts).containsKey(lspFolder);
  }

  @Test
  void folderIsUnregisteredByItsRegisteredUri() throws IOException {
    var srcDir = tempWorkspaceDirectory();
    bootstrap.register(srcDir, null);

    assertThat(bootstrap.unregister(srcDir.toUri())).isTrue();
    assertThat(contexts).doesNotContainKey(srcDir.toUri());
  }

  @Test
  void unregisterOfFolderWithGoneDirectoryFailsInsteadOfLeakingSilently() throws IOException {
    var srcDir = tempWorkspaceDirectory();
    bootstrap.register(srcDir, null);
    var registered = srcDir.toUri();

    // Каталога больше нет: и Path.toUri(), и Absolute.uri теряют завершающий слэш, поэтому
    // removeWorkspace не находит запись по ключу реестра.
    Files.delete(srcDir);
    assertThat(Absolute.uri(registered.toString())).isNotEqualTo(registered);

    // Отвечать «удалено», оставив папку в контексте, нельзя: клиент должен узнать правду.
    assertThatThrownBy(() -> bootstrap.unregister(registered))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("stays registered");
    assertThat(contexts).containsKey(registered);

    // Владение сохранено: повторная попытка не выглядит обращением к чужой папке.
    assertThatThrownBy(() -> bootstrap.unregister(registered))
      .isInstanceOf(IllegalStateException.class);
  }

  /**
   * Временный каталог в том виде, в котором его увидит контекст сервера.
   * <p>
   * Канонизация обязательна: на macOS {@code /var} — симлинк на {@code /private/var}, поэтому сырой
   * путь из {@code createTempDirectory} дал бы ключ реестра, который {@code removeWorkspace} затем
   * не нашёл бы — он канонизирует URI ещё раз.
   */
  private static Path tempWorkspaceDirectory() throws IOException {
    return Absolute.path(Files.createTempDirectory("mcp-workspace"));
  }

  private URI addWorkspaceOutsideMcp(Path srcDir) {
    var workspaceUri = srcDir.toUri();
    serverContextProvider.addWorkspace(workspaceUri, "lsp");
    return workspaceUri;
  }

  @Test
  void rootThatDisappearedIsRemoved() {
    bootstrap.syncRoots(List.of(CLI_DIR, MCP_DIR));
    assertThat(contexts).containsKeys(CLI_DIR.toUri(), MCP_DIR.toUri());

    bootstrap.syncRoots(List.of(MCP_DIR));

    assertThat(contexts)
      .doesNotContainKey(CLI_DIR.toUri())
      .containsKey(MCP_DIR.toUri());
  }

  @Test
  void failedRootIndexingIsRetriedOnNextSync() {
    doThrow(new IllegalStateException("boom")).when(serverContext).populateContext(anyCollection());
    bootstrap.syncRoots(List.of(CLI_DIR));
    assertThat(contexts).doesNotContainKey(CLI_DIR.toUri());

    doNothing().when(serverContext).populateContext(anyCollection());
    bootstrap.syncRoots(List.of(CLI_DIR));

    assertThat(contexts).containsKey(CLI_DIR.toUri());
  }

  @Test
  void ownershipIsForgottenWhenFolderLeavesContextBehindOurBack() {
    bootstrap.register(CLI_DIR, null);

    // Папку убрали мимо этого бина — так делает LSP-клиент через didChangeWorkspaceFolders.
    contexts.remove(CLI_DIR.toUri());
    WorkspaceContextHolder.unregisterWorkspace(CLI_DIR.toUri());

    bootstrap.syncRoots(List.of(CLI_DIR));
    assertThat(contexts).containsKey(CLI_DIR.toUri());

    // Просроченное явное владение не должно удерживать папку, зарегистрированную заново.
    bootstrap.syncRoots(List.of());
    assertThat(contexts).doesNotContainKey(CLI_DIR.toUri());
  }

  @Test
  void indexReportsNumberOfIndexedFiles() {
    assertThat(bootstrap.index(CLI_DIR))
      .isEqualTo(BSLFiles.listBslFiles(CLI_DIR, null).size());
  }
}
