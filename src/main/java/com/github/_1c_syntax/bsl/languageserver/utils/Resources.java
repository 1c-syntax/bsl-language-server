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
package com.github._1c_syntax.bsl.languageserver.utils;

import com.github._1c_syntax.bsl.languageserver.configuration.GlobalLanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.utils.StringInterner;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Вспомогательный класс для оптимизированного чтения ресурсов прикладных классов с учетом {@link Language}.
 */
@Component
@Scope(value = "workspace", proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class Resources {

  private static final StringInterner stringInterner = new StringInterner();

  /**
   * Источник локали — глобальная конфигурация (а не workspace
   * {@code LanguageServerConfiguration}). Workspace LSC хранит дублирующее поле
   * {@code language}, которое заполняется только при init из конфиг-файла;
   * runtime-перезагрузка глобального файла обновляет только
   * {@link GlobalLanguageServerConfiguration}, и потребители workspace LSC
   * (Resources в том числе) видели stale-значение — hover/inlay/диагностики
   * не реагировали на смену языка без перезапуска workspace.
   */
  private final GlobalLanguageServerConfiguration configuration;

  /**
   * @return Текущая локаль LS. Доступ для потребителей, которым нужно передать
   * её в API, принимающее {@link Language} как параметр (например,
   * {@code TypeRegistry.getForEachDescription(ref, lang)}).
   */
  public Language getLanguage() {
    return configuration.getLanguage();
  }

  /**
   * @param clazz    Класс, ресурсы которого необходимо прочитать.
   * @param key      Ключ из {@link ResourceBundle}.
   * @return Содержимое ресурса.
   */
  public String getResourceString(Class<?> clazz, String key) {
    return getResourceString(configuration.getLanguage().getLocale(), clazz, key);
  }

  /**
   * @param clazz    Класс, ресурсы которого необходимо прочитать.
   * @param key      Ключ из {@link ResourceBundle}.
   * @param args     Аргументы для форматирования ресурсной строки.
   * @return Содержимое ресурса.
   */
  public String getResourceString(Class<?> clazz, String key, Object... args) {
    return getResourceString(configuration.getLanguage().getLocale(), clazz, key, args);
  }

  /**
   * @param language Язык получения ресурсной строки.
   * @param clazz    Класс, ресурсы которого необходимо прочитать.
   * @param key      Ключ из {@link ResourceBundle}.
   * @return Содержимое ресурса.
   */
  public static String getResourceString(Language language, Class<?> clazz, String key) {
    return getResourceString(language.getLocale(), clazz, key);
  }

  /**
   * @param locale Язык получения ресурсной строки.
   * @param clazz  Класс, ресурсы которого необходимо прочитать.
   * @param key    Ключ из {@link ResourceBundle}.
   * @return Содержимое ресурса.
   */
  public static String getResourceString(Locale locale, Class<?> clazz, String key) {
    var resourceString = ResourceBundle.getBundle(clazz.getName(), locale, new UTF8Control()).getString(key);
    return stringInterner.intern(resourceString);
  }

  /**
   * @param language Язык получения ресурсной строки.
   * @param clazz    Класс, ресурсы которого необходимо прочитать.
   * @param key      Ключ из {@link ResourceBundle}.
   * @param args     Аргументы для форматирования ресурсной строки.
   * @return Содержимое ресурса.
   */
  public static String getResourceString(Language language, Class<?> clazz, String key, Object... args) {
    return getResourceString(language.getLocale(), clazz, key, args);
  }

  /**
   * @param locale Язык получения ресурсной строки.
   * @param clazz  Класс, ресурсы которого необходимо прочитать.
   * @param key    Ключ из {@link ResourceBundle}.
   * @param args   Аргументы для форматирования ресурсной строки.
   * @return Содержимое ресурса.
   */
  public static String getResourceString(Locale locale, Class<?> clazz, String key, Object... args) {
    var resourceString = getResourceString(locale, clazz, key).formatted(args);
    return stringInterner.intern(resourceString);
  }
}
