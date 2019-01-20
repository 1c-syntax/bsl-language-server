/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
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
package org.github._1c_syntax.bsl.languageserver.cli;

import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarStyle;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.io.FileUtils;
import org.github._1c_syntax.bsl.languageserver.diagnostics.FileInfo;
import org.github._1c_syntax.bsl.languageserver.diagnostics.reporter.AnalysisInfo;
import org.github._1c_syntax.bsl.languageserver.diagnostics.reporter.ReportersAggregator;
import org.github._1c_syntax.bsl.languageserver.providers.DiagnosticProvider;
import org.github._1c_syntax.parser.BSLLexer;
import org.github._1c_syntax.parser.BSLParser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class AnalyzeCommand implements Command {
  private CommandLine cmd;

  public AnalyzeCommand(CommandLine cmd) {
    this.cmd = cmd;
  }

  @Override
  public int execute() {
    String srcDirOption = cmd.getOptionValue("srcDir", "");
    String[] reporters = Optional.ofNullable(cmd.getOptionValues("reporter")).orElse(new String[0]);

    Path srcDir = Paths.get(srcDirOption).toAbsolutePath();

    Collection<File> files = FileUtils.listFiles(srcDir.toFile(), new String[]{"bsl", "os"}, true);

    List<FileInfo> diagnostics;
    try (ProgressBar pb = new ProgressBar("Analyzing files...", files.size(), ProgressBarStyle.ASCII)) {
      diagnostics = files.parallelStream()
        .peek(file -> pb.step())
        .map(File::toPath)
        .map(AnalyzeCommand::getFileContextFromPath)
        .collect(Collectors.toList());
    }

    AnalysisInfo analysisInfo = new AnalysisInfo(LocalDateTime.now(), diagnostics);
    ReportersAggregator aggregator = new ReportersAggregator(reporters);
    aggregator.report(analysisInfo);
    return 0;
  }

  private static FileInfo getFileContextFromPath(Path path) {
    BSLParser parser = prepareParser(path);
    BSLParser.FileContext file = parser.file();
    return new FileInfo(path, DiagnosticProvider.computeDiagnostics(file));
  }


  private static BSLParser prepareParser(Path path) {
    CharStream input;
    try {
      input = CharStreams.fromPath(path, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    BSLLexer lexer = new BSLLexer(input);
    CommonTokenStream tokenStream = new CommonTokenStream(lexer);
    return new BSLParser(tokenStream);
  }
}
