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

import com.github._1c_syntax.bsl.context.api.ContextProvider;
import com.github._1c_syntax.bsl.languageserver.configuration.platform.V8PlatformOptions;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.utils.Lazy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Workspace-scoped lazy-кэш {@link ContextProvider} от {@code bsl-context}.
 * Парсинг HBK-справки занимает порядка двух секунд и выполняется один раз
 * за время жизни scope; результат шарится между потребителями
 * ({@link BslContextPlatformTypesProvider} для типов и {@link GlobalScopeProvider}
 * для глобального контекста, keyword'ов и системных перечислений).
 * <p>
 * Источник выбирается по {@link V8PlatformOptions#getBinPath()}: если задан —
 * берётся явный каталог {@code bin} платформы, иначе — автодетект самой
 * свежей установки на машине через {@code PlatformFinder}.
 * <p>
 * Если 1С не установлена или парсинг падает — {@link #get()} возвращает
 * {@link Optional#empty()}, потребители работают через JSON-fallback.
 * Повторных попыток инициализации не делается.
 */
@Slf4j
@Component
@WorkspaceScope
public class BslContextHolder {

  private final PlatformContextProviderFactory factory;
  private final Lazy<Optional<ContextProvider>> cached;

  public BslContextHolder(PlatformContextProviderFactory factory) {
    this.factory = factory;
    this.cached = new Lazy<>(this::load);
  }

  /**
   * Возвращает {@link ContextProvider}, инициализируя его при первом вызове.
   * Если источник недоступен — возвращает {@link Optional#empty()}; повторных
   * попыток не делает.
   */
  public Optional<ContextProvider> get() {
    return cached.getOrCompute();
  }

  private Optional<ContextProvider> load() {
    try {
      return factory.create();
    } catch (Exception e) {
      LOGGER.warn("Failed to load platform contexts from 1C syntax helper: {}", e.getMessage());
      return Optional.empty();
    }
  }
}
