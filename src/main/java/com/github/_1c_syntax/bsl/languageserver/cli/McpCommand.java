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
import java.util.Locale;
import java.util.concurrent.Callable;

import static picocli.CommandLine.Option;

/**
 * Запуск сервера в режиме Model Context Protocol (MCP).
 * <p>
 * Транспорт выбирается параметром {@code --protocol}: {@code stdio} (по умолчанию) или {@code sse}
 * (Server-Sent Events по HTTP). Сервер поднимает автоконфигурация Spring AI (профили
 * {@code mcp,mcp-stdio} или {@code mcp,mcp-sse}); инструменты ({@code @McpTool}) работают через
 * общий {@code ServerContextProvider}. Рабочие пространства приходят от клиента через MCP roots
 * (см. {@code McpRootsChangeConsumer}) — аналог workspace folders в LSP.
 * <p>
 * Для {@code stdio} команда применяет глобальную конфигурацию и блокируется до отключения клиента
 * (EOF stdin). Для {@code sse} процесс жив за счёт встроенного веб-сервера.
 * <p>
 * Ключ команды:
 *  mcp
 * Параметры:
 *  -c, (--configuration) &lt;arg&gt; - Путь к конфигурационному файлу BSL Language Server.
 *  (--protocol) &lt;arg&gt;          - Транспорт MCP: stdio (по умолчанию) или sse.
 */
@Slf4j
@Command(
  name = "mcp",
  description = "MCP (Model Context Protocol) server mode (stdio or sse)",
  usageHelpAutoWidth = true,
  footer = "@|green Copyright(c) 2018-2026|@")
@Component
@RequiredArgsConstructor
public class McpCommand implements Callable<Integer> {

  static final String PROTOCOL_STDIO = "stdio";
  static final String PROTOCOL_SSE = "sse";

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

  @Option(
    names = {"--protocol"},
    description = "MCP transport protocol: stdio (default) or sse",
    paramLabel = "<protocol>",
    defaultValue = PROTOCOL_STDIO)
  private String protocol;

  private final GlobalLanguageServerConfiguration globalConfiguration;
  private final ObjectProvider<McpShutdownSignal> shutdownSignalProvider;

  @Override
  public Integer call() {
    globalConfiguration.update(new File(configurationOption));

    var transport = protocol.toLowerCase(Locale.ROOT);
    if (PROTOCOL_SSE.equals(transport)) {
      // HTTP-транспорт: процесс жив за счёт встроенного веб-сервера, блокироваться не нужно.
      LOGGER.info("MCP server enabled over SSE");
      return -1;
    }
    if (!PROTOCOL_STDIO.equals(transport)) {
      LOGGER.error("Unknown MCP protocol `{}`. Use `stdio` or `sse`.", protocol);
      return 1;
    }

    // stdio: сервер уже поднят автоконфигурацией; блокируемся до отключения клиента.
    var shutdownSignal = shutdownSignalProvider.getIfAvailable();
    if (shutdownSignal == null) {
      LOGGER.error("MCP profile is not active: server is not running.");
      return 1;
    }

    shutdownSignal.await();
    return 0;
  }
}
