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
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarStyle;
import org.apache.commons.cli.CommandLine;
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

/**
 * Форматирование кода в исходниках
 * TODO: дописать
 */
public class FormatCommand implements Command {

  private CommandLine cmd;
  private ServerContext serverContext;

  public FormatCommand(CommandLine cmd) {
    this.cmd = cmd;
    this.serverContext = new ServerContext();
  }

  @Override
  public int execute() {
    serverContext.clear();

    String srcDirOption = cmd.getOptionValue("srcDir", "");

    Path srcDir = Absolute.path(srcDirOption);

    Collection<File> files = FileUtils.listFiles(srcDir.toFile(), new String[]{"bsl", "os"}, true);

    try (ProgressBar pb = new ProgressBar("Formatting files...", files.size(), ProgressBarStyle.ASCII)) {
      files.parallelStream()
        .forEach((File file) -> {
          pb.step();
          formatFile(file);
        });
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
