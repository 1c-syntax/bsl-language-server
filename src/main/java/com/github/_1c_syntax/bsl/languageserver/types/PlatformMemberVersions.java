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
package com.github._1c_syntax.bsl.languageserver.types;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.support.CompatibilityMode;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * Версионная применимость платформенного члена к целевой версии платформы.
 * Отвечает на вопросы «устарел ли член» и «доступен ли член» относительно
 * целевого режима совместимости — общая логика для диагностик
 * ({@code DeprecatedMethodCall}, {@code UnavailableMemberCall}) и
 * автодополнения.
 * <p>
 * Сравнение версий ведётся относительно режима совместимости проекта (либо
 * явной настройки {@code v8platform.targetVersion}). Если режим не задан,
 * считается «самая свежая платформа», поэтому любое устаревание срабатывает,
 * а недоступность — никогда.
 */
@UtilityClass
public class PlatformMemberVersions {

  /**
   * Строка версии вида {@code 8.3.10} — минимум три числовых компонента через
   * {@code .} или {@code _} (этого достаточно для {@link CompatibilityMode}).
   * Двухкомпонентные ({@code 8.3}) и прочие — невалидны.
   */
  private static final Pattern VERSION_PATTERN = Pattern.compile("^\\d+[._]\\d+[._]\\d+");

  /**
   * Sentinel-значение поля {@code deprecatedSinceVersion} — «устарел всегда»,
   * безотносительно версии платформы. Используется для oscript-конвенции
   * (там нет версионирования, но есть API, помеченные устаревшими).
   */
  public static final String DEPRECATED_ALWAYS = "*";

  /**
   * Целевая версия платформы для сравнения. Приоритет: явная настройка
   * {@code v8platform.targetVersion} в конфиге LS → режим совместимости
   * конфигурации. Если режим совместимости не задан ({@code DontUse}),
   * {@link CompatibilityMode} трактует его как самую свежую платформу
   * (доминирует в {@code compareTo}), поэтому отдельной обработки не требуется.
   */
  public static CompatibilityMode targetCompatibilityMode(DocumentContext documentContext,
                                                          LanguageServerConfiguration configuration) {
    var explicit = parse(configuration.getV8PlatformOptions().getTargetVersion());
    if (explicit != null) {
      return explicit;
    }
    return documentContext.getServerContext().getConfiguration().getCompatibilityMode();
  }

  /**
   * Член устарел для целевой платформы: {@code target >= deprecatedSinceVersion}.
   * Sentinel {@link #DEPRECATED_ALWAYS} срабатывает всегда (oscript-конвенция).
   * Пустая или неразборчивая строка версии → {@code false}.
   */
  public static boolean firesDeprecated(String deprecatedSinceVersion, CompatibilityMode target) {
    if (DEPRECATED_ALWAYS.equals(deprecatedSinceVersion)) {
      return true;
    }
    var version = parse(deprecatedSinceVersion);
    return version != null && CompatibilityMode.compareTo(version, target) >= 0;
  }

  /**
   * Член недоступен в целевой платформе: {@code target < sinceVersion}.
   * Пустая или неразборчивая строка версии → {@code false}.
   */
  public static boolean firesUnavailable(String sinceVersion, CompatibilityMode target) {
    var version = parse(sinceVersion);
    return version != null && CompatibilityMode.compareTo(version, target) < 0;
  }

  /**
   * Парсит строку версии вида {@code 8.3.10} в {@link CompatibilityMode}.
   * Пустые, двухкомпонентные ({@code 8.3}) и иные неразборчивые строки →
   * {@code null} (проверку версии для такого члена пропускаем).
   */
  @Nullable
  private static CompatibilityMode parse(@Nullable String version) {
    if (version == null || !VERSION_PATTERN.matcher(version).find()) {
      return null;
    }
    return new CompatibilityMode(version);
  }
}
