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

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

/**
 * Контроллер для загрузки ResourceBundle в кодировке UTF-8.
 * <p>
 * Переопределяет стандартное поведение {@link ResourceBundle.Control}
 * для корректной загрузки properties-файлов в UTF-8.
 */
public class UTF8Control extends ResourceBundle.Control {
  @Override
  public @Nullable ResourceBundle newBundle(String baseName,
                                            Locale locale,
                                            String format,
                                            ClassLoader loader,
                                            boolean reload) throws IOException {
    // The below is a copy of the default implementation.
    var bundleName = toBundleName(baseName, locale);
    var resourceName = toResourceName(bundleName, "properties");
    ResourceBundle bundle = null;
    InputStream stream = null;
    if (reload) {
      var url = loader.getResource(resourceName);
      if (url != null) {
        var connection = url.openConnection();
        if (connection != null) {
          connection.setUseCaches(false);
          stream = connection.getInputStream();
        }
      }
    } else {
      stream = loader.getResourceAsStream(resourceName);
    }

    if (stream != null) {
      try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
        // Only this line is changed to make it to read properties files as UTF-8.
        bundle = new PropertyResourceBundle(reader);
      } finally {
        stream.close();
      }
    }
    return bundle;
  }
}
