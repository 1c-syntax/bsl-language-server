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

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.lsif.LsifIndexer;
import com.github._1c_syntax.bsl.languageserver.lsif.LsifOutputFormat;
import com.github._1c_syntax.utils.Absolute;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Генерация LSIF-индекса.
 * <p>
 * Ключ команды: lsif
 * <p>
 * Параметры:
 * <ul>
 *   <li>-s, --srcDir — путь к каталогу исходных файлов</li>
 *   <li>-o, --output — путь к выходному файлу (по умолчанию: dump.lsif)</li>
 *   <li>-f, --format — формат вывода: ndjson или json (по умолчанию: ndjson)</li>
 *   <li>-c, --configuration — путь к конфигурационному файлу</li>
 * </ul>
 */
@Slf4j
@Command(
  name = "lsif",
  aliases = {"-l", "--lsif"},
  description = "Generate LSIF index for code navigation",
  usageHelpAutoWidth = true,
  footer = "@|green Copyright(c) 2018-2026|@")
@Component
@RequiredArgsConstructor
public class LsifCommand implements Callable<Integer> {

  private static final String DEFAULT_OUTPUT = "dump.lsif";

  @Option(
    names = {"-h", "--help"},
    usageHelp = true,
    description = "Show this help message and exit")
  private boolean usageHelpRequested;

  @Option(
    names = {"-s", "--srcDir"},
    description = "Source directory",
    paramLabel = "<path>",
    defaultValue = "")
  private String srcDirOption;

  @Option(
    names = {"-o", "--output"},
    description = "Output LSIF file (default: dump.lsif)",
    paramLabel = "<path>",
    defaultValue = DEFAULT_OUTPUT)
  private String outputOption;

  @Option(
    names = {"-f", "--format"},
    description = "Output format: ndjson (default) or json. NDJSON is recommended for large projects.",
    paramLabel = "<format>",
    defaultValue = "NDJSON")
  private LsifOutputFormat formatOption;

  @Option(
    names = {"-c", "--configuration"},
    description = "Path to language server configuration file",
    paramLabel = "<path>",
    defaultValue = "")
  private String configurationOption;

  private final LanguageServerConfiguration configuration;
  private final ServerContext context;
  private final LsifIndexer lsifIndexer;

  @Override
  public Integer call() {
    Path srcDir = Absolute.path(srcDirOption);
    if (!srcDir.toFile().exists()) {
      LOGGER.error("Source dir `{}` does not exist", srcDir);
      return 1;
    }

    var configurationFile = new File(configurationOption);
    configuration.update(configurationFile);

    var configurationPath = LanguageServerConfiguration.getCustomConfigurationRoot(configuration, srcDir);
    context.setConfigurationRoot(configurationPath);

    Path outputFile = Absolute.path(outputOption);
    String toolVersion = getClass().getPackage().getImplementationVersion();
    if (toolVersion == null) {
      toolVersion = "dev";
    }

    try {
      lsifIndexer.index(srcDir, outputFile, toolVersion, formatOption);
      LOGGER.info("LSIF index generated: {} (format: {})", outputFile, formatOption.getFormatId());
      return 0;
    } catch (Exception e) {
      LOGGER.error("Failed to generate LSIF index", e);
      return 1;
    }
  }
}
