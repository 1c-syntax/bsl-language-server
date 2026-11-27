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
package com.github._1c_syntax.bsl.languageserver.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationFilesTest {

  private static final String CONFIG_NAME = ".bsl-language-server.json";

  @TempDir
  Path tempDir;

  @Test
  void locatePrefersExplicitPathOverGlobal() throws IOException {
    // given
    var explicit = Files.writeString(tempDir.resolve("explicit.json"), "{}");
    var workspace = Files.createDirectories(tempDir.resolve("workspace"));
    var global = Files.writeString(tempDir.resolve("global.json"), "{}");

    // when
    var located = ConfigurationFiles.locate(explicit.toString(), workspace, global.toString());

    // then
    assertThat(located).contains(explicit.toFile());
  }

  @Test
  void locateResolvesConfigurationPathAgainstWorkspaceRoot() throws IOException {
    // given
    var workspace = Files.createDirectories(tempDir.resolve("workspace"));
    var workspaceConfig = Files.writeString(workspace.resolve(CONFIG_NAME), "{}");
    var global = Files.writeString(tempDir.resolve("global.json"), "{}");

    // when
    var located = ConfigurationFiles.locate(CONFIG_NAME, workspace, global.toString());

    // then
    assertThat(located).contains(workspaceConfig.toFile());
  }

  @Test
  void locateFallsBackToGlobalAndSkipsDirectories() throws IOException {
    // given
    var workspace = Files.createDirectories(tempDir.resolve("workspace"));
    Files.createDirectories(workspace.resolve(CONFIG_NAME));
    var global = Files.writeString(tempDir.resolve("global.json"), "{}");

    // when
    var located = ConfigurationFiles.locate(CONFIG_NAME, workspace, global.toString());

    // then
    assertThat(located).contains(global.toFile());
  }

  @Test
  void locateWithoutWorkspaceRootChecksOnlyExplicitAndGlobal() {
    // when
    var located = ConfigurationFiles.locate(
      tempDir.resolve("missing.json").toString(),
      null,
      tempDir.resolve("missing-global.json").toString()
    );

    // then
    assertThat(located).isEmpty();
  }

  @Test
  void readReturnsEmptyForMalformedJson() throws IOException {
    // given
    var malformed = Files.writeString(tempDir.resolve(CONFIG_NAME), "{\"language\": ");

    // when
    var read = ConfigurationFiles.read(malformed.toFile(), GlobalLanguageServerConfiguration.class);

    // then
    assertThat(read).isEmpty();
  }

  @Test
  void readAcceptsCaseInsensitiveEnums() throws IOException {
    // given
    var config = Files.writeString(tempDir.resolve(CONFIG_NAME), "{\"language\": \"En\"}");

    // when
    var read = ConfigurationFiles.read(config.toFile(), GlobalLanguageServerConfiguration.class);

    // then
    assertThat(read).hasValueSatisfying(loaded -> assertThat(loaded.getLanguage()).isEqualTo(Language.EN));
  }
}
