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

import com.github._1c_syntax.bsl.languageserver.configuration.codelens.CodeLensOptions;
import com.github._1c_syntax.bsl.languageserver.configuration.diagnostics.DiagnosticsOptions;
import com.github._1c_syntax.bsl.languageserver.configuration.diagnostics.Mode;
import com.github._1c_syntax.bsl.languageserver.configuration.diagnostics.SkipSupport;
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

import static com.github._1c_syntax.bsl.languageserver.configuration.Language.DEFAULT_LANGUAGE;
import static org.assertj.core.api.Assertions.assertThat;

class LanguageServerConfigurationTest {

  private static final String PATH_TO_CONFIGURATION_FILE = "./src/test/resources/.bsl-language-server.json";
  private static final String PATH_TO_EMPTY_CONFIGURATION_FILE = "./src/test/resources/.empty-bsl-language-server.json";
  private static final String PATH_TO_METADATA = "src/test/resources/metadata";
  private static final String PATH_TO_PARTIAL_CONFIGURATION_FILE
    = "./src/test/resources/.partial-bsl-language-server.json";

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
    assertThat(configuration.getLanguage()).isEqualTo(Language.RU);
    assertThat(configuration.getDiagnosticsOptions().getParameters()).isEmpty();
  }

  @Test
  void createFromFile() {

    // given
    File configurationFile = new File(PATH_TO_CONFIGURATION_FILE);

    // when
    LanguageServerConfiguration configuration = LanguageServerConfiguration.create(configurationFile);

    // then
    DiagnosticsOptions diagnosticsOptions = configuration.getDiagnosticsOptions();
    Language language = configuration.getLanguage();
    Map<String, Either<Boolean, Map<String, Object>>> parameters = diagnosticsOptions.getParameters();

    assertThat(language).isEqualTo(Language.EN);
    assertThat(parameters).hasSize(2);

    Either<Boolean, Map<String, Object>> lineLength = parameters.get("LineLength");
    assertThat(lineLength.isRight()).isTrue();
    assertThat(lineLength.getRight()).isInstanceOfAny(Map.class);
    assertThat(lineLength.getRight())
      .extracting(stringObjectMap -> stringObjectMap.get("maxLineLength"))
      .isEqualTo(140);

    Either<Boolean, Map<String, Object>> methodSize = parameters.get("MethodSize");
    assertThat(methodSize.isLeft()).isTrue();
    assertThat(methodSize.getLeft()).isEqualTo(false);

    Path configurationRoot = configuration.getConfigurationRoot();
    assertThat(configurationRoot).isNotEqualTo(null);

    assertThat(configuration.getDocumentLinkOptions().useDevSite()).isTrue();

  }

  @Test
  void createFromEmptyFile() {

    // given
    File configurationFile = new File(PATH_TO_EMPTY_CONFIGURATION_FILE);

    // when
    LanguageServerConfiguration configuration = LanguageServerConfiguration.create(configurationFile);

    // then
    DiagnosticsOptions diagnosticsOptions = configuration.getDiagnosticsOptions();
    Language language = configuration.getLanguage();
    Map<String, Either<Boolean, Map<String, Object>>> parameters = diagnosticsOptions.getParameters();

    assertThat(language).isEqualTo(DEFAULT_LANGUAGE);
    assertThat(parameters).isEmpty();

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

  @Test
  void testPartialInitialization() {
    // given
    File configurationFile = new File(PATH_TO_PARTIAL_CONFIGURATION_FILE);

    // when
    LanguageServerConfiguration configuration = LanguageServerConfiguration.create(configurationFile);

    CodeLensOptions codeLensOptions = configuration.getCodeLensOptions();
    DiagnosticsOptions diagnosticsOptions = configuration.getDiagnosticsOptions();

    // then
    assertThat(codeLensOptions.isShowCognitiveComplexity()).isTrue();
    assertThat(codeLensOptions.isShowCyclomaticComplexity()).isFalse();

    assertThat(configuration.getLanguage()).isEqualTo(DEFAULT_LANGUAGE);

    assertThat(diagnosticsOptions.getMode()).isEqualTo(Mode.ON);
    assertThat(diagnosticsOptions.getSkipSupport()).isEqualTo(SkipSupport.NEVER);
    assertThat(diagnosticsOptions.getParameters()).isEmpty();
  }

}