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
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.bsl.languageserver.utils.BSLFiles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;

/**
 * Регистрация и удаление рабочих пространств MCP в общем {@link ServerContextProvider}.
 * <p>
 * Рабочие пространства приходят от клиента через MCP roots (см. {@link McpRootsChangeConsumer}) —
 * аналог workspace folders в LSP. Индексация выполняется так же, как в {@code analyze}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpWorkspaceBootstrap {

  private final LanguageServerConfiguration configuration;
  private final ServerContextProvider serverContextProvider;

  /**
   * Зарегистрировать каталог исходников как рабочее пространство и проиндексировать его.
   *
   * @param srcDir Каталог исходных файлов.
   * @return Количество проиндексированных файлов.
   */
  public int index(Path srcDir) {
    var workspaceUri = srcDir.toUri();
    var serverContext = serverContextProvider.addWorkspace(workspaceUri);

    try (var ignored = WorkspaceContextHolder.forUri(workspaceUri)) {
      configuration.update(new File(""));

      var configurationPath = LanguageServerConfiguration.getCustomConfigurationRoot(configuration, srcDir);
      serverContext.setConfigurationRoot(configurationPath);

      var files = new ArrayList<>(BSLFiles.listBslFiles(srcDir, configuration.getExcludePaths()));
      serverContext.populateContext(files);
      LOGGER.info("Indexed {} files in workspace `{}`", files.size(), srcDir);
      return files.size();
    }
  }

  /**
   * Удалить рабочее пространство из общего контекста сервера.
   *
   * @param srcDir Каталог исходных файлов ранее добавленного рабочего пространства.
   */
  public void remove(Path srcDir) {
    var uri = srcDir.toUri().toString();
    serverContextProvider.removeWorkspace(new WorkspaceFolder(uri, uri));
  }
}
