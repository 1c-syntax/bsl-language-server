/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018
 * Alexey Sosnoviy <labotamy@yandex.ru>, Nikita Gryzlov <nixel2007@gmail.com>
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
package org.github._1c_syntax.intellij.bsl.lsp.server;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.github._1c_syntax.intellij.bsl.lsp.server.settings.LanguageServerSettings;

import java.io.InputStream;
import java.io.OutputStream;

public class BSLLSPLauncher {

  private static Options options = new Options();

  static {
    setOptions();
  }

  public static void main(String[] args) {

    CommandLine cmd = getCommandLine(args);

    String diagnosticLanguage = cmd.getOptionValue("diagnosticLanguage", "en");
    LanguageServerSettings settings = new LanguageServerSettings(diagnosticLanguage);

    LanguageServer server = new BSLLanguageServer(settings);
    InputStream in = System.in;
    OutputStream out = System.out;

    Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(server, in, out);

    LanguageClient client = launcher.getRemoteProxy();
    ((LanguageClientAware) server).connect(client);

    launcher.startListening();
  }

  private static void setOptions() {
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

    options.addOption(diagnosticLanguageOption);
    options.addOption(help);
  }

  private static CommandLine getCommandLine(String[] args) {
    CommandLineParser parser = new DefaultParser();
    HelpFormatter formatter = new HelpFormatter();
    CommandLine cmd = null;

    try {
      cmd = parser.parse(options, args);

      if (cmd.hasOption("help")) {
        formatter.printHelp("BSL language server", options);
        System.exit(0);
      }

    } catch (ParseException e) {
      System.out.println(e.getMessage());
      formatter.printHelp("BSL language server", options);

      System.exit(1);
    }
    return cmd;
  }
}
