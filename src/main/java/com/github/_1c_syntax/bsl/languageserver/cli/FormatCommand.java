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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.providers.FormatProvider;
import com.github._1c_syntax.utils.Absolute;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarStyle;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.TextEdit;

import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

import static picocli.CommandLine.Command;
import static picocli.CommandLine.Option;

/**
 * Форматирование кода в исходниках
 * Ключ команды:
 *  -f, (--format)
 * Параметры:
 *  -s, (--srcDir) &lt;arg&gt; -  Путь к каталогу исходных файлов.
 *                                Возможно указывать как в абсолютном, так и относительном виде. Если параметр опущен,
 *                                то анализ выполняется в текущем каталоге запуска.
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
  footer = "@|green Copyright(c) 2018-2020|@")
public class FormatCommand implements Callable<Integer> {

  private final ServerContext serverContext;

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
    names = {"--silent"},
    description = "Silent mode")
  private boolean silentMode;

  public FormatCommand() {
    this.serverContext = new ServerContext();
  }

  public Integer call() {
    serverContext.clear();

    Path srcDir = Absolute.path(srcDirOption);
    if (!srcDir.toFile().exists()) {
      LOGGER.error("Source dir `{}` is not exists", srcDir.toString());
      return 1;
    }

    Collection<File> files = FileUtils.listFiles(srcDir.toFile(), new String[]{"bsl", "os"}, true);

    if (silentMode) {
      files.parallelStream().forEach(this::formatFile);
    } else {
      try (ProgressBar pb = new ProgressBar("Formatting files...", files.size(), ProgressBarStyle.ASCII)) {
        files.parallelStream()
          .forEach((File file) -> {
            pb.step();
            formatFile(file);
          });
      }
    }

    return 0;
  }

  @SneakyThrows
  private void formatFile(File file) {
    String textDocumentContent = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
    final URI uri = file.toURI();

    DocumentContext documentContext = serverContext.addDocument(uri, textDocumentContent);

    DocumentFormattingParams params = new DocumentFormattingParams();
    FormattingOptions options = new FormattingOptions();
    options.setInsertSpaces(false);

    params.setOptions(options);
    final List<TextEdit> formatting = FormatProvider.getFormatting(params, documentContext);

    serverContext.removeDocument(uri);

    if (formatting.isEmpty()) {
      return;
    }

    final TextEdit textEdit = formatting.get(0);
    final String newText = textEdit.getNewText();
    FileUtils.writeStringToFile(file, newText, StandardCharsets.UTF_8);
  }

}
