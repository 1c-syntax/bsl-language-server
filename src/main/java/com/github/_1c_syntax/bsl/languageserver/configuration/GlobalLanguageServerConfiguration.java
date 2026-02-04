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
import com.github._1c_syntax.bsl.languageserver.configuration.events.GlobalLanguageServerConfigurationChangedEvent;
import jakarta.annotation.PostConstruct;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
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
 */
@Data
@Component
@Slf4j
@RequiredArgsConstructor
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
   * Файл, из которого была загружена текущая конфигурация.
   */
  @JsonIgnore
  @Setter(AccessLevel.NONE)
  @Nullable
  private File configurationFile;

  @Value("${app.globalConfiguration.path:${user.home}/.bsl-language-server.json}")
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @JsonIgnore
  private String globalConfigPath;

  @JsonIgnore
  private final ApplicationEventPublisher eventPublisher;

  @PostConstruct
  void init() {
    update(new File(globalConfigPath));
  }

  /**
   * Обновить глобальную конфигурацию из файла.
   * <p>
   * Если файл существует, загружает из него только глобальные настройки (language, sendErrors, traceLog).
   * Событие {@link GlobalLanguageServerConfigurationChangedEvent} публикуется всегда,
   * независимо от существования файла.
   *
   * @param configurationFile Файл с конфигурацией
   */
  public void update(@Nullable File configurationFile) {
    if (configurationFile != null && configurationFile.exists() && !configurationFile.isDirectory()) {
      loadFromFile(configurationFile);
      this.configurationFile = configurationFile;
    }
    eventPublisher.publishEvent(new GlobalLanguageServerConfigurationChangedEvent(this));
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
    } catch (IOException e) {
      LOGGER.error("Can't deserialize global configuration file", e);
    }
  }
}
