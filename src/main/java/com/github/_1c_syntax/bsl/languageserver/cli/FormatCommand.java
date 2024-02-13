/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2024
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

import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.providers.FormatProvider;
import com.github._1c_syntax.utils.Absolute;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import me.tongfei.progressbar.ProgressBarStyle;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.TextEdit;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

import static picocli.CommandLine.Command;
import static picocli.CommandLine.Option;

/**
 * Форматирование кода в исходниках
 * Ключ команды:
 *  -f, (--format)
 * Параметры:
 *  -s, (--src)                -  Путь к каталогу исходных файлов.
 *                                Возможно указывать как в абсолютном, так и относительном виде. Если параметр опущен,
 *                                то анализ выполняется в текущем каталоге запуска.
 *                                Можно указать каталог, в котором будут найдены файлы для форматирования, либо один
 *                                файл для форматирования
 *  -q, (--silent)             -  Флаг для отключения вывода прогресс-бара и дополнительных сообщений в консоль
 * Выводимая информация:
 *  Выполняет форматирование исходного кода в файлах каталога. Для форматирования используются правила и настройки
 *  "форматтера" FormatProvider, т.е. пользователь никак не может овлиять на результат.
 */
@Slf4j
@Command(
  name = "format",
  aliases = {"-f", "--format"},
  description = "Format files in source directory",
  usageHelpAutoWidth = true,
  footer = "@|green Copyright(c) 2018-2022|@")
@Component
@RequiredArgsConstructor
public class FormatCommand implements Callable<Integer> {

  private static final Pattern COMMA_PATTERN = Pattern.compile(",");
  private final ServerContext serverContext;
  private final FormatProvider formatProvider;

  @Option(
    names = {"-h", "--help"},
    usageHelp = true,
    description = "Show this help message and exit")
  private boolean usageHelpRequested;

  @Option(
    names = {"-s", "--srcDir", "--src"}, // TODO delete old key --srcDir
    description = "Source directory or file",
    paramLabel = "<path>",
    defaultValue = "")
  private String srcDirOption;

  @Option(
    names = {"-q", "--silent"},
    description = "Silent mode")
  private boolean silentMode;

  public Integer call() {
    serverContext.clear();

    String[] filePaths = COMMA_PATTERN.split(srcDirOption);

    List<File> files = findFilesForFormatting(filePaths);

    if (files.isEmpty()) {
      return 1;
    }

    if (silentMode) {
      files.parallelStream().forEach(this::formatFile);
    } else {
      try (ProgressBar pb = new ProgressBarBuilder()
        .setTaskName("Formatting files...")
        .setInitialMax(files.size())
        .setStyle(ProgressBarStyle.ASCII)
        .build()) {
        files.parallelStream()
          .forEach((File file) -> {
            pb.step();
            formatFile(file);
          });
      }
    }

    return 0;
  }

  private List<File> findFilesForFormatting(String[] filePaths) {
    List<File> files = new ArrayList<>();
    for (String filePath : filePaths) {
      Path srcDir = Absolute.path(filePath);
      if (!srcDir.toFile().exists()) {
        LOGGER.error("Source dir `{}` is not exists", srcDir);
        continue;
      }

      if(srcDir.toFile().isDirectory()) {
        files.addAll(FileUtils.listFiles(srcDir.toFile(), new String[]{"bsl", "os"}, true));
      } else {
        files.add(srcDir.toFile());
      }
    }

    return files;
  }

  @SneakyThrows
  private void formatFile(File file) {
    var uri = file.toURI();

    var documentContext = serverContext.addDocument(uri);
    serverContext.rebuildDocument(documentContext);

    var params = new DocumentFormattingParams();
    var options = new FormattingOptions();
    options.setInsertSpaces(false);

    params.setOptions(options);
    final List<TextEdit> formatting = formatProvider.getFormatting(params, documentContext);

    serverContext.removeDocument(uri);

    if (formatting.isEmpty()) {
      return;
    }

    final TextEdit textEdit = formatting.get(0);
    final String newText = textEdit.getNewText();
    FileUtils.writeStringToFile(file, newText, StandardCharsets.UTF_8);
  }

}
