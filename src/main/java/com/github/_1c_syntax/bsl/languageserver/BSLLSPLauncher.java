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
package com.github._1c_syntax.bsl.languageserver;

import com.github._1c_syntax.bsl.languageserver.cli.AnalyzeCommand;
import com.github._1c_syntax.bsl.languageserver.cli.FormatCommand;
import com.github._1c_syntax.bsl.languageserver.cli.LanguageServerStartCommand;
import com.github._1c_syntax.bsl.languageserver.cli.VersionCommand;
import org.jetbrains.annotations.NotNull;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

import static picocli.CommandLine.Command;

@Command(
  name = "bsl-language-server",
  subcommands = {
    AnalyzeCommand.class,
    FormatCommand.class,
    VersionCommand.class,
    LanguageServerStartCommand.class
  },
  usageHelpAutoWidth = true,
  synopsisSubcommandLabel = "[COMMAND [ARGS]]",
  footer = "@|green Copyright(c) 2018-2020|@",
  header = "@|green BSL language server|@")
public class BSLLSPLauncher implements Callable<Integer> {

  private static final String DEFAULT_COMMAND = "lsp";

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

  public static void main(String[] args) {
    var app = new BSLLSPLauncher();
    var cmd = new CommandLine(app);

    // проверка использования дефолтной команды
    // если строка параметров пуста, то это точно вызов команды по умолчанию
    if (args.length == 0) {
      args = addDefaultCommand(args);
    } else {
      // выполнение проверки строки запуска в попытке, т.к. парсер при нахождении
      // неизвестных параметров выдает ошибку
      try {
        var parseResult = cmd.parseArgs(args);
        // если переданы параметры без команды и это не справка
        // то считаем, что параметры для команды по умолчанию
        if(!parseResult.hasSubcommand() && !parseResult.isUsageHelpRequested()) {
          args = addDefaultCommand(args);
        }
      } catch (ParameterException ex) {
        // если поймали ошибку, а имя команды не передано, подставим команду и посмотрим,
        // вдруг заработает
        if (!ex.getCommandLine().getParseResult().hasSubcommand()) {
          args = addDefaultCommand(args);
        }
      }
    }

    int result = cmd.execute(args);
    if (result >= 0) {
      System.exit(result);
    }
  }

  @NotNull
  private static String[] addDefaultCommand(String[] args) {
    List<String> tmpList = new ArrayList<>(Arrays.asList(args));
    tmpList.add(0, DEFAULT_COMMAND);
    args = tmpList.toArray(new String[0]);
    return args;
  }

  public Integer call() {
    // заглушка, командой как таковой не пользуемся
    return 0;
  }
}
