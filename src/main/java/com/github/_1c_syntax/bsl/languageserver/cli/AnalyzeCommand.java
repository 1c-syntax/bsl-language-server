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

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.MetricStorage;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.diagnostics.DiagnosticSupplier;
import com.github._1c_syntax.bsl.languageserver.diagnostics.FileInfo;
import com.github._1c_syntax.bsl.languageserver.diagnostics.reporter.AnalysisInfo;
import com.github._1c_syntax.bsl.languageserver.diagnostics.reporter.ReportersAggregator;
import com.github._1c_syntax.bsl.languageserver.providers.DiagnosticProvider;
import com.github._1c_syntax.utils.Absolute;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarStyle;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.Diagnostic;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Выполнение анализа
 * Ключ команды:
 *  -a, (--analyze)
 * Параметры:
 *  -s, (--srcDir) &lt;arg&gt; -        Путь к каталогу исходных файлов.
 *                                Возможно указывать как в абсолютном, так и относительном виде. Если параметр опущен,
 *                                то анализ выполняется в текущем каталоге запуска.
 *  -o, (--outputDir) &lt;arg&gt; -     Путь к каталогу размещения отчетов - результатов анализа.
 *                                Возможно указывать как в абсолютном, так и относительном виде. Если параметр опущен,
 *                                то файлы отчета будут сохранены в текущем каталоге запуска.
 *  -c, (--configuration) &lt;arg&gt; - Путь к конфигурационному файлу BSL Language Server (.bsl-language-server.json).
 *                                Возможно указывать как в абсолютном, так и относительном виде. Если параметр опущен,
 *                                то будут использованы настройки по умолчанию.
 *  -r, (--reporter) &lt;arg&gt; -      Ключи "Репортеров", т.е. форматов отчетов, котрые необходимо сгенерировать после
 *                                выполнения анализа. Может быть указано более одного ключа. Если параметр опущен,
 *                                то вывод результата будет призведен в консоль.
 *  -q, (--silent)              -       Флаг для отключения вывода прогресс-бара и дополнительных сообщений в консоль
 * Выводимая информация:
 *  Выполняет анализ каталога исходных файлов и генерацию файлов отчета. Для каждого указанного ключа "Репортера"
 *  создается отдельный файл (каталог файлов). Реализованные "репортеры" находятся в пакете "reporter".
 */
public class AnalyzeCommand implements Command {

  private final CommandLine cmd;
  private DiagnosticProvider diagnosticProvider;
  private ServerContext context;

  public AnalyzeCommand(CommandLine cmd) {
    this.cmd = cmd;
  }

  @Override
  public int execute() {
    String srcDirOption = cmd.getOptionValue("srcDir", "");
    String outputDirOption = cmd.getOptionValue("outputDir", "");
    String configurationOption = cmd.getOptionValue("configuration", "");
    boolean silentMode = cmd.hasOption("silent");

    Path srcDir = Absolute.path(srcDirOption);
    File configurationFile = new File(configurationOption);

    LanguageServerConfiguration configuration = LanguageServerConfiguration.create(configurationFile);

    Path configurationPath = LanguageServerConfiguration.getCustomConfigurationRoot(configuration, srcDir);
    context = new ServerContext(configurationPath);
    DiagnosticSupplier diagnosticSupplier = new DiagnosticSupplier(configuration);
    diagnosticProvider = new DiagnosticProvider(diagnosticSupplier);

    Collection<File> files = FileUtils.listFiles(srcDir.toFile(), new String[]{"bsl", "os"}, true);

    List<FileInfo> fileInfos;
    if (silentMode) {
      fileInfos = files.parallelStream()
        .map((File file) -> getFileInfoFromFile(srcDir, file))
        .collect(Collectors.toList());
    } else {
      try (ProgressBar pb = new ProgressBar("Analyzing files...", files.size(), ProgressBarStyle.ASCII)) {
        fileInfos = files.parallelStream()
          .map((File file) -> {
            pb.step();
            return getFileInfoFromFile(srcDir, file);
          })
          .collect(Collectors.toList());
      }
    }

    AnalysisInfo analysisInfo = new AnalysisInfo(LocalDateTime.now(), fileInfos, srcDirOption);
    Path outputDir = Absolute.path(outputDirOption);
    String[] reporters = Optional.ofNullable(cmd.getOptionValues("reporter")).orElse(new String[0]);
    ReportersAggregator aggregator = new ReportersAggregator(outputDir, reporters);
    aggregator.report(analysisInfo);
    return 0;
  }

  private FileInfo getFileInfoFromFile(Path srcDir, File file) {
    String textDocumentContent;
    try {
      textDocumentContent = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    DocumentContext documentContext = context.addDocument(file.toURI(), textDocumentContent);

    Path filePath = srcDir.relativize(Absolute.path(file));
    List<Diagnostic> diagnostics = diagnosticProvider.computeDiagnostics(documentContext);
    MetricStorage metrics = documentContext.getMetrics();

    FileInfo fileInfo = new FileInfo(filePath, diagnostics, metrics);

    // clean up AST after diagnostic computing to free up RAM.
    documentContext.clearSecondaryData();
    diagnosticProvider.clearComputedDiagnostics(documentContext);

    return fileInfo;
  }

}
