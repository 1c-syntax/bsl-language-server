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
package com.github._1c_syntax.bsl.languageserver.util;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.utils.Absolute;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class TestUtils {

  public static final URI FAKE_DOCUMENT_URI = Absolute.uri("file:///fake-uri.bsl");
  public static final String PATH_TO_METADATA = "src/test/resources/metadata/designer";
  private static final String TEST_WORKSPACE_NAME = "test-metadata-workspace";

  /**
   * Получить ServerContext, зарегистрированный в ServerContextProvider.
   * Регистрирует workspace для PATH_TO_METADATA если ещё не зарегистрирован.
   *
   * @return ServerContext, связанный с тестовым workspace
   */
  private static ServerContext getServerContext() {
    var provider = TestApplicationContext.getBean(ServerContextProvider.class);
    var metadataPath = Absolute.path(PATH_TO_METADATA);

    // Check if workspace already registered
    var existingContext = provider.getServerContext(metadataPath.toUri());
    if (existingContext.isPresent()) {
      return existingContext.get();
    }

    // Register new workspace
    var workspaceFolder = new WorkspaceFolder(metadataPath.toUri().toString(), TEST_WORKSPACE_NAME);
    var context = provider.addWorkspace(workspaceFolder);
    context.setConfigurationRoot(metadataPath);
    return context;
  }

  /**
   * Получить ServerContext, зарегистрированный в ServerContextProvider.
   * Регистрирует workspace для PATH_TO_METADATA если ещё не зарегистрирован.
   *
   * @return ServerContext, связанный с тестовым workspace
   */
  public static ServerContext getRegisteredServerContext() {
    return getServerContext();
  }

  /**
   * Получить ServerContext для файла.
   * <p>
   * Логика:
   * - 0 контекстов в провайдере → создаёт новый для родительской директории файла
   * - 1 контекст → возвращает его (файл будет добавлен туда)
   * - >1 контекстов → выбрасывает исключение (тест должен явно указать контекст)
   */
  private static ServerContext getServerContextForFile(Path filePath) {
    var provider = TestApplicationContext.getBean(ServerContextProvider.class);
    var allContexts = provider.getAllContexts();

    if (allContexts.isEmpty()) {
      // No contexts registered - create new one for parent directory
      var absolutePath = Absolute.path(filePath);
      var parentDir = absolutePath.getParent();
      if (parentDir == null) {
        parentDir = absolutePath;
      }
      var workspaceFolder = new WorkspaceFolder(parentDir.toUri().toString(), "test-" + parentDir.getFileName());
      return provider.addWorkspace(workspaceFolder);
    } else if (allContexts.size() == 1) {
      // Single context - use it
      return allContexts.iterator().next();
    } else {
      // Multiple contexts - test must explicitly specify which one to use
      throw new IllegalStateException(
        "Multiple ServerContexts registered. Use getDocumentContextFromFile(path, serverContext) to specify target context."
      );
    }
  }

  /**
   * Загрузить файл и добавить его в ServerContext.
   * <p>
   * Если контекстов нет — создаёт новый.
   * Если один контекст — добавляет туда.
   * Если больше одного — выбрасывает исключение (используйте перегрузку с явным контекстом).
   */
  @SneakyThrows
  public static DocumentContext getDocumentContextFromFile(String filePath) {
    String fileContent = FileUtils.readFileToString(
      new File(filePath),
      StandardCharsets.UTF_8
    );

    var path = Path.of(filePath);
    var context = getServerContextForFile(path);
    return getDocumentContext(path.toUri(), fileContent, context);
  }

  /**
   * Загрузить файл и добавить его в указанный ServerContext.
   */
  @SneakyThrows
  public static DocumentContext getDocumentContextFromFile(String filePath, ServerContext serverContext) {
    String fileContent = FileUtils.readFileToString(
      new File(filePath),
      StandardCharsets.UTF_8
    );

    var path = Path.of(filePath);
    return getDocumentContext(path.toUri(), fileContent, serverContext);
  }

  public static DocumentContext getDocumentContext(URI uri, String fileContent) {
    return getDocumentContext(uri, fileContent, getServerContextForUri(uri));
  }

  /**
   * Получить или создать ServerContext для URI.
   */
  private static ServerContext getServerContextForUri(URI uri) {
    var provider = TestApplicationContext.getBean(ServerContextProvider.class);

    // Check if workspace already registered for this URI
    var existingContext = provider.getServerContext(uri);
    if (existingContext.isPresent()) {
      return existingContext.get();
    }

    // For file URIs, register workspace for parent directory
    if ("file".equalsIgnoreCase(uri.getScheme())) {
      var path = Path.of(uri);
      return getServerContextForFile(path);
    }

    // For non-file URIs (like fake URIs), use default metadata workspace
    return getServerContext();
  }

  public static DocumentContext getDocumentContext(String fileContent) {
    return getDocumentContext(FAKE_DOCUMENT_URI, fileContent);
  }

  public static DocumentContext getDocumentContext(String fileContent, @Nullable ServerContext context) {
    ServerContext passedContext = context;
    if (passedContext == null) {
      passedContext = getServerContext();
    }

    return getDocumentContext(FAKE_DOCUMENT_URI, fileContent, passedContext);
  }

  public static DocumentContext getDocumentContext(URI uri, String fileContent, ServerContext context) {
    var normalizedUri = Absolute.uri(uri);
    var documentContext = context.addDocument(normalizedUri);
    context.rebuildDocument(documentContext, fileContent, 0);
    return documentContext;
  }

  public static TextDocumentIdentifier getTextDocumentIdentifier(URI uri) {
    return new TextDocumentIdentifier(uri.toString());
  }

}
