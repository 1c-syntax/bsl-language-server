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
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.utils.Absolute;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.FileChangeType;
import org.eclipse.lsp4j.FileEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Тесты для {@link BSLWorkspaceService}.
 */
@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class BSLWorkspaceServiceTest {

  @Autowired
  private BSLWorkspaceService workspaceService;

  @Autowired
  private ServerContext serverContext;

  @TempDir
  Path tempDir;

  @Test
  void testDidChangeWatchedFiles_Created_NotOpened() throws IOException {
    // given
    var testFile = createTestFile("test_created.bsl");
    var uri = Absolute.uri(testFile.toURI());

    var fileEvent = new FileEvent(uri.toString(), FileChangeType.Created);
    var params = new DidChangeWatchedFilesParams(List.of(fileEvent));

    // when
    workspaceService.didChangeWatchedFiles(params);
    await().until(() -> serverContext.getDocument(uri) != null);

    // then
    var documentContext = serverContext.getDocument(uri);
    assertThat(documentContext).isNotNull();
    assertThat(serverContext.isDocumentOpened(documentContext)).isFalse();
  }

  @Test
  void testDidChangeWatchedFiles_Created_AlreadyOpened() throws IOException {
    // given
    var testFile = createTestFile("test_created_opened.bsl");
    var uri = Absolute.uri(testFile.toURI());
    var content = FileUtils.readFileToString(testFile, StandardCharsets.UTF_8);

    var documentContext = serverContext.addDocument(uri);
    serverContext.openDocument(documentContext, content, 1);

    var fileEvent = new FileEvent(uri.toString(), FileChangeType.Created);
    var params = new DidChangeWatchedFilesParams(List.of(fileEvent));

    // when
    workspaceService.didChangeWatchedFiles(params);
    await().pollDelay(Duration.ofMillis(200)).until(() -> true);

    // then
    // Для открытого файла событие Created должно быть проигнорировано
    // Документ должен остаться в контексте
    assertThat(serverContext.getDocument(uri)).isNotNull();
  }

  @Test
  void testDidChangeWatchedFiles_Changed_NotOpened() throws IOException {
    // given
    var testFile = createTestFile("test_changed.bsl");
    var uri = Absolute.uri(testFile.toURI());

    var documentContext = serverContext.addDocument(uri);
    serverContext.rebuildDocument(documentContext);
    serverContext.tryClearDocument(documentContext);

    // Изменяем содержимое файла
    FileUtils.writeStringToFile(testFile, "// Новое содержимое\nПроцедура Тест()\nКонецПроцедуры\n", StandardCharsets.UTF_8);

    var fileEvent = new FileEvent(uri.toString(), FileChangeType.Changed);
    var params = new DidChangeWatchedFilesParams(List.of(fileEvent));

    // when
    workspaceService.didChangeWatchedFiles(params);
    await().pollDelay(Duration.ofMillis(100)).until(() -> true);

    // then
    assertThat(serverContext.getDocument(uri)).isNotNull();
    assertThat(serverContext.isDocumentOpened(documentContext)).isFalse();
  }

  @Test
  void testDidChangeWatchedFiles_Changed_Opened() throws IOException {
    // given
    var testFile = createTestFile("test_changed_opened.bsl");
    var uri = Absolute.uri(testFile.toURI());
    var content = FileUtils.readFileToString(testFile, StandardCharsets.UTF_8);

    var documentContext = serverContext.addDocument(uri);
    serverContext.openDocument(documentContext, content, 1);

    // Изменяем содержимое файла на диске
    var newContentOnDisk = "// Измененное содержимое\n";
    FileUtils.writeStringToFile(testFile, newContentOnDisk, StandardCharsets.UTF_8);

    var fileEvent = new FileEvent(uri.toString(), FileChangeType.Changed);
    var params = new DidChangeWatchedFilesParams(List.of(fileEvent));

    // when
    workspaceService.didChangeWatchedFiles(params);
    await().pollDelay(Duration.ofMillis(200)).until(() -> true);

    // then
    // Для открытого файла событие Changed должно быть проигнорировано
    // Документ должен остаться в контексте
    assertThat(serverContext.getDocument(uri)).isNotNull();
  }

  @Test
  void testDidChangeWatchedFiles_Changed_UnknownFile() throws IOException {
    // given
    var testFile = createTestFile("test_changed_unknown.bsl");
    var uri = Absolute.uri(testFile.toURI());

    assertThat(serverContext.getDocument(uri)).isNull();

    var fileEvent = new FileEvent(uri.toString(), FileChangeType.Changed);
    var params = new DidChangeWatchedFilesParams(List.of(fileEvent));

    // when
    workspaceService.didChangeWatchedFiles(params);
    await().until(() -> serverContext.getDocument(uri) != null);

    // then
    var documentContext = serverContext.getDocument(uri);
    assertThat(documentContext).isNotNull();
  }

  @Test
  void testDidChangeWatchedFiles_Deleted_NotOpened() throws IOException {
    // given
    var testFile = createTestFile("test_deleted.bsl");
    var uri = Absolute.uri(testFile.toURI());

    var documentContext = serverContext.addDocument(uri);
    serverContext.rebuildDocument(documentContext);
    serverContext.tryClearDocument(documentContext);

    assertThat(serverContext.getDocument(uri)).isNotNull();

    var fileEvent = new FileEvent(uri.toString(), FileChangeType.Deleted);
    var params = new DidChangeWatchedFilesParams(List.of(fileEvent));

    // when
    workspaceService.didChangeWatchedFiles(params);
    await().until(() -> serverContext.getDocument(uri) == null);

    // then
    assertThat(serverContext.getDocument(uri)).isNull();
  }

  @Test
  void testDidChangeWatchedFiles_Deleted_Opened() throws IOException {
    // given
    var testFile = createTestFile("test_deleted_opened.bsl");
    var uri = Absolute.uri(testFile.toURI());
    var content = FileUtils.readFileToString(testFile, StandardCharsets.UTF_8);

    var documentContext = serverContext.addDocument(uri);
    serverContext.openDocument(documentContext, content, 1);

    assertThat(serverContext.getDocument(uri)).isNotNull();

    var fileEvent = new FileEvent(uri.toString(), FileChangeType.Deleted);
    var params = new DidChangeWatchedFilesParams(List.of(fileEvent));

    // when
    workspaceService.didChangeWatchedFiles(params);
    await().until(() -> serverContext.getDocument(uri) == null);

    // then
    assertThat(serverContext.getDocument(uri)).isNull();
  }

  @Test
  void testDidChangeWatchedFiles_Deleted_UnknownFile() {
    // given
    var uri = URI.create("file:///nonexistent.bsl");

    var fileEvent = new FileEvent(uri.toString(), FileChangeType.Deleted);
    var params = new DidChangeWatchedFilesParams(List.of(fileEvent));

    // when
    workspaceService.didChangeWatchedFiles(params);
    await().pollDelay(Duration.ofMillis(100)).until(() -> true);

    // then
    // Не должно быть исключений
    assertThat(serverContext.getDocument(uri)).isNull();
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

    var documentContext2 = serverContext.addDocument(uri2);
    serverContext.rebuildDocument(documentContext2);

    // file3 добавляем в контекст, чтобы проверить его удаление
    var documentContext3 = serverContext.addDocument(uri3);
    serverContext.rebuildDocument(documentContext3);

    var events = List.of(
      new FileEvent(uri1.toString(), FileChangeType.Created),
      new FileEvent(uri2.toString(), FileChangeType.Changed),
      new FileEvent(uri3.toString(), FileChangeType.Deleted)
    );
    var params = new DidChangeWatchedFilesParams(events);

    // when
    workspaceService.didChangeWatchedFiles(params);
    await().until(() ->
      serverContext.getDocument(uri1) != null &&
      serverContext.getDocument(uri2) != null &&
      serverContext.getDocument(uri3) == null
    );

    // then
    assertThat(serverContext.getDocument(uri1)).isNotNull();
    assertThat(serverContext.getDocument(uri2)).isNotNull();
    assertThat(serverContext.getDocument(uri3)).isNull();
  }

  /**
   * Создает временный тестовый файл с базовым содержимым.
   */
  private File createTestFile(String fileName) throws IOException {
    var file = tempDir.resolve(fileName).toFile();
    var content = """
      // Тестовый файл
      Процедура ТестоваяПроцедура()
        Сообщить("Тест");
      КонецПроцедуры
      """;
    FileUtils.writeStringToFile(file, content, StandardCharsets.UTF_8);
    return file;
  }
}

