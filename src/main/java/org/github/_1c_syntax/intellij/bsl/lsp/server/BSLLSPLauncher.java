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

import javafx.util.Pair;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.github._1c_syntax.intellij.bsl.lsp.server.diagnostics.DiagnosticProvider;
import org.github._1c_syntax.intellij.bsl.lsp.server.settings.LanguageServerSettings;
import org.github._1c_syntax.parser.BSLLexer;
import org.github._1c_syntax.parser.BSLParser;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class BSLLSPLauncher {

  private static Options options = new Options();

  static {
    setOptions();
  }

  public static void main(String[] args) {
    CommandLineParser parser = new DefaultParser();

    try {
      CommandLine cmd = parser.parse(options, args);

      if (cmd.hasOption("help")) {
        processHelp();
      } else if (cmd.hasOption("analyze")) {
        processAnalyze(cmd);
      } else {
        processLanguageServerStart(cmd);
      }

    } catch (ParseException e) {
      processParseException(e);
    }
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

    options.addOption(analyze);
    options.addOption(srcDir);

    options.addOption(diagnosticLanguageOption);
    options.addOption(help);
  }

  private static void processAnalyze(CommandLine cmd) {
    String srcDirOption = cmd.getOptionValue("srcDir", "");
    Path srcDir = Paths.get(srcDirOption).toAbsolutePath();

    Collection<File> files = FileUtils.listFiles(srcDir.toFile(), new String[]{"bsl", "os"}, true);

    List<Pair<Path, List<Diagnostic>>> diagnostics = files.parallelStream()
      .map(File::toPath)
      .map(path -> new Pair<>(path, prepareParser(path)))
      .map(pair -> new Pair<>(pair.getKey(), pair.getValue().file()))
      .map(pair -> new Pair<>(pair.getKey(), DiagnosticProvider.computeDiagnostics(pair.getValue())))
      .collect(Collectors.toList());

    System.out.println(srcDir);
    diagnostics.forEach(diagnostic -> System.out.println(diagnostic.toString()));

  }

  private static void processLanguageServerStart(CommandLine cmd) {
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

  private static void processParseException(ParseException e) {
    HelpFormatter formatter = new HelpFormatter();

    System.out.println(e.getMessage());
    formatter.printHelp("BSL language server", options, true);

    System.exit(1);
  }

  private static void processHelp() {
    HelpFormatter formatter = new HelpFormatter();

    formatter.printHelp("BSL language server", options, true);
    System.exit(0);
  }

  private static BSLParser prepareParser(Path path) {
    CharStream input;
    try {
      input = CharStreams.fromPath(path, Charset.forName("UTF-8"));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    BSLLexer lexer = new BSLLexer(input);
    CommonTokenStream tokenStream = new CommonTokenStream(lexer);
    return new BSLParser(tokenStream);
  }
}
