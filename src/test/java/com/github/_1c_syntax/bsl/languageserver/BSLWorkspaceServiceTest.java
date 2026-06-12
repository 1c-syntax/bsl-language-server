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

import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextContentChangedEvent;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.utils.Absolute;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.CreateFilesParams;
import org.eclipse.lsp4j.DeleteFilesParams;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams;
import org.eclipse.lsp4j.FileChangeType;
import org.eclipse.lsp4j.FileCreate;
import org.eclipse.lsp4j.FileDelete;
import org.eclipse.lsp4j.FileEvent;
import org.eclipse.lsp4j.FileRename;
import org.eclipse.lsp4j.RenameFilesParams;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.WorkspaceFoldersChangeEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.SmartApplicationListener;
import org.springframework.core.Ordered;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Тесты для {@link BSLWorkspaceService}.
 */
@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class BSLWorkspaceServiceTest {

  private static final File FIXTURE_BSL = new File("./src/test/resources/cli/test.bsl");
  private static final File FIXTURE_FORMATTABLE_BSL = new File("./src/test/resources/providers/format.bsl");

  @Autowired
  private BSLWorkspaceService workspaceService;

  @Autowired
  private ServerContextProvider serverContextProvider;

  @Autowired
  private ConfigurableApplicationContext applicationContext;

  @TempDir
  Path tempDir;

  @BeforeEach
  void setUp() {
    // Register workspace for temp directory
    var workspaceFolder = new org.eclipse.lsp4j.WorkspaceFolder(tempDir.toUri().toString(), "test-workspace");
    serverContextProvider.addWorkspace(workspaceFolder);
  }

  @Test
  void testDidChangeWatchedFiles_Created_NotOpened() throws IOException {
    // given
    var testFile = createTestFile("test_created.bsl");
    var uri = Absolute.uri(testFile.toURI());

    var fileEvent = new FileEvent(uri.toString(), FileChangeType.Created);
    var params = new DidChangeWatchedFilesParams(List.of(fileEvent));

    // when
    workspaceService.didChangeWatchedFiles(params);
    await().until(() -> serverContextProvider.getServerContext(uri).map(c -> c.getDocument(uri)).orElse(null) != null);

    // then
    var documentContext = serverContextProvider.getServerContext(uri).map(c -> c.getDocument(uri)).orElse(null);
    assertThat(documentContext).isNotNull();
    assertThat(serverContextProvider.getServerContext(uri).map(c -> c.isDocumentOpened(documentContext)).orElse(false)).isFalse();
  }

  @Test
  void testDidChangeWatchedFiles_Created_AlreadyOpened() throws IOException {
    // given
    var testFile = createTestFile("test_created_opened.bsl");
    var uri = Absolute.uri(testFile.toURI());
    var content = FileUtils.readFileToString(testFile, StandardCharsets.UTF_8);

    var ctx = serverContextProvider.getServerContext(uri).orElseThrow();
    var documentContext = ctx.addDocument(uri);
    ctx.openDocument(documentContext, content, 1);

    var fileEvent = new FileEvent(uri.toString(), FileChangeType.Created);
    var params = new DidChangeWatchedFilesParams(List.of(fileEvent));

    // when
    assertThatCode(() -> workspaceService.didChangeWatchedFiles(params)).doesNotThrowAnyException();

    // then
    // Для открытого файла событие Created должно быть проигнорировано
    // Документ должен остаться в контексте
    assertThat(serverContextProvider.getServerContext(uri).map(c -> c.getDocument(uri))).isPresent();
  }

  @Test
  void testDidChangeWatchedFiles_Changed_NotOpened() throws IOException {
    // given
    var testFile = createTestFile("test_changed.bsl");
    var uri = Absolute.uri(testFile.toURI());

    var ctx = serverContextProvider.getServerContext(uri).orElseThrow();
    var documentContext = ctx.addDocument(uri);
    ctx.rebuildDocument(documentContext);
    ctx.tryClearDocument(documentContext);

    FileUtils.copyFile(FIXTURE_FORMATTABLE_BSL, testFile);

    var fileEvent = new FileEvent(uri.toString(), FileChangeType.Changed);
    var params = new DidChangeWatchedFilesParams(List.of(fileEvent));

    // when
    workspaceService.didChangeWatchedFiles(params);

    // then
    assertThat(serverContextProvider.getServerContext(uri).map(c -> c.getDocument(uri))).isPresent();
    assertThat(serverContextProvider.getServerContext(uri).map(c -> c.isDocumentOpened(documentContext)).orElse(false)).isFalse();
  }

  @Test
  void testDidChangeWatchedFiles_Changed_Opened() throws IOException {
    // given
    var testFile = createTestFile("test_changed_opened.bsl");
    var uri = Absolute.uri(testFile.toURI());
    var content = FileUtils.readFileToString(testFile, StandardCharsets.UTF_8);

    var ctx = serverContextProvider.getServerContext(uri).orElseThrow();
    var documentContext = ctx.addDocument(uri);
    ctx.openDocument(documentContext, content, 1);

    FileUtils.copyFile(FIXTURE_FORMATTABLE_BSL, testFile);

    var fileEvent = new FileEvent(uri.toString(), FileChangeType.Changed);
    var params = new DidChangeWatchedFilesParams(List.of(fileEvent));

    // when
    workspaceService.didChangeWatchedFiles(params);

    // then
    // Для открытого файла событие Changed должно быть проигнорировано
    // Документ должен остаться в контексте
    assertThat(serverContextProvider.getServerContext(uri).map(c -> c.getDocument(uri))).isPresent();
  }

  @Test
  void testDidChangeWatchedFiles_Changed_UnknownFile() throws IOException {
    // given
    var testFile = createTestFile("test_changed_unknown.bsl");
    var uri = Absolute.uri(testFile.toURI());

    assertThat(serverContextProvider.getServerContext(uri).map(c -> c.getDocument(uri))).isEmpty();

    var fileEvent = new FileEvent(uri.toString(), FileChangeType.Changed);
    var params = new DidChangeWatchedFilesParams(List.of(fileEvent));

    // when
    workspaceService.didChangeWatchedFiles(params);
    await().until(() -> serverContextProvider.getServerContext(uri).map(c -> c.getDocument(uri)).orElse(null) != null);

    // then
    var documentContext = serverContextProvider.getServerContext(uri).map(c -> c.getDocument(uri)).orElse(null);
    assertThat(documentContext).isNotNull();
  }

  @Test
  void testDidChangeWatchedFiles_Deleted_NotOpened() throws IOException {
    // given
    var testFile = createTestFile("test_deleted.bsl");
    var uri = Absolute.uri(testFile.toURI());

    var ctx = serverContextProvider.getServerContext(uri).orElseThrow();
    var documentContext = ctx.addDocument(uri);
    ctx.rebuildDocument(documentContext);
    ctx.tryClearDocument(documentContext);

    assertThat(serverContextProvider.getServerContext(uri).map(c -> c.getDocument(uri))).isPresent();

    var fileEvent = new FileEvent(uri.toString(), FileChangeType.Deleted);
    var params = new DidChangeWatchedFilesParams(List.of(fileEvent));

    // when
    workspaceService.didChangeWatchedFiles(params);
    await().until(() -> serverContextProvider.getServerContext(uri).map(c -> c.getDocument(uri)).orElse(null) == null);

    // then
    assertThat(serverContextProvider.getServerContext(uri).map(c -> c.getDocument(uri))).isEmpty();
  }

  @Test
  void testDidChangeWatchedFiles_Deleted_Opened() throws IOException {
    // given
    var testFile = createTestFile("test_deleted_opened.bsl");
    var uri = Absolute.uri(testFile.toURI());
    var content = FileUtils.readFileToString(testFile, StandardCharsets.UTF_8);

    var ctx = serverContextProvider.getServerContext(uri).orElseThrow();
    var documentContext = ctx.addDocument(uri);
    ctx.openDocument(documentContext, content, 1);

    assertThat(serverContextProvider.getServerContext(uri).map(c -> c.getDocument(uri))).isPresent();

    var fileEvent = new FileEvent(uri.toString(), FileChangeType.Deleted);
    var params = new DidChangeWatchedFilesParams(List.of(fileEvent));

    // when
    workspaceService.didChangeWatchedFiles(params);
    await().until(() -> serverContextProvider.getServerContext(uri).map(c -> c.getDocument(uri)).orElse(null) == null);

    // then
    assertThat(serverContextProvider.getServerContext(uri).map(c -> c.getDocument(uri))).isEmpty();
  }

  @Test
  void testDidChangeWatchedFiles_Deleted_UnknownFile() {
    // given
    var uri = URI.create("file:///nonexistent.bsl");

    var fileEvent = new FileEvent(uri.toString(), FileChangeType.Deleted);
    var params = new DidChangeWatchedFilesParams(List.of(fileEvent));

    // when
    assertThatCode(() -> workspaceService.didChangeWatchedFiles(params)).doesNotThrowAnyException();

    // then
    // Не должно быть исключений
    assertThat(serverContextProvider.getServerContext(uri).map(c -> c.getDocument(uri))).isEmpty();
  }

  /**
   * Клиенты (VS Code) при удалении каталога присылают одно событие Deleted с URI каталога,
   * без событий по вложенным файлам. Все документы внутри каталога должны быть выгружены
   * из контекста, а документы каталога-«тёзки» с общим строковым префиксом — остаться.
   */
  @Test
  void testDidChangeWatchedFiles_Deleted_Folder() throws IOException {
    // given
    var objectModule = createTestFile("Catalogs/Goods/Ext/ObjectModule.bsl");
    var formModule = createTestFile("Catalogs/Goods/Forms/ItemForm/Ext/Form/Module.bsl");
    var namesakeModule = createTestFile("Catalogs/GoodsArchive/Ext/ObjectModule.bsl");

    var objectModuleUri = Absolute.uri(objectModule.toURI());
    var formModuleUri = Absolute.uri(formModule.toURI());
    var namesakeModuleUri = Absolute.uri(namesakeModule.toURI());

    var ctx = workspaceServerContext();
    for (var uri : List.of(objectModuleUri, formModuleUri, namesakeModuleUri)) {
      var documentContext = ctx.addDocument(uri);
      ctx.rebuildDocument(documentContext);
      ctx.tryClearDocument(documentContext);
    }

    var deletedFolder = tempDir.resolve("Catalogs").resolve("Goods").toFile();
    FileUtils.deleteDirectory(deletedFolder);
    var folderUri = Absolute.uri(deletedFolder.toURI());

    var fileEvent = new FileEvent(folderUri.toString(), FileChangeType.Deleted);
    var params = new DidChangeWatchedFilesParams(List.of(fileEvent));

    // when
    workspaceService.didChangeWatchedFiles(params);
    await().until(() -> ctx.getDocument(objectModuleUri) == null);

    // then
    assertThat(ctx.getDocument(objectModuleUri)).isNull();
    assertThat(ctx.getDocument(formModuleUri)).isNull();
    assertThat(ctx.getDocument(namesakeModuleUri)).isNotNull();
  }

  /** Открытый в редакторе документ внутри удалённого каталога тоже должен быть закрыт и выгружен. */
  @Test
  void testDidChangeWatchedFiles_Deleted_FolderWithOpenedDocument() throws IOException {
    // given
    var openedModule = createTestFile("Catalogs/Products/Ext/ObjectModule.bsl");
    var openedModuleUri = Absolute.uri(openedModule.toURI());
    var content = FileUtils.readFileToString(openedModule, StandardCharsets.UTF_8);

    var ctx = workspaceServerContext();
    var documentContext = ctx.addDocument(openedModuleUri);
    ctx.openDocument(documentContext, content, 1);

    var deletedFolder = tempDir.resolve("Catalogs").resolve("Products").toFile();
    FileUtils.deleteDirectory(deletedFolder);
    var folderUri = Absolute.uri(deletedFolder.toURI());

    var fileEvent = new FileEvent(folderUri.toString(), FileChangeType.Deleted);
    var params = new DidChangeWatchedFilesParams(List.of(fileEvent));

    // when
    workspaceService.didChangeWatchedFiles(params);
    await().until(() -> ctx.getDocument(openedModuleUri) == null);

    // then
    assertThat(ctx.getDocument(openedModuleUri)).isNull();
  }

  @Test
  void testDidChangeWatchedFiles_MultipleEvents() throws IOException {
    // given
    var file1 = createTestFile("test_multiple_1.bsl");
    var file2 = createTestFile("test_multiple_2.bsl");
    var file3 = createTestFile("test_multiple_3.bsl");

    var uri1 = Absolute.uri(file1.toURI());
    var uri2 = Absolute.uri(file2.toURI());
    var uri3 = Absolute.uri(file3.toURI());

    var ctx = serverContextProvider.getServerContext(uri2).orElseThrow();
    var documentContext2 = ctx.addDocument(uri2);
    ctx.rebuildDocument(documentContext2);

    // file3 добавляем в контекст, чтобы проверить его удаление
    var documentContext3 = ctx.addDocument(uri3);
    ctx.rebuildDocument(documentContext3);

    var events = List.of(
      new FileEvent(uri1.toString(), FileChangeType.Created),
      new FileEvent(uri2.toString(), FileChangeType.Changed),
      new FileEvent(uri3.toString(), FileChangeType.Deleted)
    );
    var params = new DidChangeWatchedFilesParams(events);

    // when
    workspaceService.didChangeWatchedFiles(params);
    await().until(() ->
      serverContextProvider.getServerContext(uri1).map(c -> c.getDocument(uri1)).orElse(null) != null &&
      serverContextProvider.getServerContext(uri2).map(c -> c.getDocument(uri2)).orElse(null) != null &&
      serverContextProvider.getServerContext(uri3).map(c -> c.getDocument(uri3)).orElse(null) == null
    );

    // then
    assertThat(serverContextProvider.getServerContext(uri1).map(c -> c.getDocument(uri1))).isPresent();
    assertThat(serverContextProvider.getServerContext(uri2).map(c -> c.getDocument(uri2))).isPresent();
    assertThat(serverContextProvider.getServerContext(uri3).map(c -> c.getDocument(uri3))).isEmpty();
  }

  /** Событие создания файла внутри excluded-каталога не приводит к добавлению его в контекст. */
  @Test
  void testDidChangeWatchedFiles_Created_ExcludedPath() throws IOException {
    // given
    var workspaceUri = Absolute.uri(tempDir.toUri());
    WorkspaceContextHolder.run(workspaceUri, () -> {
      var wsCtx = workspaceServerContext();
      wsCtx.setConfigurationRoot(tempDir);
      wsCtx.getLanguageServerConfiguration().setExcludePaths(List.of(".git"));
    });

    var excludedDir = tempDir.resolve(".git").toFile();
    assertThat(excludedDir.mkdirs()).isTrue();
    var testFile = new File(excludedDir, "excluded.bsl");
    FileUtils.copyFile(FIXTURE_BSL, testFile);
    var uri = Absolute.uri(testFile.toURI());

    var fileEvent = new FileEvent(uri.toString(), FileChangeType.Created);

    // when
    workspaceService.didChangeWatchedFiles(new DidChangeWatchedFilesParams(List.of(fileEvent)));

    // then
    await()
      .atMost(Duration.ofSeconds(2))
      .during(Duration.ofMillis(300))
      .until(() -> workspaceServerContext().getDocument(uri) == null);
  }

  /** Событие изменения для неизвестного файла внутри excluded-каталога — файл не добавляется. */
  @Test
  void testDidChangeWatchedFiles_Changed_ExcludedPathNotAdded() throws IOException {
    // given
    var workspaceUri = Absolute.uri(tempDir.toUri());
    WorkspaceContextHolder.run(workspaceUri, () -> {
      var wsCtx = workspaceServerContext();
      wsCtx.setConfigurationRoot(tempDir);
      wsCtx.getLanguageServerConfiguration().setExcludePaths(List.of(".git"));
    });

    var excludedDir = tempDir.resolve(".git").toFile();
    assertThat(excludedDir.mkdirs()).isTrue();
    var testFile = new File(excludedDir, "excluded_changed.bsl");
    FileUtils.copyFile(FIXTURE_BSL, testFile);
    var uri = Absolute.uri(testFile.toURI());

    assertThat(workspaceServerContext().getDocument(uri)).isNull();

    var fileEvent = new FileEvent(uri.toString(), FileChangeType.Changed);

    // when
    workspaceService.didChangeWatchedFiles(new DidChangeWatchedFilesParams(List.of(fileEvent)));

    // then
    await()
      .atMost(Duration.ofSeconds(2))
      .during(Duration.ofMillis(300))
      .until(() -> workspaceServerContext().getDocument(uri) == null);
  }

  @Test
  void testDidChangeConfiguration_WithNullSettings() {
    // given
    var params = mock(DidChangeConfigurationParams.class);
    when(params.getSettings()).thenReturn(null);

    // when / then
    assertThatCode(() -> workspaceService.didChangeConfiguration(params))
      .doesNotThrowAnyException();
  }

  @Test
  void testDidChangeWorkspaceFolders_AddWorkspace() {
    // given
    var newWorkspaceDir = tempDir.resolve("new-workspace").toFile();
    newWorkspaceDir.mkdir();
    var workspaceFolder = new WorkspaceFolder(newWorkspaceDir.toURI().toString(), "new-workspace");

    var event = new WorkspaceFoldersChangeEvent(
      List.of(workspaceFolder),
      List.of()
    );
    var params = new DidChangeWorkspaceFoldersParams(event);

    // when
    workspaceService.didChangeWorkspaceFolders(params);
    await().pollDelay(Duration.ofMillis(100)).until(() -> true);

    // then
    var uri = Absolute.uri(newWorkspaceDir.toURI());
    assertThat(serverContextProvider.getServerContext(uri)).isPresent();

    // cleanup
    serverContextProvider.removeWorkspace(workspaceFolder);
  }

  @Test
  void testDidChangeWorkspaceFolders_RemoveWorkspace() {
    // given
    var workspaceFolder = new WorkspaceFolder(tempDir.toUri().toString(), "test-workspace");

    var event = new WorkspaceFoldersChangeEvent(
      List.of(),
      List.of(workspaceFolder)
    );
    var params = new DidChangeWorkspaceFoldersParams(event);

    // when
    workspaceService.didChangeWorkspaceFolders(params);
    await().pollDelay(Duration.ofMillis(100)).until(() -> true);

    // then
    var uri = Absolute.uri(tempDir.toUri());
    assertThat(serverContextProvider.getServerContext(uri)).isEmpty();
  }

  @Test
  void testDidChangeWorkspaceFolders_AddAndRemove() {
    // given
    var newWorkspaceDir = tempDir.resolve("new-workspace-2").toFile();
    newWorkspaceDir.mkdir();
    var workspaceFolderToAdd = new WorkspaceFolder(newWorkspaceDir.toURI().toString(), "new-workspace-2");
    var workspaceFolderToRemove = new WorkspaceFolder(tempDir.toUri().toString(), "test-workspace");

    var event = new WorkspaceFoldersChangeEvent(
      List.of(workspaceFolderToAdd),
      List.of(workspaceFolderToRemove)
    );
    var params = new DidChangeWorkspaceFoldersParams(event);

    // when
    workspaceService.didChangeWorkspaceFolders(params);
    await().pollDelay(Duration.ofMillis(100)).until(() -> true);

    // then
    var uriAdded = Absolute.uri(newWorkspaceDir.toURI());
    var uriRemoved = Absolute.uri(tempDir.toUri());
    assertThat(serverContextProvider.getServerContext(uriAdded)).isPresent();
    assertThat(serverContextProvider.getServerContext(uriRemoved)).isEmpty();

    // cleanup
    serverContextProvider.removeWorkspace(workspaceFolderToAdd);
  }

  /**
   * Воспроизводит ScopeNotActiveException из Sentry:
   * при обработке файловых событий через didChangeWatchedFiles workspace-контекст
   * должен быть установлен на треде, иначе workspace-scoped proxy beans не резолвятся
   * в @EventListener методах (например, AnnotationReferenceFinder).
   */
  @Test
  void didChangeWatchedFiles_Created_setsWorkspaceContextForEventListeners() throws IOException, InterruptedException {
    // given
    var capturedWorkspaceUri = new AtomicReference<URI>();
    var eventFired = new CountDownLatch(1);

    // Регистрируем высокоприоритетный слушатель, чтобы получить контекст до AnnotationReferenceFinder
    var listener = new SmartApplicationListener() {
      @Override
      public boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
        return DocumentContextContentChangedEvent.class.isAssignableFrom(eventType);
      }

      @Override
      public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
      }

      @Override
      public void onApplicationEvent(ApplicationEvent event) {
        capturedWorkspaceUri.set(WorkspaceContextHolder.get());
        eventFired.countDown();
      }
    };
    applicationContext.addApplicationListener(listener);

    var testFile = createTestFile("test_scope_bug.bsl");
    var uri = Absolute.uri(testFile.toURI());
    var fileEvent = new FileEvent(uri.toString(), FileChangeType.Created);

    try {
      // when
      workspaceService.didChangeWatchedFiles(new DidChangeWatchedFilesParams(List.of(fileEvent)));

      // then - ждём что событие сработало и проверяем контекст
      assertThat(eventFired.await(5, TimeUnit.SECONDS))
        .as("DocumentContextContentChangedEvent should be fired within 5 seconds")
        .isTrue();
      assertThat(capturedWorkspaceUri.get())
        .as("Workspace context must be set on the thread when DocumentContextContentChangedEvent fires "
          + "(otherwise workspace-scoped beans like AnnotationRepository cannot be resolved)")
        .isNotNull()
        .isEqualTo(Absolute.uri(tempDir.toUri()));
    } finally {
      applicationContext.removeApplicationListener(listener);
    }
  }

  @Test
  void testDidCreateFiles_AddsDocument() throws IOException {
    // given
    var testFile = createTestFile("created_via_fileops.bsl");
    var uri = Absolute.uri(testFile.toURI());

    var fileCreate = new FileCreate(uri.toString());
    var params = new CreateFilesParams(List.of(fileCreate));

    // when
    workspaceService.didCreateFiles(params);
    await().until(() -> workspaceServerContext().getDocument(uri) != null);

    // then
    var documentContext = workspaceServerContext().getDocument(uri);
    assertThat(documentContext).isNotNull();
    assertThat(workspaceServerContext().isDocumentOpened(documentContext)).isFalse();
  }

  /** Создание файла внутри excluded-каталога через didCreateFiles не добавляет его в контекст. */
  @Test
  void testDidCreateFiles_ExcludedPath() throws IOException {
    // given
    var workspaceUri = Absolute.uri(tempDir.toUri());
    WorkspaceContextHolder.run(workspaceUri, () -> {
      var wsCtx = workspaceServerContext();
      wsCtx.setConfigurationRoot(tempDir);
      wsCtx.getLanguageServerConfiguration().setExcludePaths(List.of(".git"));
    });

    var excludedDir = tempDir.resolve(".git").toFile();
    assertThat(excludedDir.mkdirs()).isTrue();
    var testFile = new File(excludedDir, "excluded_fileops.bsl");
    FileUtils.copyFile(FIXTURE_BSL, testFile);
    var uri = Absolute.uri(testFile.toURI());

    var params = new CreateFilesParams(List.of(new FileCreate(uri.toString())));

    // when
    workspaceService.didCreateFiles(params);

    // then
    await()
      .atMost(Duration.ofSeconds(2))
      .during(Duration.ofMillis(300))
      .until(() -> workspaceServerContext().getDocument(uri) == null);
  }

  @Test
  void testDidDeleteFiles_RemovesDocument() throws IOException {
    // given
    var testFile = createTestFile("deleted_via_fileops.bsl");
    var uri = Absolute.uri(testFile.toURI());

    var ctx = workspaceServerContext();
    var documentContext = ctx.addDocument(uri);
    ctx.rebuildDocument(documentContext);
    ctx.tryClearDocument(documentContext);

    assertThat(ctx.getDocument(uri)).isNotNull();

    var params = new DeleteFilesParams(List.of(new FileDelete(uri.toString())));

    // when
    workspaceService.didDeleteFiles(params);
    await().until(() -> workspaceServerContext().getDocument(uri) == null);

    // then
    assertThat(workspaceServerContext().getDocument(uri)).isNull();
  }

  /**
   * При удалении каталога через didDeleteFiles присылается один URI каталога без вложенных файлов.
   * Все документы внутри каталога должны быть выгружены, а каталог-«тёзка» — остаться.
   */
  @Test
  void testDidDeleteFiles_Folder() throws IOException {
    // given
    var objectModule = createTestFile("Catalogs/Wares/Ext/ObjectModule.bsl");
    var namesakeModule = createTestFile("Catalogs/WaresArchive/Ext/ObjectModule.bsl");

    var objectModuleUri = Absolute.uri(objectModule.toURI());
    var namesakeModuleUri = Absolute.uri(namesakeModule.toURI());

    var ctx = workspaceServerContext();
    for (var uri : List.of(objectModuleUri, namesakeModuleUri)) {
      var documentContext = ctx.addDocument(uri);
      ctx.rebuildDocument(documentContext);
      ctx.tryClearDocument(documentContext);
    }

    var deletedFolder = tempDir.resolve("Catalogs").resolve("Wares").toFile();
    FileUtils.deleteDirectory(deletedFolder);
    var folderUri = Absolute.uri(deletedFolder.toURI());

    var params = new DeleteFilesParams(List.of(new FileDelete(folderUri.toString())));

    // when
    workspaceService.didDeleteFiles(params);
    await().until(() -> ctx.getDocument(objectModuleUri) == null);

    // then
    assertThat(ctx.getDocument(objectModuleUri)).isNull();
    assertThat(ctx.getDocument(namesakeModuleUri)).isNotNull();
  }

  @Test
  void testDidRenameFiles_MovesDocument() throws IOException {
    // given
    var oldFile = createTestFile("rename_old.bsl");
    var oldUri = Absolute.uri(oldFile.toURI());

    var ctx = workspaceServerContext();
    var documentContext = ctx.addDocument(oldUri);
    ctx.rebuildDocument(documentContext);
    ctx.tryClearDocument(documentContext);

    var newFile = tempDir.resolve("rename_new.bsl").toFile();
    FileUtils.moveFile(oldFile, newFile);
    var newUri = Absolute.uri(newFile.toURI());

    var fileRename = new FileRename(oldUri.toString(), newUri.toString());
    var params = new RenameFilesParams(List.of(fileRename));

    // when
    workspaceService.didRenameFiles(params);
    await().until(() -> workspaceServerContext().getDocument(newUri) != null
      && workspaceServerContext().getDocument(oldUri) == null);

    // then
    assertThat(workspaceServerContext().getDocument(oldUri)).isNull();
    assertThat(workspaceServerContext().getDocument(newUri)).isNotNull();
  }

  /**
   * Клиенты (VS Code) при создании каталога присылают одно событие с URI каталога без событий
   * по вложенным файлам. Все BSL/OS-файлы внутри каталога должны быть добавлены в контекст.
   */
  @Test
  void testDidCreateFiles_Folder() throws IOException {
    // given
    var objectModule = createTestFile("Catalogs/Items/Ext/ObjectModule.bsl");
    var formModule = createTestFile("Catalogs/Items/Forms/ItemForm/Ext/Form/Module.bsl");

    var objectModuleUri = Absolute.uri(objectModule.toURI());
    var formModuleUri = Absolute.uri(formModule.toURI());

    var ctx = workspaceServerContext();
    assertThat(ctx.getDocument(objectModuleUri)).isNull();
    assertThat(ctx.getDocument(formModuleUri)).isNull();

    var createdFolder = tempDir.resolve("Catalogs").resolve("Items").toFile();
    var folderUri = Absolute.uri(createdFolder.toURI());

    var params = new CreateFilesParams(List.of(new FileCreate(folderUri.toString())));

    // when
    workspaceService.didCreateFiles(params);
    await().until(() -> ctx.getDocument(objectModuleUri) != null && ctx.getDocument(formModuleUri) != null);

    // then
    assertThat(ctx.getDocument(objectModuleUri)).isNotNull();
    assertThat(ctx.getDocument(formModuleUri)).isNotNull();
  }

  /**
   * При переименовании каталога присылается один FileRename с URI каталога без вложенных файлов.
   * Документы внутри каталога должны появиться в контексте по новым URI и исчезнуть по старым.
   */
  @Test
  void testDidRenameFiles_Folder() throws IOException {
    // given
    var oldObjectModule = createTestFile("Catalogs/Orders/Ext/ObjectModule.bsl");
    var oldFormModule = createTestFile("Catalogs/Orders/Forms/ItemForm/Ext/Form/Module.bsl");

    var oldObjectModuleUri = Absolute.uri(oldObjectModule.toURI());
    var oldFormModuleUri = Absolute.uri(oldFormModule.toURI());

    var ctx = workspaceServerContext();
    for (var uri : List.of(oldObjectModuleUri, oldFormModuleUri)) {
      var documentContext = ctx.addDocument(uri);
      ctx.rebuildDocument(documentContext);
      ctx.tryClearDocument(documentContext);
    }

    var oldFolder = tempDir.resolve("Catalogs").resolve("Orders").toFile();
    var newFolder = tempDir.resolve("Catalogs").resolve("Documents").toFile();
    FileUtils.moveDirectory(oldFolder, newFolder);

    var oldFolderUri = Absolute.uri(oldFolder.toURI());
    var newFolderUri = Absolute.uri(newFolder.toURI());

    var newObjectModuleUri = Absolute.uri(newFolder.toPath()
      .resolve("Ext").resolve("ObjectModule.bsl").toUri());
    var newFormModuleUri = Absolute.uri(newFolder.toPath()
      .resolve("Forms").resolve("ItemForm").resolve("Ext").resolve("Form").resolve("Module.bsl").toUri());

    var params = new RenameFilesParams(List.of(
      new FileRename(oldFolderUri.toString(), newFolderUri.toString())
    ));

    // when
    workspaceService.didRenameFiles(params);
    await().until(() -> ctx.getDocument(newObjectModuleUri) != null
      && ctx.getDocument(newFormModuleUri) != null
      && ctx.getDocument(oldObjectModuleUri) == null
      && ctx.getDocument(oldFormModuleUri) == null);

    // then
    assertThat(ctx.getDocument(oldObjectModuleUri)).isNull();
    assertThat(ctx.getDocument(oldFormModuleUri)).isNull();
    assertThat(ctx.getDocument(newObjectModuleUri)).isNotNull();
    assertThat(ctx.getDocument(newFormModuleUri)).isNotNull();
  }

  private ServerContext workspaceServerContext() {
    return serverContextProvider.getServerContext(Absolute.uri(tempDir.toUri())).orElseThrow();
  }

  /**
   * Создаёт временный BSL-файл из фикстуры {@code cli/test.bsl}.
   *
   * @param fileName имя файла во временном каталоге
   * @return созданный файл
   */
  private File createTestFile(String fileName) throws IOException {
    var file = tempDir.resolve(fileName).toFile();
    FileUtils.copyFile(FIXTURE_BSL, file);
    return file;
  }
}

