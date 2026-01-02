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

import com.github._1c_syntax.bsl.languageserver.configuration.codelens.CodeLensOptions;
import com.github._1c_syntax.bsl.languageserver.configuration.diagnostics.DiagnosticsOptions;
import com.github._1c_syntax.bsl.languageserver.configuration.diagnostics.Mode;
import com.github._1c_syntax.bsl.languageserver.configuration.diagnostics.SkipSupport;
import com.github._1c_syntax.bsl.languageserver.configuration.inlayhints.InlayHintOptions;
import com.github._1c_syntax.bsl.languageserver.configuration.semantictokens.SemanticTokensOptions;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static com.github._1c_syntax.bsl.languageserver.configuration.Language.DEFAULT_LANGUAGE;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class LanguageServerConfigurationTest {

  private static final String PATH_TO_CONFIGURATION_FILE = "./src/test/resources/.bsl-language-server.json";
  private static final String PATH_TO_EMPTY_CONFIGURATION_FILE = "./src/test/resources/.empty-bsl-language-server.json";
  private static final String PATH_TO_METADATA = "src/test/resources/metadata/designer";
  private static final String PATH_TO_PARTIAL_CONFIGURATION_FILE
    = "./src/test/resources/.partial-bsl-language-server.json";

  @Autowired
  private LanguageServerConfiguration configuration;

  @BeforeEach
  void startUp() throws IOException {
    try {
      Files.deleteIfExists(Paths.get("build/.trace.log"));
    } catch (FileSystemException e) {
      // no-op
    }
  }

  @Test
  void createFromFile() {

    // given
    File configurationFile = new File(PATH_TO_CONFIGURATION_FILE);

    // when
    configuration.update(configurationFile);

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
    assertThat(methodSize.getLeft()).isFalse();

    Path configurationRoot = configuration.getConfigurationRoot();
    assertThat(configurationRoot).isNotNull();

    assertThat(configuration.isUseDevSite()).isTrue();
    assertThat(configuration.getDiagnosticsOptions().isOrdinaryAppSupport()).isFalse();
    assertThat(configuration.getCapabilities().getTextDocumentSync().getChange()).isEqualTo(TextDocumentSyncKind.Full);

    var annotations = configuration.getCodeLensOptions().getTestRunnerAdapterOptions().getAnnotations();
    assertThat(annotations)
      .hasSize(2)
      .contains("Test", "Test2");
  }

  @Test
  void createDefault() {
    // then
    assertThat(configuration.getLanguage()).isEqualTo(Language.RU);
    assertThat(configuration.getDiagnosticsOptions().getParameters()).isEmpty();
    assertThat(configuration.getDiagnosticsOptions().isOrdinaryAppSupport()).isTrue();
    assertThat(configuration.getCapabilities().getTextDocumentSync().getChange())
      .isEqualTo(TextDocumentSyncKind.Incremental);
  }

  @Test
  void createFromEmptyFile() {

    // given
    File configurationFile = new File(PATH_TO_EMPTY_CONFIGURATION_FILE);

    // when
    configuration.update(configurationFile);

    // then
    DiagnosticsOptions diagnosticsOptions = configuration.getDiagnosticsOptions();
    Language language = configuration.getLanguage();
    Map<String, Either<Boolean, Map<String, Object>>> parameters = diagnosticsOptions.getParameters();

    assertThat(language).isEqualTo(DEFAULT_LANGUAGE);
    assertThat(parameters).isEmpty();

  }

  @Test
  void test_GetCustomConfigurationRoot() {

    Path path = Paths.get(PATH_TO_METADATA);
    Path configurationRoot = LanguageServerConfiguration.getCustomConfigurationRoot(configuration, path);
    assertThat(configurationRoot).isEqualTo(Absolute.path(path));

    File configurationFile = new File(PATH_TO_CONFIGURATION_FILE);
    configuration.update(configurationFile);
    configurationRoot = LanguageServerConfiguration.getCustomConfigurationRoot(configuration, path);
    assertThat(configurationRoot).isEqualTo(Absolute.path(path));

  }

  @Test
  void testPartialInitialization() {
    // given
    File configurationFile = new File(PATH_TO_PARTIAL_CONFIGURATION_FILE);
    configuration.update(configurationFile);

    // when
    CodeLensOptions codeLensOptions = configuration.getCodeLensOptions();
    DiagnosticsOptions diagnosticsOptions = configuration.getDiagnosticsOptions();
    InlayHintOptions inlayHintOptions = configuration.getInlayHintOptions();
    SemanticTokensOptions semanticTokensOptions = configuration.getSemanticTokensOptions();

    // then
    assertThat(codeLensOptions.getParameters().get("cognitiveComplexity")).isNull();
    assertThat(codeLensOptions.getParameters()).containsEntry("cyclomaticComplexity", Either.forLeft(false));

    assertThat(configuration.getLanguage()).isEqualTo(DEFAULT_LANGUAGE);

    assertThat(diagnosticsOptions.getMode()).isEqualTo(Mode.ON);
    assertThat(diagnosticsOptions.getSkipSupport()).isEqualTo(SkipSupport.NEVER);
    assertThat(diagnosticsOptions.getParameters()).isEmpty();

    assertThat(inlayHintOptions.getParameters())
      .containsEntry("sourceDefinedMethodCall", Either.forRight(Map.of("showParametersWithTheSameName", true)));

    assertThat(semanticTokensOptions.getStrTemplateMethods())
      .hasSize(2)
      .contains("CustomModule.CustomMethod", "CustomLocalMethod");
    assertThat(semanticTokensOptions.getParsedStrTemplateMethods().localMethods())
      .contains("customlocalmethod");
    assertThat(semanticTokensOptions.getParsedStrTemplateMethods().moduleMethodPairs())
      .containsKey("custommodule");

  }

}