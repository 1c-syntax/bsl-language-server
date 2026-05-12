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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github._1c_syntax.bsl.languageserver.configuration.capabilities.CapabilitiesOptions;
import com.github._1c_syntax.bsl.languageserver.configuration.codelens.CodeLensOptions;
import com.github._1c_syntax.bsl.languageserver.configuration.diagnostics.DiagnosticsOptions;
import com.github._1c_syntax.bsl.languageserver.configuration.documentlink.DocumentLinkOptions;
import com.github._1c_syntax.bsl.languageserver.configuration.events.LanguageServerConfigurationChangedEvent;
import com.github._1c_syntax.bsl.languageserver.configuration.formating.FormattingOptions;
import com.github._1c_syntax.bsl.languageserver.configuration.inlayhints.InlayHintOptions;
import com.github._1c_syntax.bsl.languageserver.configuration.references.ReferencesOptions;
import com.github._1c_syntax.bsl.languageserver.configuration.semantictokens.SemanticTokensOptions;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.utils.Absolute;
import jakarta.annotation.PostConstruct;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.PropertyUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import static tools.jackson.databind.MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS;

/**
 * Per-workspace конфигурация BSL Language Server.
 * <p>
 * Содержит настройки, специфичные для конкретного workspace.
 * Создаётся lazy при первом обращении к workspace-scoped proxy.
 * <p>
 * Глобальные настройки (language, sendErrors, traceLog) находятся в {@link GlobalLanguageServerConfiguration}.
 */
@Data
@AllArgsConstructor(onConstructor_ = {@JsonCreator(mode = JsonCreator.Mode.DISABLED)})
@NoArgsConstructor
@Slf4j
@JsonIgnoreProperties(ignoreUnknown = true)
@Component
@Scope(value = "workspace", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class LanguageServerConfiguration {

  /**
   * Язык интерфейса для сообщений и документации в этом workspace.
   */
  private Language language = Language.DEFAULT_LANGUAGE;

  @JsonProperty("diagnostics")
  @Setter(value = AccessLevel.NONE)
  private DiagnosticsOptions diagnosticsOptions = new DiagnosticsOptions();

  @JsonProperty("codeLens")
  @Setter(value = AccessLevel.NONE)
  private CodeLensOptions codeLensOptions = new CodeLensOptions();

  @JsonProperty("documentLink")
  @Setter(value = AccessLevel.NONE)
  private DocumentLinkOptions documentLinkOptions = new DocumentLinkOptions();

  @JsonProperty("inlayHint")
  @Setter(value = AccessLevel.NONE)
  private InlayHintOptions inlayHintOptions = new InlayHintOptions();

  @JsonProperty("capabilities")
  @Setter(value = AccessLevel.NONE)
  private CapabilitiesOptions capabilities = new CapabilitiesOptions();

  @JsonProperty("formatting")
  @Setter(value = AccessLevel.NONE)
  private FormattingOptions formattingOptions = new FormattingOptions();

  @JsonProperty("references")
  @Setter(value = AccessLevel.NONE)
  private ReferencesOptions referencesOptions = new ReferencesOptions();

  @JsonProperty("semanticTokens")
  @Setter(value = AccessLevel.NONE)
  private SemanticTokensOptions semanticTokensOptions = new SemanticTokensOptions();

  private String siteRoot = "https://1c-syntax.github.io/bsl-language-server";
  private boolean useDevSite;

  @Nullable
  private Path configurationRoot;

  @JsonIgnore
  @Setter(value = AccessLevel.NONE)
  @Nullable
  private File configurationFile;

  @JsonIgnore
  @Value("${app.configuration.path:.bsl-language-server.json}")
  @Getter(AccessLevel.NONE)
  @Setter(value = AccessLevel.NONE)
  private String defaultConfigFileName = ".bsl-language-server.json";

  @JsonIgnore
  @Value("${app.globalConfiguration.path:${user.home}/.bsl-language-server.json}")
  @Getter(AccessLevel.NONE)
  @Setter(value = AccessLevel.NONE)
  private String globalConfigPath = System.getProperty("user.home") + "/.bsl-language-server.json";

  /**
   * Инициализация конфигурации при создании workspace-scoped бина.
   * Ищет конфиг-файл в workspace root, затем глобальный.
   */
  @PostConstruct
  void init() {
    var workspaceUri = WorkspaceContextHolder.get();
    if (workspaceUri == null) {
      return;
    }

    Path workspaceRoot;
    try {
      workspaceRoot = Absolute.path(workspaceUri);
    } catch (RuntimeException e) {
      LOGGER.debug("Cannot resolve workspace path from URI: {}", workspaceUri, e);
      return;
    }

    // During @PostConstruct, use loadConfigurationFile() directly instead of update()
    // to avoid firing LanguageServerConfigurationChangedEvent via AOP.
    // This prevents circular dependency: LSC -> event -> DiagnosticInfos -> LSC (in creation).

    // 1. Прямой путь к конфигурации (из application.properties)
    var configFile = new File(defaultConfigFileName);
    if (configFile.isFile()) {
      loadConfigurationFile(configFile);
      this.configurationFile = configFile;
      return;
    }

    // 2. Конфиг в workspace
    var workspaceConfig = workspaceRoot.resolve(defaultConfigFileName).toFile();
    if (workspaceConfig.isFile()) {
      loadConfigurationFile(workspaceConfig);
      this.configurationFile = workspaceConfig;
      return;
    }

    // 3. Глобальная конфигурация
    var globalConfig = new File(globalConfigPath);
    if (globalConfig.isFile()) {
      loadConfigurationFile(globalConfig);
      this.configurationFile = globalConfig;
    }
  }

  /**
   * Обновить конфигурацию из файла.
   * <p>
   * Публикует {@link LanguageServerConfigurationChangedEvent} через AOP аспект.
   *
   * @param configurationFile Файл с конфигурацией
   */
  public void update(@Nullable File configurationFile) {
    if (configurationFile != null && configurationFile.exists() && !configurationFile.isDirectory()) {
      loadConfigurationFile(configurationFile);
      this.configurationFile = configurationFile;
    }
    // Событие публикуется через EventPublisherAspect
  }

  /**
   * Сбросить конфигурацию к значениям по умолчанию.
   */
  public void reset() {
    copyPropertiesFrom(new LanguageServerConfiguration());
    // Событие публикуется через EventPublisherAspect
  }

  /**
   * Получить корневой каталог конфигурации с учётом настроек.
   *
   * @param configuration Конфигурация language server
   * @param srcDir Директория исходных файлов
   * @return Корневой каталог для анализа, или {@code null} если конфигурация находится вне srcDir
   */
  public static @Nullable Path getCustomConfigurationRoot(LanguageServerConfiguration configuration, Path srcDir) {

    Path rootPath = null;
    var pathFromConfiguration = configuration.getConfigurationRoot();

    if (pathFromConfiguration == null) {
      rootPath = Absolute.path(srcDir);
    } else {
      // Проверим, что srcDir = pathFromConfiguration или что pathFromConfiguration находится внутри srcDir
      var absoluteSrcDir = Absolute.path(srcDir);
      var absolutePathFromConfiguration = Absolute.path(pathFromConfiguration);
      if (absolutePathFromConfiguration.startsWith(absoluteSrcDir)) {
        rootPath = absolutePathFromConfiguration;
      }
    }

    return rootPath;
  }

  private void loadConfigurationFile(File configurationFile) {
    var mapper = JsonMapper.builder()
      .enable(ACCEPT_CASE_INSENSITIVE_ENUMS)
      .build();

    LanguageServerConfiguration configuration;
    try (var inputStream = Files.newInputStream(configurationFile.toPath())) {
      configuration = mapper.readValue(inputStream, LanguageServerConfiguration.class);
    } catch (IOException e) {
      LOGGER.error("Can't deserialize configuration file", e);
      return;
    }

    copyPropertiesFrom(configuration);
  }

  @SneakyThrows
  private void copyPropertiesFrom(LanguageServerConfiguration configuration) {
    // todo: refactor
    PropertyUtils.copyProperties(this, configuration);
    PropertyUtils.copyProperties(this.inlayHintOptions, configuration.inlayHintOptions);
    PropertyUtils.copyProperties(this.capabilities, configuration.capabilities);
    PropertyUtils.copyProperties(this.codeLensOptions, configuration.codeLensOptions);
    PropertyUtils.copyProperties(this.diagnosticsOptions, configuration.diagnosticsOptions);
    PropertyUtils.copyProperties(this.documentLinkOptions, configuration.documentLinkOptions);
    PropertyUtils.copyProperties(this.formattingOptions, configuration.formattingOptions);
    PropertyUtils.copyProperties(this.referencesOptions, configuration.referencesOptions);
    PropertyUtils.copyProperties(this.semanticTokensOptions, configuration.semanticTokensOptions);
  }
}
