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
import com.github._1c_syntax.bsl.languageserver.mcp.McpWorkspaceBootstrap;
import com.github._1c_syntax.utils.Absolute;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.concurrent.Callable;

import static picocli.CommandLine.Command;
import static picocli.CommandLine.Option;

/**
 * Запускает приложение в режиме Websocket Language Server.
 * Ключ команды:
 *  -w, (--websocket)
 * Параметры:
 *  -c, (--configuration) &lt;arg&gt; - Путь к конфигурационному файлу BSL Language Server (.bsl-language-server.json).
 *                                      Возможно указывать как в абсолютном, так и относительном виде.
 *                                      Если параметр опущен, то будут использованы настройки по умолчанию.
 *  --server.port                     - Порт, на котором открывается соединение. Если параметр опущен,
 *                                      то будет использован порт по умолчанию, а именно 8025.
 *  --app.websocket.lsp-path          - Адрес, по которому открывается соединение. Если параметр опущен,
 *                                      то будет использован адрес по умолчанию, а именно /lsp.
 *  --mcp                             - Дополнительно поднять MCP-сервер по Streamable HTTP (эндпоинт /mcp)
 *                                      на том же servlet-контейнере, что и LSP-WebSocket.
 *  -s, (--srcDir) &lt;arg&gt;        - Каталог исходных файлов для индексации в общий контекст (нужен для --mcp).
 * Выводимая информация:
 *  Данный режим используется для взаимодействия с клиентом по протоколу LSP через websocket.
 *
 */
@Slf4j
@Command(
  name = "websocket",
  aliases = {"-w", "--websocket"},
  description = "Websocket server mode",
  usageHelpAutoWidth = true,
  footer = "@|green Copyright(c) 2018-2025|@")
@Component
@RequiredArgsConstructor
public class WebsocketCommand implements Callable<Integer> {
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
    names = {"--server.port"},
    description = "Port to listen. Default is 8025",
    paramLabel = "<port>",
    defaultValue = "8025")
  private int serverPort;

  @Option(
    names = {"--app.websocket.lsp-path"},
    description = "Path to LSP endpoint. Default is /lsp",
    paramLabel = "<path>",
    defaultValue = "/lsp")
  private String endpointPath;

  @Option(
    names = {"--mcp"},
    description = "Also expose an MCP server over Streamable HTTP (endpoint /mcp)")
  private boolean mcpEnabled;

  @Option(
    names = {"-s", "--srcDir"},
    description = "Source directory to index into the shared context (required with --mcp)",
    paramLabel = "<path>",
    defaultValue = "")
  private String srcDirOption;

  private final GlobalLanguageServerConfiguration globalConfiguration;
  private final McpWorkspaceBootstrap workspaceBootstrap;

  public Integer call() {
    var configurationFile = new File(configurationOption);

    if (mcpEnabled) {
      var srcDir = Absolute.path(srcDirOption);
      if (!srcDir.toFile().exists()) {
        LOGGER.error("Source dir `{}` is not exists. `--srcDir` is required with `--mcp`.", srcDir);
        return 1;
      }
      // Indexes into the shared ServerContextProvider; a connected LSP-WebSocket session
      // adds its own workspace folders to the same provider on top of this.
      workspaceBootstrap.index(srcDir, configurationFile);
    } else {
      globalConfiguration.update(configurationFile);
    }

    return -1;
  }

}
