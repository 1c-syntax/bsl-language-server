/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2026
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

import com.github._1c_syntax.bsl.languageserver.configuration.GlobalLanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.bsl.languageserver.reporters.ReportContext;
import com.github._1c_syntax.bsl.languageserver.reporters.ReportSession;
import com.github._1c_syntax.bsl.languageserver.reporters.ReportersAggregator;
import com.github._1c_syntax.bsl.languageserver.reporters.data.FileInfo;
import com.github._1c_syntax.bsl.languageserver.utils.BSLFiles;
import com.github._1c_syntax.utils.Absolute;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import me.tongfei.progressbar.ProgressBarStyle;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

import static picocli.CommandLine.Option;

/**
 * Выполнение анализа
 * Ключ команды:
 * -a, (--analyze)
 * Параметры:
 * -s, (--srcDir) &lt;arg&gt; -        Путь к каталогу исходных файлов.
 * Возможно указывать как в абсолютном, так и относительном виде. Если параметр опущен,
 * то анализ выполняется в текущем каталоге запуска.
 * -o, (--outputDir) &lt;arg&gt; -     Путь к каталогу размещения отчетов - результатов анализа.
 * Возможно указывать как в абсолютном, так и относительном виде. Если параметр опущен,
 * то файлы отчета будут сохранены в текущем каталоге запуска.
 * -w, (--workspaceDir) &lt;arg&gt; -  Путь к каталогу проекта, относительно которого располагаются исходные файлы.
 * Возможно указывать как в абсолютном, так и в относительном виде. Если параметр опущен,
 * то пути к исходным файлам будут указываться относительно текущего каталога запуска.
 * -c, (--configuration) &lt;arg&gt; - Путь к конфигурационному файлу BSL Language Server (.bsl-language-server.json).
 * Возможно указывать как в абсолютном, так и относительном виде. Если параметр опущен,
 * то будут использованы настройки по умолчанию.
 * -r, (--reporter) &lt;arg&gt; -      Ключи "Репортеров", т.е. форматов отчетов, котрые необходимо сгенерировать после
 * выполнения анализа. Может быть указано более одного ключа. Если параметр опущен,
 * то вывод результата будет призведен в консоль.
 * -q, (--silent)              -       Флаг для отключения вывода прогресс-бара и дополнительных сообщений в консоль
 * Выводимая информация:
 * Выполняет анализ каталога исходных файлов и генерацию файлов отчета. Для каждого указанного ключа "Репортера"
 * создается отдельный файл (каталог файлов). Реализованные "репортеры" находятся в пакете "reporter".
 **/
@Slf4j
@Command(
  name = "analyze",
  aliases = {"-a", "--analyze"},
  description = "Run analysis and get diagnostic info",
  usageHelpAutoWidth = true,
  footer = "@|green Copyright(c) 2018-2025|@")
@Component
@RequiredArgsConstructor
public class AnalyzeCommand implements Callable<Integer> {

  private static class ReportersKeys extends ArrayList<String> {
    ReportersKeys(ReportersAggregator aggregator) {
      super(aggregator.reporterKeys());
    }
  }

  @Option(
    names = {"-h", "--help"},
    usageHelp = true,
    description = "Show this help message and exit")
  private boolean usageHelpRequested;

  @Option(
    names = {"-w", "--workspaceDir"},
    description = "Workspace directory",
    paramLabel = "<path>",
    defaultValue = "")
  private String workspaceDirOption;

  @Option(
    names = {"-s", "--srcDir"},
    description = "Source directory",
    paramLabel = "<path>",
    defaultValue = "")
  private String srcDirOption;

  @Option(
    names = {"-o", "--outputDir"},
    description = "Output report directory",
    paramLabel = "<path>",
    defaultValue = "")
  private String outputDirOption;

  @Option(
    names = {"-c", "--configuration"},
    description = "Path to language server configuration file",
    paramLabel = "<path>",
    defaultValue = "")
  private String configurationOption;

  @Option(
    names = {"-r", "--reporter"},
    paramLabel = "<keys>",
    completionCandidates = ReportersKeys.class,
    description = "Reporter key (${COMPLETION-CANDIDATES})")
  private String[] reportersOptions = {};

  @Option(
    names = {"-q", "--silent"},
    description = "Silent mode")
  private boolean silentMode;

  private final ReportersAggregator aggregator;
  private final GlobalLanguageServerConfiguration globalConfiguration;
  private final ServerContextProvider serverContextProvider;
  private final LanguageServerConfiguration configuration;
  @Qualifier("cliExecutor")
  private final ExecutorService cliExecutor;

  private ServerContext serverContext;

  public Integer call() {

    var workspaceDir = Absolute.path(workspaceDirOption);
    if (!workspaceDir.toFile().exists()) {
      LOGGER.error("Workspace dir `{}` is not exists", workspaceDir);
      return 1;
    }

    var srcDir = Absolute.path(srcDirOption);
    if (!srcDir.toFile().exists()) {
      LOGGER.error("Source dir `{}` is not exists", srcDir);
      return 1;
    }

    var configurationFile = new File(configurationOption);

    // Update global configuration
    globalConfiguration.update(configurationFile);

    // -c влияет и на глобальную, и на per-workspace конфигурацию (в analyze всегда ровно один
    // workspace). Обновляем workspace-scoped конфигурацию ДО addWorkspace, чтобы тот сам
    // подхватил из неё configurationRoot (см. ServerContextProvider#addWorkspace) и типы
    // конфигурации зарегистрировались уже на WorkspaceAddedEvent — без отдельного события.
    // forUri с именем задаёт контекст для ещё не зарегистрированного workspace; addWorkspace
    // зарегистрирует его позже и прочитает уже обновлённую конфигурацию (тот же инстанс LSC).
    try (var ctx = WorkspaceContextHolder.forUri(srcDir.toUri(), srcDir.getFileName().toString())) {
      configuration.update(configurationFile);
    }

    // Create workspace for srcDir (factory will create per-workspace configuration)
    serverContext = serverContextProvider.addWorkspace(srcDir.toUri());

    try (var ctx = WorkspaceContextHolder.forUri(srcDir.toUri())) {
      var files = new ArrayList<>(BSLFiles.listBslFiles(srcDir, configuration.getExcludePaths()));

      serverContext.populateContext(files);

      // Метрики вычисляются лениво и стоят дорого. Считаем их только если хотя бы одному
      // активному репортеру они действительно нужны (см. ReportersAggregator).
      var metricCalculationRequired = aggregator.isMetricCalculationRequired();

      var context = new ReportContext(LocalDateTime.now(), srcDir.toString());
      var outputDir = Absolute.path(outputDirOption);

      // Результаты передаются репортёрам по одному сразу после разбора файла и больше нигде не
      // удерживаются: пик памяти не зависит от размера конфигурации (см. issue #4412).
      try (var session = aggregator.beginReport(context, outputDir)) {
        analyze(files, workspaceDir, metricCalculationRequired, session);
        session.commit();
      }
      return 0;
    } catch (ExecutionException e) {
      throw new IllegalStateException("Error analyzing files", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while analyzing files", e);
    }
  }

  public String[] getReportersOptions() {
    return reportersOptions.clone();
  }

  private void analyze(
    List<File> files,
    Path workspaceDir,
    boolean metricCalculationRequired,
    ReportSession session
  ) throws ExecutionException, InterruptedException {
    if (silentMode) {
      analyze(files, workspaceDir, metricCalculationRequired, session, null);
      return;
    }

    try (var progressBar = new ProgressBarBuilder()
      .setTaskName("Analyzing files...")
      .setInitialMax(files.size())
      .setStyle(ProgressBarStyle.ASCII)
      .build()) {
      analyze(files, workspaceDir, metricCalculationRequired, session, progressBar);
    }
  }

  private void analyze(
    List<File> files,
    Path workspaceDir,
    boolean metricCalculationRequired,
    ReportSession session,
    @Nullable ProgressBar progressBar
  ) throws ExecutionException, InterruptedException {
    cliExecutor.submit(() ->
      files.parallelStream()
        .map((File file) -> {
          if (progressBar != null) {
            progressBar.step();
          }
          return getFileInfoFromFile(workspaceDir, file, metricCalculationRequired);
        })
        .forEach(session::accept)
    ).get();
  }

  private FileInfo getFileInfoFromFile(Path srcDir, File file, boolean metricCalculationRequired) {
    var documentContext = serverContext.addDocument(Absolute.uri(file));
    serverContext.rebuildDocument(documentContext);

    var filePath = srcDir.relativize(Absolute.path(file));
    var diagnostics = documentContext.getDiagnostics();
    var metrics = metricCalculationRequired ? documentContext.getMetrics() : null;
    var mdoRef = documentContext.getMdoRef();

    var fileInfo = new FileInfo(filePath, mdoRef, diagnostics, metrics);

    // clean up AST after diagnostic computing to free up RAM.
    // Документ заморожен в populateContext: между populate и вычислением диагностик файл не
    // меняется, поэтому заморозка бережёт уже построенные ленивые данные от очистки/пересчёта
    // (флаг влияет только на очистку). Здесь все нужные чтения (getDiagnostics, getMdoRef и,
    // при необходимости, getMetrics) уже выполнены и захвачены в FileInfo, документ дальше не
    // используется — размораживаем
    // ПЕРЕД финальной очисткой, чтобы tryClearDocument освободил вторичные данные
    // (сложность/метрики/подавления), а не держал их на всю конфигурацию (см. issue #4248).
    // Саму заморозку это не отменяет: разморозка только на финальном clear, оптимизация
    // populate -> diagnostics не затрагивается.
    documentContext.unfreezeComputedData();
    serverContext.tryClearDocument(documentContext);

    return fileInfo;
  }
}
