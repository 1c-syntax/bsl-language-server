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
package com.github._1c_syntax.bsl.languageserver.cli;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.diagnostics.DiagnosticSupplier;
import com.github._1c_syntax.bsl.languageserver.diagnostics.FileInfo;
import com.github._1c_syntax.bsl.languageserver.diagnostics.reporter.AnalysisInfo;
import com.github._1c_syntax.bsl.languageserver.diagnostics.reporter.ReportersAggregator;
import com.github._1c_syntax.bsl.languageserver.providers.DiagnosticProvider;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarStyle;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.io.FileUtils;

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
  private DiagnosticProvider diagnosticProvider;
  private ServerContext context;

  public AnalyzeCommand(CommandLine cmd) {
    this.cmd = cmd;
  }

  @Override
  public int execute() {
    String srcDirOption = cmd.getOptionValue("srcDir", "");
    String outputDirOption = cmd.getOptionValue("outputDir", "");
    String[] reporters = Optional.ofNullable(cmd.getOptionValues("reporter")).orElse(new String[0]);
    String configurationOption = cmd.getOptionValue("configuration", "");

    Path srcDir = Paths.get(srcDirOption).toAbsolutePath();
    Path outputDir = Paths.get(outputDirOption).toAbsolutePath();
    File configurationFile = new File(configurationOption);

    LanguageServerConfiguration configuration = LanguageServerConfiguration.create(configurationFile);

    Path configurationPath = LanguageServerConfiguration.getCustomConfigurationRoot(configuration, srcDir);
    context = new ServerContext(configurationPath);
    DiagnosticSupplier diagnosticSupplier = new DiagnosticSupplier(configuration);
    diagnosticProvider = new DiagnosticProvider(diagnosticSupplier);

    Collection<File> files = FileUtils.listFiles(srcDir.toFile(), new String[]{"bsl", "os"}, true);

    List<FileInfo> diagnostics;
    try (ProgressBar pb = new ProgressBar("Analyzing files...", files.size(), ProgressBarStyle.ASCII)) {
      diagnostics = files.parallelStream()
        .peek(file -> pb.step())
        .map(this::getFileContextFromFile)
        .collect(Collectors.toList());
    }

    AnalysisInfo analysisInfo = new AnalysisInfo(LocalDateTime.now(), diagnostics, srcDirOption);
    ReportersAggregator aggregator = new ReportersAggregator(outputDir, reporters);
    aggregator.report(analysisInfo);
    return 0;
  }

  private FileInfo getFileContextFromFile(File file) {
    String textDocumentContent;
    try {
      textDocumentContent = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    DocumentContext documentContext = context.addDocument(file.toURI().toString(), textDocumentContent);
    FileInfo fileInfo = new FileInfo(documentContext, diagnosticProvider.computeDiagnostics(documentContext));

    // clean up AST after diagnostic computing to free up RAM.
    documentContext.clearASTData();

    return fileInfo;
  }

}
