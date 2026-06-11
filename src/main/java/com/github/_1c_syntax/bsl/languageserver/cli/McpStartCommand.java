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
package com.github._1c_syntax.bsl.languageserver.cli;

import com.github._1c_syntax.bsl.languageserver.configuration.GlobalLanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.bsl.languageserver.mcp.McpServerRunner;
import com.github._1c_syntax.bsl.languageserver.mcp.McpWorkspace;
import com.github._1c_syntax.bsl.languageserver.utils.BSLFiles;
import com.github._1c_syntax.utils.Absolute;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.Callable;

import static picocli.CommandLine.Option;

/**
 * Запуск сервера в режиме Model Context Protocol (MCP).
 * <p>
 * Прототип. Поднимает MCP-сервер поверх stdio, переиспользуя движок анализа
 * (тот же {@link ServerContextProvider} и провайдеры, что и LSP-режим).
 * Перед стартом индексирует исходники, чтобы инструменты (диагностики, символы,
 * поиск ссылок) работали корректно, в том числе кросс-файлово.
 * <p>
 * Ключ команды:
 *  mcp
 * Параметры:
 *  -s, (--srcDir) &lt;arg&gt; - Путь к каталогу исходных файлов для индексации.
 *  -c, (--configuration) &lt;arg&gt; - Путь к конфигурационному файлу BSL Language Server.
 */
@Slf4j
@Command(
  name = "mcp",
  aliases = {"--mcp"},
  description = "MCP (Model Context Protocol) server mode over stdio",
  usageHelpAutoWidth = true,
  footer = "@|green Copyright(c) 2018-2026|@")
@Component
@RequiredArgsConstructor
public class McpStartCommand implements Callable<Integer> {

  @Option(
    names = {"-h", "--help"},
    usageHelp = true,
    description = "Show this help message and exit")
  private boolean usageHelpRequested;

  @Option(
    names = {"-s", "--srcDir"},
    description = "Source directory to index",
    paramLabel = "<path>",
    defaultValue = "")
  private String srcDirOption;

  @Option(
    names = {"-c", "--configuration"},
    description = "Path to language server configuration file",
    paramLabel = "<path>",
    defaultValue = "")
  private String configurationOption;

  private final GlobalLanguageServerConfiguration globalConfiguration;
  private final LanguageServerConfiguration configuration;
  private final ServerContextProvider serverContextProvider;
  private final McpWorkspace workspace;
  private final McpServerRunner mcpServerRunner;

  @Override
  public Integer call() {
    var srcDir = Absolute.path(srcDirOption);
    if (!srcDir.toFile().exists()) {
      LOGGER.error("Source dir `{}` is not exists", srcDir);
      return 1;
    }

    var configurationFile = new File(configurationOption);
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
    }

    workspace.bind(serverContext, workspaceUri);

    // stdout — канал протокола MCP; перенаправляем посторонний вывод в stderr.
    var protocolOut = System.out;
    System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));

    mcpServerRunner.run(System.in, protocolOut);

    return 0;
  }
}
