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

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;

/**
 * Фабрика для создания per-workspace конфигураций.
 * <p>
 * Создаёт {@link LanguageServerConfiguration} для каждого workspace с учётом
 * иерархии конфигурационных файлов:
 * <ol>
 *   <li>{@code <workspace>/.bsl-language-server.json} — приоритет</li>
 *   <li>{@code ~/.bsl-language-server.json} — fallback</li>
 *   <li>Дефолтные значения</li>
 * </ol>
 * <p>
 * Использует {@link ObjectProvider} для получения prototype beans от Spring,
 * что позволяет AOP аспектам работать корректно.
 */
@Component
@RequiredArgsConstructor
public class LanguageServerConfigurationFactory {

  @Value("${app.configuration.path:.bsl-language-server.json}")
  private String defaultConfigFileName;

  @Value("${app.globalConfiguration.path:${user.home}/.bsl-language-server.json}")
  private String globalConfigPath;

  private final ObjectProvider<LanguageServerConfiguration> configurationProvider;

  /**
   * Создать конфигурацию для workspace.
   *
   * @param workspaceRoot Корневой путь workspace
   * @return Новый экземпляр конфигурации для workspace
   */
  public LanguageServerConfiguration createConfiguration(Path workspaceRoot) {
    // Spring создаёт prototype bean — AOP аспекты работают
    var config = configurationProvider.getObject();

    // 1. Если задан полный путь к конфигурации (не просто имя файла)
    var configFile = new File(defaultConfigFileName);
    if (configFile.exists()) {
      config.update(configFile);
      return config;
    }

    // 2. Попробовать загрузить из workspace
    var workspaceConfig = workspaceRoot.resolve(defaultConfigFileName).toFile();
    if (workspaceConfig.exists()) {
      config.update(workspaceConfig);
      return config;
    }

    // 3. Fallback на глобальную конфигурацию
    var globalConfig = new File(globalConfigPath);
    if (globalConfig.exists()) {
      config.update(globalConfig);
    }

    // 4. Иначе — дефолтные значения (уже в config)
    return config;
  }
}
