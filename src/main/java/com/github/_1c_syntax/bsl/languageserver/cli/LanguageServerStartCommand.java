/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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

import com.github._1c_syntax.bsl.languageserver.BSLLanguageServer;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;

import static picocli.CommandLine.Command;
import static picocli.CommandLine.Option;

/**
 * Запускает приложение в режиме Language Server
 * Ключ команды:
 *  без ключа
 * Параметры:
 * -c, (--configuration) &lt;arg&gt; - Путь к конфигурационному файлу BSL Language Server (.bsl-language-server.json).
 *                                   Возможно указывать как в абсолютном, так и относительном виде. Если параметр опущен,
 *                                   то будут использованы настройки по умолчанию.
 * Выводимая информация:
 *  Данный режим используется для взаимодействия с клиентом по протоколу LSP.
 */
@Slf4j
@Command(
  name = "",
  description = "BSL language server on LSP server mode",
  subcommands = {
    AnalyzeCommand.class,
    FormatCommand.class,
    VersionCommand.class
  },
  usageHelpAutoWidth = true,
  synopsisSubcommandLabel = "[COMMAND [ARGS]]",
  footer = "@|green Copyright(c) 2018-2020|@",
  header = "@|green BSL language server|@")
public class LanguageServerStartCommand implements Callable<Integer> {
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

  public Integer call() {

    File configurationFile = new File(configurationOption);

    LanguageServerConfiguration configuration = LanguageServerConfiguration.create(configurationFile);
    LanguageServer server = new BSLLanguageServer(configuration);

    Launcher<LanguageClient> launcher = getLanguageClientLauncher(server, configuration);

    LanguageClient client = launcher.getRemoteProxy();
    ((LanguageClientAware) server).connect(client);

    launcher.startListening();
    return -1;
  }

  private static Launcher<LanguageClient> getLanguageClientLauncher(
    LanguageServer server,
    LanguageServerConfiguration configuration
  ) {
    InputStream in = System.in;
    OutputStream out = System.out;

    File logFile = configuration.getTraceLog();
    if (logFile == null) {
      return LSPLauncher.createServerLauncher(server, in, out);
    }

    Launcher<LanguageClient> launcher;

    try {
      PrintWriter printWriter = new PrintWriter(logFile, StandardCharsets.UTF_8.name());
      launcher = LSPLauncher.createServerLauncher(server, in, out, false, printWriter);
    } catch (FileNotFoundException | UnsupportedEncodingException e) {
      LOGGER.error("Can't create LSP trace file", e);
      if (logFile.isDirectory()) {
        LOGGER.error("Trace log setting must lead to file, not directory! {}", logFile.getAbsolutePath());
      }

      launcher = LSPLauncher.createServerLauncher(server, in, out);
    }

    return launcher;
  }
}
