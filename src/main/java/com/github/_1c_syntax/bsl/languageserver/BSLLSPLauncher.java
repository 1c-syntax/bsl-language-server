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
import com.github._1c_syntax.bsl.languageserver.cli.Command;
import com.github._1c_syntax.bsl.languageserver.cli.FormatCommand;
import com.github._1c_syntax.bsl.languageserver.cli.HelpCommand;
import com.github._1c_syntax.bsl.languageserver.cli.LanguageServerStartCommand;
import com.github._1c_syntax.bsl.languageserver.cli.ParseExceptionCommand;
import com.github._1c_syntax.bsl.languageserver.cli.VersionCommand;
import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class BSLLSPLauncher {

  public static final String APP_NAME = "BSL language server";

  private static final Options options = createOptions();

  public static void main(String[] args) {
    CommandLineParser parser = new DefaultParser();

    Command command;
    try {
      CommandLine cmd = parser.parse(options, args);

      if (cmd.hasOption("help")) {
        command = new HelpCommand(options);
      } else if (cmd.hasOption("version")) {
        command = new VersionCommand();
      } else if (cmd.hasOption("analyze")) {
        command = new AnalyzeCommand(cmd);
      } else if (cmd.hasOption("format")) {
        command = new FormatCommand(cmd);
      } else {
        command = new LanguageServerStartCommand(cmd);
      }

    } catch (ParseException e) {
      command = new ParseExceptionCommand(options, e);
    }

    int result = command.execute();
    if (result >= 0) {
      System.exit(result);
    }
  }

  @VisibleForTesting
  public static Options createOptions() {
    Options createdOptions = new Options();

    Option configurationOption = new Option(
      "c",
      "configuration",
      true,
      "Path to language server configuration file"
    );
    configurationOption.setRequired(false);

    Option help = new Option(
      "h",
      "help",
      false,
      "Show help."
    );

    Option analyze = new Option(
      "a",
      "analyze",
      false,
      "Run analysis and get diagnostic info"
    );

    Option format = new Option(
      "f",
      "format",
      false,
      "Format files in source directory"
    );

    Option srcDir = new Option(
      "s",
      "srcDir",
      true,
      "Source directory"
    );

    Option reporter = new Option(
      "r",
      "reporter",
      true,
      "Reporter key"
    );

    Option outputDir = new Option(
      "o",
      "outputDir",
      true,
      "Output report directory"
    );

    Option version = new Option(
      "v",
      "version",
      false,
      "Show version."
    );

    Option silentMode = new Option(
      "q",
      "silent",
      false,
      "Silent mode"
    );

    createdOptions.addOption(analyze);
    createdOptions.addOption(format);
    createdOptions.addOption(srcDir);
    createdOptions.addOption(outputDir);
    createdOptions.addOption(reporter);
    createdOptions.addOption(silentMode);

    createdOptions.addOption(configurationOption);
    createdOptions.addOption(help);
    createdOptions.addOption(version);

    return createdOptions;
  }

}
