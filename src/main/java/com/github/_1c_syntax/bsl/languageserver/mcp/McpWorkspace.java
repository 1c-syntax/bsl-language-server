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

import com.github._1c_syntax.bsl.languageserver.configuration.GlobalLanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.bsl.languageserver.utils.BSLFiles;
import com.github._1c_syntax.utils.Absolute;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;

/**
 * Активное рабочее пространство MCP-сервера: общий {@link ServerContext},
 * наполненный документами проекта.
 * <p>
 * Индексация выполняется командой запуска ({@code mcp}) на старте. Поскольку
 * MCP-сервер (его поднимает автоконфигурация Spring AI) может начать принимать
 * запросы раньше, чем завершится индексация, вызовы инструментов синхронизируются
 * через {@link #readyLatch}: ранний вызов дождётся готовности контекста.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpWorkspace {

  private final GlobalLanguageServerConfiguration globalConfiguration;
  private final LanguageServerConfiguration configuration;
  private final ServerContextProvider serverContextProvider;

  private final CountDownLatch readyLatch = new CountDownLatch(1);

  @Getter
  private ServerContext serverContext;
  @Getter
  private URI uri;

  /**
   * Проиндексировать исходники и привязать рабочее пространство к серверу.
   *
   * @param srcDir Каталог исходных файлов.
   * @param configurationFile Файл конфигурации BSL Language Server (может отсутствовать).
   */
  public void initialize(Path srcDir, File configurationFile) {
    globalConfiguration.update(configurationFile);

    uri = srcDir.toUri();
    serverContext = serverContextProvider.addWorkspace(uri);

    try (var ignored = WorkspaceContextHolder.forUri(uri)) {
      configuration.update(configurationFile);

      var configurationPath = LanguageServerConfiguration.getCustomConfigurationRoot(configuration, srcDir);
      serverContext.setConfigurationRoot(configurationPath);

      LOGGER.info("Indexing source directory `{}`...", srcDir);
      var files = new ArrayList<>(BSLFiles.listBslFiles(srcDir, configuration.getExcludePaths()));
      serverContext.populateContext(files);
      LOGGER.info("Indexed {} files.", files.size());
    } finally {
      readyLatch.countDown();
    }
  }

  /**
   * Выполнить действие в контексте рабочего пространства, дождавшись окончания индексации.
   *
   * @param action Действие над контекстом.
   * @param <T> Тип результата.
   * @return Результат действия.
   */
  public <T> T inWorkspace(Supplier<T> action) {
    awaitReady();
    try (var ignored = WorkspaceContextHolder.forUri(uri)) {
      return action.get();
    }
  }

  /**
   * Получить контекст документа по пути к файлу, гарантированно пересобрав AST.
   *
   * @param path Путь к файлу (абсолютный или относительный).
   * @return Готовый к запросам контекст документа.
   */
  public DocumentContext resolveDocument(String path) {
    var fileUri = Absolute.uri(new File(path));
    var documentContext = serverContext.addDocument(fileUri);
    serverContext.rebuildDocument(documentContext);
    return documentContext;
  }

  private void awaitReady() {
    try {
      readyLatch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for MCP workspace to be ready", e);
    }
  }
}
