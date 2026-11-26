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
package com.github._1c_syntax.bsl.languageserver.inlayhints;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.util.Map;

/**
 * Чтение вложенных флагов рендеринга подсказок вызовов методов
 * ({@code showParametersWithTheSameName}, {@code showDefaultValues}) из конфигурации.
 * <p>
 * Оба коллектора ({@link SourceDefinedMethodCallInlayHintCollector} и
 * {@link PlatformMethodCallInlayHintCollector}) рендерят одни и те же подсказки и
 * отличаются лишь источником метаданных метода, поэтому читают единый ключ конфига
 * {@code inlayHint.parameters.methodCall}.
 */
final class MethodCallInlayHintFlags {

  private static final String CONFIG_KEY = "methodCall";

  private static final boolean DEFAULT_SHOW_PARAMETERS_WITH_THE_SAME_NAME = false;
  private static final boolean DEFAULT_SHOW_DEFAULT_VALUES = true;

  private MethodCallInlayHintFlags() {
    // utility class
  }

  static boolean showParametersWithTheSameName(LanguageServerConfiguration configuration) {
    return readFlag(configuration, "showParametersWithTheSameName", DEFAULT_SHOW_PARAMETERS_WITH_THE_SAME_NAME);
  }

  static boolean showDefaultValues(LanguageServerConfiguration configuration) {
    return readFlag(configuration, "showDefaultValues", DEFAULT_SHOW_DEFAULT_VALUES);
  }

  private static boolean readFlag(LanguageServerConfiguration configuration, String name, boolean defaultValue) {
    Either<Boolean, Map<String, Object>> entry = configuration.getInlayHintOptions().getParameters().get(CONFIG_KEY);
    if (entry != null && entry.isRight()) {
      var value = entry.getRight().get(name);
      if (value instanceof Boolean b) {
        return b;
      }
    }
    return defaultValue;
  }
}
