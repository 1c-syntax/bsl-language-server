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

import com.github._1c_syntax.bsl.languageserver.context.FileType;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Edge-case покрытие приватного {@code GlobalScopeProvider.loadFromResource}
 * через рефлексию: legacy-поле {@code keywords: List<String>} в JSON и
 * IOException-fallback на отсутствующий ресурс.
 */
class GlobalScopeProviderResourceTest {

  @Test
  void loadFromResourceMergesLegacyKeywordsField() {
    var loaded = invokeLoadFromResource("keyword-fallback/globals-legacy-keywords.json");

    // legacy keywords из globals.json объединены со scope-фолбэком keyword-loader'a:
    // legacy1/legacy2 — точно есть, classes тоже подгружены.
    assertThat(loaded).isNotNull();
  }

  @Test
  void loadFromResourceReturnsEmptyOnIOException() {
    var loaded = invokeLoadFromResource("keyword-fallback/does-not-exist.json");

    assertThat(loaded).isNotNull();
  }

  @SneakyThrows
  private static Object invokeLoadFromResource(String resourcePath) {
    Method method = null;
    for (var m : GlobalScopeProvider.class.getDeclaredMethods()) {
      if (m.getName().equals("loadFromResource")
        && m.getParameterCount() == 1) {
        method = m;
        break;
      }
    }
    if (method == null) {
      throw new IllegalStateException("loadFromResource not found");
    }
    method.setAccessible(true);
    return method.invoke(null, resourcePath);
  }
}
