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
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import picocli.CommandLine;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тесты для {@link FormatCommand}.
 */
@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class FormatCommandTest {

  private static final File METADATA_FIXTURE = new File(TestUtils.PATH_TO_METADATA);

  @Autowired
  private FormatCommand formatCommand;

  @Autowired
  private ServerContext serverContext;

  @Autowired
  private LanguageServerConfiguration configuration;

  @TempDir
  Path tempDir;

  /** Форматирование с настроенным {@code excludePaths} проходит успешно. */
  @Test
  void callWithExcludePathsRunsSuccessfully() throws Exception {
    serverContext.setConfigurationRoot(tempDir);
    configuration.setExcludePaths(List.of("Catalogs"));

    FileUtils.copyDirectory(METADATA_FIXTURE, tempDir.toFile());

    var exitCode = new CommandLine(formatCommand).execute(
      "-s", tempDir.toAbsolutePath().toString(),
      "-q"
    );

    assertThat(exitCode).isZero();
  }

  /** Если в указанном каталоге нет BSL/OS файлов — команда возвращает код 1. */
  @Test
  void callReturnsOneWhenNoFilesFound() throws Exception {
    serverContext.setConfigurationRoot(tempDir);
    var noBslDir = tempDir.resolve("noBsl").toFile();
    FileUtils.copyDirectory(new File(METADATA_FIXTURE, "Languages"), noBslDir);

    var exitCode = new CommandLine(formatCommand).execute(
      "-s", noBslDir.getAbsolutePath(),
      "-q"
    );

    assertThat(exitCode).isOne();
  }

  /** Несуществующий путь логируется и пропускается; при отсутствии файлов возвращается код 1. */
  @Test
  void callSkipsNonExistentPathAndReturnsOneWhenNoFiles() {
    serverContext.setConfigurationRoot(tempDir);
    var exitCode = new CommandLine(formatCommand).execute(
      "-s", tempDir.resolve("nonexistent").toAbsolutePath().toString(),
      "-q"
    );

    assertThat(exitCode).isOne();
  }

  /** Можно передать путь к одному файлу — он будет отформатирован. */
  @Test
  void callFormatsSingleFile() throws Exception {
    serverContext.setConfigurationRoot(tempDir);
    var fixtureFile = new File(METADATA_FIXTURE, "CommonModules/ОбщегоНазначения/Ext/Module.bsl");
    var singleFile = tempDir.resolve("Single.bsl").toFile();
    FileUtils.copyFile(fixtureFile, singleFile);

    var exitCode = new CommandLine(formatCommand).execute(
      "-s", singleFile.getAbsolutePath(),
      "-q"
    );

    assertThat(exitCode).isZero();
  }

  /** Опция {@code -s} принимает несколько путей через запятую, файлы собираются из всех. */
  @Test
  void callWithCommaSeparatedPathsFormatsFromBoth() throws Exception {
    serverContext.setConfigurationRoot(tempDir);
    FileUtils.copyDirectory(METADATA_FIXTURE, tempDir.toFile());

    var commonModules = tempDir.resolve("CommonModules").toFile();
    var documents = tempDir.resolve("Documents").toFile();
    var exitCode = new CommandLine(formatCommand).execute(
      "-s", commonModules.getAbsolutePath() + "," + documents.getAbsolutePath(),
      "-q"
    );

    assertThat(exitCode).isZero();
  }

  /** Без флага {@code -q} форматирование выводит прогресс-бар и завершается успешно. */
  @Test
  void callWithoutSilentShowsProgressBar() throws Exception {
    serverContext.setConfigurationRoot(tempDir);
    FileUtils.copyDirectory(METADATA_FIXTURE, tempDir.toFile());

    var exitCode = new CommandLine(formatCommand).execute(
      "-s", tempDir.toAbsolutePath().toString()
    );

    assertThat(exitCode).isZero();
  }
}
