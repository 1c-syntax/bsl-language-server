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
package com.github._1c_syntax.bsl.languageserver.types.registry;

import com.github._1c_syntax.bsl.context.PlatformContextGrabber;
import com.github._1c_syntax.bsl.context.api.ContextProvider;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

/**
 * Фабрика {@link ContextProvider} от {@code bsl-context}: создаёт парсер
 * синтакс-помощника установленной 1С и возвращает готовый провайдер. Подходящий
 * каталог {@code bin} платформы выбирается по
 * {@link com.github._1c_syntax.bsl.languageserver.configuration.platform.V8PlatformOptions#getBinPath()}
 * либо через {@code PlatformFinder.autoDetect()} (самая свежая установка на машине).
 * <p>
 * Stateless — кэш живёт в {@link BslContextHolder}. Здесь только factory,
 * чтобы инкапсулировать статическую фабрику {@link PlatformContextGrabber}
 * в Spring-компонент.
 */
@Slf4j
@Component
@WorkspaceScope
@RequiredArgsConstructor
public class PlatformContextProviderFactory {

  private final LanguageServerConfiguration configuration;

  /**
   * Включение загрузки платформенного контекста (1С синтакс-помощник).
   * По умолчанию включено; в тестах выключается через {@code application.properties}, чтобы
   * избежать дорогой автодетекции и парсинга HBK при подъёме контекста.
   */
  @Value("${app.platform-context.enabled:true}")
  private boolean platformContextEnabled;

  /**
   * Создаёт новый {@link ContextProvider}, прочитав HBK-файлы платформы.
   *
   * @return полностью инициализированный провайдер, либо {@link Optional#empty()},
   *   если платформа не найдена / HBK не открылся
   * @throws IOException если парсинг упал на IO-ошибке (для логирования в caller'е)
   */
  public Optional<ContextProvider> create() throws IOException {
    if (!platformContextEnabled) {
      LOGGER.debug("Platform context loader is disabled via app.platform-context.enabled=false");
      return Optional.empty();
    }
    var platformOptions = configuration.getV8PlatformOptions();
    if (!platformOptions.isEnabled()) {
      LOGGER.debug("Platform context loader is disabled via configuration");
      return Optional.empty();
    }
    var binPath = platformOptions.getBinPath();
    var grabber = binPath != null
      ? PlatformContextGrabber.fromPlatformBin(binPath)
      : PlatformContextGrabber.autoDetect();
    grabber.parse();
    var provider = grabber.getProvider();
    if (provider == null) {
      return Optional.empty();
    }
    LOGGER.info("Loaded {} platform contexts from 1C syntax helper",
      provider.getContexts().size());
    return Optional.of(provider);
  }
}
