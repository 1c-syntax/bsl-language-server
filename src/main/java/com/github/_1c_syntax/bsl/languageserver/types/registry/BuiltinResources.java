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

import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;

/**
 * Доступ к встроенным JSON-ресурсам реестра типов ({@code builtin-*.json}).
 * <p>
 * Ресурсы лежат в jar-е BSL LS, поэтому и искать их нужно тем загрузчиком классов,
 * который этот jar загрузил. Нельзя полагаться на context class loader потока (а
 * именно его подставляет {@link ClassPathResource#ClassPathResource(String)} через
 * {@code ClassUtils.getDefaultClassLoader()}): когда BSL LS встроен в хост с
 * изолированными загрузчиками — например, в плагин SonarQube, — context class loader
 * принадлежит хосту и jar-а BSL LS не видит. Классы в этом случае грузятся нормально,
 * а ресурсы — нет, и глобальная область молча оказывается пустой.
 */
final class BuiltinResources {

  private BuiltinResources() {
  }

  /**
   * Открывает встроенный ресурс загрузчиком классов BSL LS.
   *
   * @param resourcePath путь к ресурсу на classpath
   * @return поток с содержимым ресурса
   * @throws IOException если ресурс отсутствует или нечитаем
   */
  static InputStream open(String resourcePath) throws IOException {
    return new ClassPathResource(resourcePath, BuiltinResources.class.getClassLoader())
      .getInputStream();
  }
}
