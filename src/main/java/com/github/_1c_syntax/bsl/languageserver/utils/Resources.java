/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2022
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

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import lombok.experimental.UtilityClass;

import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Вспомогательный класс для оптимизированного чтения ресурсов прикладных классов с учетом {@link Language}.
 */
@UtilityClass
public class Resources {

  /**
   * @param language Язык получения ресурсной строки.
   * @param clazz    Класс, ресурсы которого необходимо прочитать.
   * @param key      Ключ из {@link ResourceBundle}.
   * @return Содержимое ресурса.
   */
  public String getResourceString(Language language, Class<?> clazz, String key) {
    String languageCode = language.getLanguageCode();
    var locale = Locale.forLanguageTag(languageCode);
    return ResourceBundle.getBundle(clazz.getName(), locale, new UTF8Control()).getString(key).intern();
  }

  /**
   * @param locale Язык получения ресурсной строки.
   * @param clazz  Класс, ресурсы которого необходимо прочитать.
   * @param key    Ключ из {@link ResourceBundle}.
   * @return Содержимое ресурса.
   */
  public String getResourceString(Locale locale, Class<?> clazz, String key) {
    return ResourceBundle.getBundle(clazz.getName(), locale, new UTF8Control()).getString(key).intern();
  }

  /**
   * @param language Язык получения ресурсной строки.
   * @param clazz    Класс, ресурсы которого необходимо прочитать.
   * @param key      Ключ из {@link ResourceBundle}.
   * @param args     Аргументы для форматирования ресурсной строки.
   * @return Содержимое ресурса.
   */
  public String getResourceString(Language language, Class<?> clazz, String key, Object... args) {
    return String.format(getResourceString(language, clazz, key), args).intern();
  }

  /**
   * @param locale Язык получения ресурсной строки.
   * @param clazz  Класс, ресурсы которого необходимо прочитать.
   * @param key    Ключ из {@link ResourceBundle}.
   * @param args   Аргументы для форматирования ресурсной строки.
   * @return Содержимое ресурса.
   */
  public String getResourceString(Locale locale, Class<?> clazz, String key, Object... args) {
    return String.format(getResourceString(locale, clazz, key), args).intern();
  }
}
