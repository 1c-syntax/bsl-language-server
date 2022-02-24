/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2022
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

import com.github._1c_syntax.bsl.languageserver.BSLLSBinding;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.websocket.BSLLSWebSocketServerConfigProvider;
import com.github._1c_syntax.bsl.languageserver.websocket.WebSocketRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.concurrent.Callable;

import static picocli.CommandLine.Command;
import static picocli.CommandLine.Option;

/**
 * Запускает приложение в режиме Websocket Language Server
 * Ключ команды:
 * -w, (--websocket)
 * Параметры:
 * -c, (--configuration)      -  Путь к конфигурационному файлу BSL Language Server (.bsl-language-server.json).
 * Возможно указывать как в абсолютном, так и относительном виде. Если параметр опущен,
 * то будут использованы настройки по умолчанию.
 * Можно указать каталог, в котором будут найдены файлы для форматирования, либо один
 * файл для форматирования
 * -p, (--port)               -  Порт, на котором открывается соединение. Если параметр опущен, то будет использован
 * порт по умолчанию, а именно 8025
 * Выводимая информация:
 * Данный режим используется для взаимодействия с клиентом по протоколу LSP через websocket
 */
@Slf4j
@Command(
  name = "websocket",
  aliases = {"-w", "--websocket"},
  description = "Websocket server mode",
  usageHelpAutoWidth = true,
  footer = "@|green Copyright(c) 2018-2020|@")
@Component
@RequiredArgsConstructor
public class WebsocketStartCommand implements Callable<Integer> {
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
    names = {"-p", "--port"},
    description = "Listening port",
    paramLabel = "<port>",
    defaultValue = "8025")
  private int websocketPort;

  private final LanguageServerConfiguration configuration;
  private final WebSocketRunner launcher;

  public Integer call() {

    var configurationFile = new File(configurationOption);
    if (configurationFile.exists()) {
      configuration.update(configurationFile);
    }

    launcher.runWebSocketServer(websocketPort);
    return -1;

  }

}
