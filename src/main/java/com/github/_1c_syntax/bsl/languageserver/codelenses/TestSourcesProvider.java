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
package com.github._1c_syntax.bsl.languageserver.codelenses;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.utils.Absolute;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Поставщик списка каталогов с тестовыми исходниками для линз запуска тестов.
 * <p>
 * Кэширование вынесено в отдельный бин, чтобы вызов кэшируемого метода
 * шёл через нормальный Spring AOP-прокси без self-injection в линзах
 * (паттерн {@code @Lazy self} ломает native-image на конфликте
 * pre-generated CGLIB-стабов с runtime AOP).
 */
@Component
@CacheConfig(cacheNames = "testSources")
@RequiredArgsConstructor
public class TestSourcesProvider {

  private final LanguageServerConfiguration configuration;

  /**
   * Получить список каталогов с тестами с учётом корня рабочей области.
   *
   * @param configurationRoot Корень конфигурации.
   * @return URI каталогов с тестами.
   */
  @Cacheable
  public Set<URI> getTestSources(@Nullable Path configurationRoot) {
    var configurationRootString = Optional.ofNullable(configurationRoot)
      .map(Path::toString)
      .orElse("");

    return configuration.getCodeLensOptions().getTestRunnerAdapterOptions().getTestSources()
      .stream()
      .map(testDir -> Path.of(configurationRootString, testDir))
      .map(path -> Absolute.path(path).toUri())
      .collect(Collectors.toSet());
  }

  /**
   * Сбросить кэш каталогов с тестами.
   */
  @CacheEvict(allEntries = true)
  public void evict() {
    // No-op. Сброс кеша выполняется аспектом @CacheEvict.
  }
}
