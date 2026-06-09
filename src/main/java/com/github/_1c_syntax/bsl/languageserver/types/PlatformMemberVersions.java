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
   * Строка версии вида {@code 8.3.10} либо {@code 8.2} — минимум два числовых
   * компонента через {@code .} или {@code _}. Двухкомпонентные значения
   * приходят из СП у API, депрекированных ещё в эпоху платформ 8.0/8.1/8.2
   * (например, {@code ТабличныйДокумент.ИмяПараметровПечати} — устарел с 8.2),
   * — их нормализуем до трёхкомпонентного вида в {@link #normalize}.
   */
  private static final Pattern VERSION_PATTERN = Pattern.compile("^\\d+[._]\\d+([._]\\d+)?");

  /** Sentinel «последний патч семейства» — синхронизирован с MAX_VERSION в CompatibilityMode. */
  private static final int MAX_PATCH = 99;

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
    // Для target-настройки берём «начало семейства» — она задаётся пользователем
    // явно (например, «8.3.10») и обычно уже трёхкомпонентна; для двухкомпонентной
    // консервативная интерпретация — самая старая 8.x.0.
    var explicit = parseSinceVersion(configuration.getV8PlatformOptions().getTargetVersion());
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
    var version = parseDeprecatedSinceVersion(deprecatedSinceVersion);
    return version != null && CompatibilityMode.compareTo(version, target) >= 0;
  }

  /**
   * Член недоступен в целевой платформе: {@code target < sinceVersion}.
   * Пустая или неразборчивая строка версии → {@code false}.
   */
  public static boolean firesUnavailable(String sinceVersion, CompatibilityMode target) {
    var version = parseSinceVersion(sinceVersion);
    return version != null && CompatibilityMode.compareTo(version, target) < 0;
  }

  /**
   * Парсит {@code sinceVersion} вида {@code 8.3.10} либо {@code 8.2}.
   * Двухкомпонентное значение «доступно с 8.2» означает «доступно с первого
   * патча 8.2», т.е. {@code 8.2.0}.
   */
  @Nullable
  private static CompatibilityMode parseSinceVersion(@Nullable String version) {
    return normalize(version, 0);
  }

  /**
   * Парсит {@code deprecatedSinceVersion} вида {@code 8.3.10} либо {@code 8.2}.
   * Двухкомпонентное значение «устарел с 8.2» означает «устарел к моменту
   * последнего патча 8.2», т.е. {@code 8.2.99} — иначе диагностика «устарел
   * с 8.2» начнёт срабатывать на промежуточных версиях семейства 8.2 раньше
   * времени.
   */
  @Nullable
  private static CompatibilityMode parseDeprecatedSinceVersion(@Nullable String version) {
    return normalize(version, MAX_PATCH);
  }

  /**
   * Достраивает строку версии до трёхкомпонентной — {@link CompatibilityMode}
   * требует минимум три компонента. Подставляемый патч {@code defaultPatch}
   * задаётся вызывающей стороной (для {@code sinceVersion} — начало семейства,
   * для {@code deprecatedSinceVersion} — конец семейства).
   */
  @Nullable
  private static CompatibilityMode normalize(@Nullable String version, int defaultPatch) {
    if (version == null) {
      return null;
    }
    var matcher = VERSION_PATTERN.matcher(version);
    if (!matcher.find()) {
      return null;
    }
    var matched = matcher.group();
    if (matcher.group(1) == null) {
      matched = matched + "." + defaultPatch;
    }
    return new CompatibilityMode(matched);
  }
}
