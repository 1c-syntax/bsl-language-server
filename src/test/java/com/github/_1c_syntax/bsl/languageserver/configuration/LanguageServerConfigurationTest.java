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
package com.github._1c_syntax.bsl.languageserver.configuration;

import com.github._1c_syntax.bsl.languageserver.configuration.diagnostics.DiagnosticsOptions;
import com.github._1c_syntax.bsl.languageserver.configuration.diagnostics.Language;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static com.github._1c_syntax.bsl.languageserver.configuration.diagnostics.Language.DEFAULT_LANGUAGE;
import static org.assertj.core.api.Assertions.assertThat;

class LanguageServerConfigurationTest {

  private static final String PATH_TO_CONFIGURATION_FILE = "./src/test/resources/.bsl-language-server.json";
  private static final String PATH_TO_EMPTY_CONFIGURATION_FILE = "./src/test/resources/.empty-bsl-language-server.json";
  private static final String PATH_TO_METADATA = "src/test/resources/metadata";

  @BeforeEach
  void startUp() throws IOException {
    try {
      Files.deleteIfExists(Paths.get("build/.trace.log"));
    } catch (FileSystemException e) {
      // no-op
    }
  }

  @Test
  void createDefault() {
    // when
    LanguageServerConfiguration configuration = LanguageServerConfiguration.create();

    // then
    assertThat(configuration.getDiagnosticsOptions().getLanguage()).isEqualTo(com.github._1c_syntax.bsl.languageserver.configuration.diagnostics.Language.RU);
    assertThat(configuration.getDiagnosticsOptions().getRules()).isEmpty();
  }

  @Test
  void createFromFile() {

    // given
    File configurationFile = new File(PATH_TO_CONFIGURATION_FILE);

    // when
    LanguageServerConfiguration configuration = LanguageServerConfiguration.create(configurationFile);

    // then
    DiagnosticsOptions diagnosticsOptions = configuration.getDiagnosticsOptions();
    Language language = diagnosticsOptions.getLanguage();
    Map<String, Either<Boolean, Map<String, Object>>> diagnostics = diagnosticsOptions.getRules();

    assertThat(language).isEqualTo(Language.EN);
    assertThat(diagnostics).hasSize(2);

    Either<Boolean, Map<String, Object>> lineLength = diagnostics.get("LineLength");
    assertThat(lineLength.isRight()).isTrue();
    assertThat(lineLength.getRight()).isInstanceOfAny(Map.class);
    assertThat(lineLength.getRight())
      .extracting(stringObjectMap -> stringObjectMap.get("maxLineLength"))
      .isEqualTo(140);

    Either<Boolean, Map<String, Object>> methodSize = diagnostics.get("MethodSize");
    assertThat(methodSize.isLeft()).isTrue();
    assertThat(methodSize.getLeft()).isEqualTo(false);

    Path configurationRoot = configuration.getConfigurationRoot();
    assertThat(configurationRoot).isNotEqualTo(null);

  }

  @Test
  void createFromEmptyFile() {

    // given
    File configurationFile = new File(PATH_TO_EMPTY_CONFIGURATION_FILE);

    // when
    LanguageServerConfiguration configuration = LanguageServerConfiguration.create(configurationFile);

    // then
    DiagnosticsOptions diagnosticsOptions = configuration.getDiagnosticsOptions();
    Language language = diagnosticsOptions.getLanguage();
    Map<String, Either<Boolean, Map<String, Object>>> rules = diagnosticsOptions.getRules();

    assertThat(language).isEqualTo(DEFAULT_LANGUAGE);
    assertThat(rules).isEmpty();

  }

  @Test
  void test_GetCustomConfigurationRoot() {

    LanguageServerConfiguration configuration = LanguageServerConfiguration.create();
    Path path = Paths.get(PATH_TO_METADATA);
    Path configurationRoot = LanguageServerConfiguration.getCustomConfigurationRoot(configuration, path);
    assertThat(configurationRoot).isEqualTo(Absolute.path(path));

    File configurationFile = new File(PATH_TO_CONFIGURATION_FILE);
    configuration = LanguageServerConfiguration.create(configurationFile);
    configurationRoot = LanguageServerConfiguration.getCustomConfigurationRoot(configuration, path);
    assertThat(configurationRoot).isEqualTo(Absolute.path(path));

  }

}