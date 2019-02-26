/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
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
package org.github._1c_syntax.bsl.languageserver;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.github._1c_syntax.bsl.languageserver.cli.AnalyzeCommand;
import org.github._1c_syntax.bsl.languageserver.cli.Command;
import org.github._1c_syntax.bsl.languageserver.cli.HelpCommand;
import org.github._1c_syntax.bsl.languageserver.cli.LanguageServerStartCommand;
import org.github._1c_syntax.bsl.languageserver.cli.ParseExceptionCommand;

public class BSLLSPLauncher {

  public static final String APP_NAME = "BSL language server";

  private static Options options = createOptions();

  public static void main(String[] args) {
    CommandLineParser parser = new DefaultParser();

    Command command;
    try {
      CommandLine cmd = parser.parse(options, args);

      if (cmd.hasOption("help")) {
        command = new HelpCommand(options);
      } else if (cmd.hasOption("analyze")) {
        command = new AnalyzeCommand(cmd);
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

    Option diagnosticLanguageOption = new Option(
      "d",
      "diagnosticLanguage",
      true,
      "Language of diagnostic messages. Possible values: en, ru. Default is en."
    );
    diagnosticLanguageOption.setRequired(false);

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

    createdOptions.addOption(analyze);
    createdOptions.addOption(srcDir);
    createdOptions.addOption(outputDir);
    createdOptions.addOption(reporter);

    createdOptions.addOption(diagnosticLanguageOption);
    createdOptions.addOption(help);

    return createdOptions;
  }

}
