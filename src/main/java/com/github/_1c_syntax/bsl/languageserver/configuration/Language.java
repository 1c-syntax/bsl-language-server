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

import lombok.Getter;

import java.util.Locale;

/**
 * Язык для сообщений, ресурсов и прочих взаимодействий между
 * BSL Language Server и пользователем.
 */
@Getter
public enum Language {

  /**
   * Русский
   */
  RU("ru"),

  /**
   * Английский
   */
  EN("en");

  /**
   * Язык по умолчанию
   */
  public static final Language DEFAULT_LANGUAGE = RU;

  /**
   * Код языка в соответствии с {@link java.util.Locale#getLanguage()}.
   */
  private final String languageCode;

  /**
   * Локаль языка.
   */
  private final Locale locale;

  /**
   * @param languageCode Код языка в соответствии с {@link java.util.Locale#getLanguage()}
   */
  Language(String languageCode) {
    this.languageCode = languageCode;
    this.locale = Locale.forLanguageTag(languageCode);
  }

}
