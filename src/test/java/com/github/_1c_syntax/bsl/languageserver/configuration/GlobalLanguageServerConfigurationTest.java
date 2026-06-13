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

import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalLanguageServerConfigurationTest {

  @TempDir
  Path tempDir;

  @Test
  void onApplicationReady_cwdConfigExists_loadsFromCwd() throws Exception {
    // given
    var cwdConfig = tempDir.resolve("cwd-config.json");
    Files.writeString(cwdConfig, "{\"language\": \"en\"}");

    var globalConfig = tempDir.resolve("global-config.json");
    Files.writeString(globalConfig, "{\"language\": \"ru\"}");

    var configuration = createConfiguration(
      cwdConfig.toString(),
      globalConfig.toString()
    );

    // when
    configuration.onApplicationReady();

    // then — CWD config takes priority
    assertThat(configuration.getLanguage()).isEqualTo(Language.EN);
    assertThat(configuration.getConfigurationFile())
      .isNotNull()
      .isEqualTo(cwdConfig.toFile());
  }

  @Test
  void onApplicationReady_cwdConfigMissing_fallsBackToGlobal() throws Exception {
    // given
    var globalConfig = tempDir.resolve("global-config.json");
    Files.writeString(globalConfig, "{\"language\": \"en\"}");

    var configuration = createConfiguration(
      tempDir.resolve("non-existent.json").toString(),
      globalConfig.toString()
    );

    // when
    configuration.onApplicationReady();

    // then — falls back to global
    assertThat(configuration.getLanguage()).isEqualTo(Language.EN);
    assertThat(configuration.getConfigurationFile())
      .isNotNull()
      .isEqualTo(globalConfig.toFile());
  }

  @Test
  void onApplicationReady_neitherExists_usesDefaults() throws Exception {
    // given
    var configuration = createConfiguration(
      tempDir.resolve("non-existent-cwd.json").toString(),
      tempDir.resolve("non-existent-global.json").toString()
    );

    // when
    configuration.onApplicationReady();

    // then — defaults
    assertThat(configuration.getLanguage()).isEqualTo(Language.DEFAULT_LANGUAGE);
    assertThat(configuration.getSendErrors()).isEqualTo(SendErrorsMode.DEFAULT);
    assertThat(configuration.getConfigurationFile()).isNull();
  }

  @Test
  void onApplicationReady_capabilitiesPresent_loadsTextDocumentSyncKind() throws Exception {
    // given
    var cwdConfig = tempDir.resolve("cwd-config.json");
    Files.writeString(cwdConfig, """
      {
        "capabilities": {
          "textDocumentSync": {
            "change": "Full"
          }
        }
      }
      """);

    var configuration = createConfiguration(
      cwdConfig.toString(),
      tempDir.resolve("non-existent-global.json").toString()
    );

    // when
    configuration.onApplicationReady();

    // then
    assertThat(configuration.getCapabilities().getTextDocumentSync().getChange())
      .isEqualTo(TextDocumentSyncKind.Full);
  }

  @Test
  void onApplicationReady_capabilitiesMissing_usesDefaultSyncKind() throws Exception {
    // given
    var configuration = createConfiguration(
      tempDir.resolve("non-existent-cwd.json").toString(),
      tempDir.resolve("non-existent-global.json").toString()
    );

    // when
    configuration.onApplicationReady();

    // then
    assertThat(configuration.getCapabilities().getTextDocumentSync().getChange())
      .isEqualTo(TextDocumentSyncKind.Incremental);
  }

  private static GlobalLanguageServerConfiguration createConfiguration(
    String configurationFilePath,
    String globalConfigPath
  ) throws Exception {
    var config = new GlobalLanguageServerConfiguration();
    setField(config, "configurationFilePath", configurationFilePath);
    setField(config, "globalConfigPath", globalConfigPath);
    return config;
  }

  private static void setField(Object target, String fieldName, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }
}
