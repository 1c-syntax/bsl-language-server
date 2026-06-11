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
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.bsl.languageserver.utils.BSLFiles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;

/**
 * Headless-инициализация рабочего пространства для MCP: регистрирует каталог исходников
 * в общем {@link ServerContextProvider} и индексирует его — так же, как {@code analyze}.
 * <p>
 * Используется обоими headless-входами ({@code mcp} по stdio и {@code websocket --mcp} по
 * Streamable HTTP). В режиме «поверх живой LSP-сессии» рабочие пространства наполняет сама
 * сессия (через workspace folders), и этот бутстрап не вызывается.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpWorkspaceBootstrap {

  private final GlobalLanguageServerConfiguration globalConfiguration;
  private final LanguageServerConfiguration configuration;
  private final ServerContextProvider serverContextProvider;
  private final McpReadiness readiness;

  /**
   * Зарегистрировать и проиндексировать каталог исходников в общий контекст сервера.
   *
   * @param srcDir Каталог исходных файлов.
   * @param configurationFile Файл конфигурации BSL Language Server (может отсутствовать).
   */
  public void index(Path srcDir, File configurationFile) {
    globalConfiguration.update(configurationFile);

    var workspaceUri = srcDir.toUri();
    var serverContext = serverContextProvider.addWorkspace(workspaceUri);

    try (var ignored = WorkspaceContextHolder.forUri(workspaceUri)) {
      configuration.update(configurationFile);

      var configurationPath = LanguageServerConfiguration.getCustomConfigurationRoot(configuration, srcDir);
      serverContext.setConfigurationRoot(configurationPath);

      LOGGER.info("Indexing source directory `{}`...", srcDir);
      var files = new ArrayList<>(BSLFiles.listBslFiles(srcDir, configuration.getExcludePaths()));
      serverContext.populateContext(files);
      LOGGER.info("Indexed {} files.", files.size());
    } finally {
      readiness.markReady();
    }
  }
}
