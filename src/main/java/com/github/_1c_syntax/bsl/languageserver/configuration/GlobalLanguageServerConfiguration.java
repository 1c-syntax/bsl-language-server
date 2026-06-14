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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github._1c_syntax.bsl.languageserver.configuration.capabilities.CapabilitiesOptions;
import com.github._1c_syntax.bsl.languageserver.configuration.capabilities.TextDocumentSyncCapabilityOptions;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static tools.jackson.databind.MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS;

/**
 * Глобальная конфигурация BSL Language Server.
 * <p>
 * Содержит настройки, которые применяются на уровне всего сервера и доступны
 * до инициализации workspace (например, traceLog, sendErrors, language).
 * <p>
 * Per-workspace настройки хранятся в {@link LanguageServerConfiguration} и доступны
 * через {@link com.github._1c_syntax.bsl.languageserver.context.ServerContext#getLanguageServerConfiguration()}.
 * <p>
 * События публикуются через AOP (см. {@link com.github._1c_syntax.bsl.languageserver.aop.EventPublisherAspect}).
 */
@Data
@Component
@Slf4j
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GlobalLanguageServerConfiguration {

  /**
   * Язык интерфейса для сообщений и документации.
   */
  private Language language = Language.DEFAULT_LANGUAGE;

  /**
   * Режим отправки ошибок в Sentry.
   */
  private SendErrorsMode sendErrors = SendErrorsMode.DEFAULT;

  /**
   * Файл для трассировки LSP-обмена.
   */
  @Nullable
  private File traceLog;

  /**
   * Настройки возможностей сервера LSP, передаваемые клиенту при инициализации
   * (например, стратегия синхронизации текстовых документов).
   */
  @Setter(AccessLevel.NONE)
  private CapabilitiesOptions capabilities = new CapabilitiesOptions();

  /**
   * Настройки поиска символов рабочей области ({@code workspace/symbol}),
   * в т.ч. режим синхронного fuzzy-поиска.
   */
  @Setter(AccessLevel.NONE)
  private WorkspaceSymbolOptions workspaceSymbol = new WorkspaceSymbolOptions();

  /**
   * Файл, из которого была загружена текущая конфигурация.
   */
  @JsonIgnore
  @Setter(AccessLevel.NONE)
  @Nullable
  private File configurationFile;

  @Value("${app.configuration.path:.bsl-language-server.json}")
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @JsonIgnore
  private String configurationFilePath;

  @Value("${app.globalConfiguration.path:${user.home}/.bsl-language-server.json}")
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @JsonIgnore
  private String globalConfigPath;

  /**
   * Инициализация конфигурации после полной загрузки Spring контекста.
   * <p>
   * Используется {@link ContextRefreshedEvent} вместо {@code @PostConstruct},
   * чтобы гарантировать что {@link com.github._1c_syntax.bsl.languageserver.aop.EventPublisherAspect}
   * уже инициализирован и может публиковать события.
   */
  @EventListener(ContextRefreshedEvent.class)
  void onApplicationReady() {
    var configFile = new File(configurationFilePath);
    if (configFile.exists()) {
      update(configFile);
      return;
    }
    update(new File(globalConfigPath));
  }

  /**
   * Обновить глобальную конфигурацию из файла.
   * <p>
   * Если файл существует, загружает из него только глобальные настройки (language, sendErrors, traceLog).
   * Событие публикуется через AOP.
   *
   * @param configurationFile Файл с конфигурацией
   */
  public void update(@Nullable File configurationFile) {
    if (configurationFile != null && configurationFile.exists() && !configurationFile.isDirectory()) {
      loadFromFile(configurationFile);
      this.configurationFile = configurationFile;
    }
    // Событие публикуется через EventPublisherAspect
  }

  /**
   * Сбросить глобальные настройки к значениям по умолчанию.
   * Событие публикуется через AOP.
   */
  public void reset() {
    this.language = Language.RU;
    this.sendErrors = SendErrorsMode.DEFAULT;
    this.traceLog = null;
    this.capabilities.getTextDocumentSync().setChange(TextDocumentSyncCapabilityOptions.DEFAULT_CHANGE);
    this.workspaceSymbol.setFuzzySearch(WorkspaceSymbolFuzzySearch.DEFAULT);
    this.configurationFile = null;
    // Событие публикуется через EventPublisherAspect
  }

  private void loadFromFile(File file) {
    var mapper = JsonMapper.builder()
      .enable(ACCEPT_CASE_INSENSITIVE_ENUMS)
      .build();

    try (var inputStream = Files.newInputStream(file.toPath())) {
      var loaded = mapper.readValue(inputStream, GlobalLanguageServerConfiguration.class);
      this.language = loaded.language;
      this.sendErrors = loaded.sendErrors;
      this.traceLog = loaded.traceLog;
      this.capabilities.getTextDocumentSync().setChange(loaded.capabilities.getTextDocumentSync().getChange());
      this.workspaceSymbol.setFuzzySearch(loaded.workspaceSymbol.getFuzzySearch());
    } catch (IOException e) {
      LOGGER.error("Can't deserialize global configuration file", e);
    }
  }
}
