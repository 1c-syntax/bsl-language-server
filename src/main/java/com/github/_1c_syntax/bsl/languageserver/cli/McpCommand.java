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
import com.github._1c_syntax.bsl.languageserver.mcp.McpShutdownSignal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

import java.io.File;
import java.util.concurrent.Callable;

import static picocli.CommandLine.Option;

/**
 * Запуск сервера в режиме Model Context Protocol (MCP) поверх stdio.
 * <p>
 * Сервер поднимает автоконфигурация Spring AI (профили {@code mcp,mcp-stdio}); инструменты
 * ({@code @McpTool}) работают через общий {@code ServerContextProvider}. Рабочие пространства
 * приходят от клиента через MCP roots (см. {@code McpRootsChangeConsumer}) — аналог workspace
 * folders в LSP. Команда применяет глобальную конфигурацию и блокируется до отключения клиента
 * (EOF stdin).
 * <p>
 * Ключ команды:
 *  mcp
 * Параметры:
 *  -c, (--configuration) &lt;arg&gt; - Путь к конфигурационному файлу BSL Language Server.
 */
@Slf4j
@Command(
  name = "mcp",
  description = "MCP (Model Context Protocol) server mode over stdio",
  usageHelpAutoWidth = true,
  footer = "@|green Copyright(c) 2018-2026|@")
@Component
@RequiredArgsConstructor
public class McpCommand implements Callable<Integer> {

  @Option(
    names = {"-h", "--help"},
    usageHelp = true,
    description = "Show this help message and exit")
  private boolean usageHelpRequested;

  @Option(
    names = {"-c", "--configuration"},
    description = "Path to language server configuration file",
    paramLabel = "<path>",
    defaultValue = "")
  private String configurationOption;

  private final GlobalLanguageServerConfiguration globalConfiguration;
  private final ObjectProvider<McpShutdownSignal> shutdownSignalProvider;

  @Override
  public Integer call() {
    globalConfiguration.update(new File(configurationOption));

    // Сервер уже поднят автоконфигурацией; блокируемся до отключения клиента.
    var shutdownSignal = shutdownSignalProvider.getIfAvailable();
    if (shutdownSignal == null) {
      LOGGER.error("MCP profile is not active: server is not running.");
      return 1;
    }

    shutdownSignal.await();
    return 0;
  }
}
