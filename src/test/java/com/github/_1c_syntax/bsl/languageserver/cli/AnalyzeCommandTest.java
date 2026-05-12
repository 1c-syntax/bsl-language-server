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

import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class AnalyzeCommandTest {

  private static final String METADATA_PATH = Path.of(TestUtils.PATH_TO_METADATA).toAbsolutePath().toString();
  private static final String CONFIG_PATH = resolveConfigPath();

  @Autowired
  private AnalyzeCommand analyzeCommand;

  @TempDir
  Path tempDir;

  @Test
  void callWithExcludePathsConfigFiltersFiles() {
    ReflectionTestUtils.setField(analyzeCommand, "srcDirOption", METADATA_PATH);
    ReflectionTestUtils.setField(analyzeCommand, "workspaceDirOption", METADATA_PATH);
    ReflectionTestUtils.setField(analyzeCommand, "outputDirOption", tempDir.toString());
    ReflectionTestUtils.setField(analyzeCommand, "configurationOption", CONFIG_PATH);
    ReflectionTestUtils.setField(analyzeCommand, "silentMode", true);

    var exitCode = analyzeCommand.call();

    assertThat(exitCode).isZero();
  }

  @Test
  void callReturnsOneWhenWorkspaceDirDoesNotExist() {
    var nonexistentWorkspace = tempDir.resolve("nonexistent_workspace").toAbsolutePath().toString();

    ReflectionTestUtils.setField(analyzeCommand, "srcDirOption", METADATA_PATH);
    ReflectionTestUtils.setField(analyzeCommand, "workspaceDirOption", nonexistentWorkspace);
    ReflectionTestUtils.setField(analyzeCommand, "outputDirOption", tempDir.toString());
    ReflectionTestUtils.setField(analyzeCommand, "configurationOption", CONFIG_PATH);
    ReflectionTestUtils.setField(analyzeCommand, "silentMode", true);

    var exitCode = analyzeCommand.call();

    assertThat(exitCode).isOne();
  }

  @Test
  void callReturnsOneWhenSrcDirDoesNotExist() {
    var nonexistentSrc = tempDir.resolve("nonexistent_src").toAbsolutePath().toString();

    ReflectionTestUtils.setField(analyzeCommand, "srcDirOption", nonexistentSrc);
    ReflectionTestUtils.setField(analyzeCommand, "workspaceDirOption", METADATA_PATH);
    ReflectionTestUtils.setField(analyzeCommand, "outputDirOption", tempDir.toString());
    ReflectionTestUtils.setField(analyzeCommand, "configurationOption", CONFIG_PATH);
    ReflectionTestUtils.setField(analyzeCommand, "silentMode", true);

    var exitCode = analyzeCommand.call();

    assertThat(exitCode).isOne();
  }

  @Test
  void callWithoutSilentRunsWithProgressBar() {
    ReflectionTestUtils.setField(analyzeCommand, "srcDirOption", METADATA_PATH);
    ReflectionTestUtils.setField(analyzeCommand, "workspaceDirOption", METADATA_PATH);
    ReflectionTestUtils.setField(analyzeCommand, "outputDirOption", tempDir.toString());
    ReflectionTestUtils.setField(analyzeCommand, "configurationOption", CONFIG_PATH);
    ReflectionTestUtils.setField(analyzeCommand, "silentMode", false);

    var exitCode = analyzeCommand.call();

    assertThat(exitCode).isZero();
  }

  private static String resolveConfigPath() {
    var resource = AnalyzeCommandTest.class.getResource("/.bsl-language-server-exclude-paths.json");
    if (resource == null) {
      return Path.of("src/test/resources/.bsl-language-server-exclude-paths.json").toAbsolutePath().toString();
    }
    try {
      return Paths.get(resource.toURI()).toString();
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }
}
