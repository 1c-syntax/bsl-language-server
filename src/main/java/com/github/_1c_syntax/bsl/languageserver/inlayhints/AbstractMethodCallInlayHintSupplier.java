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

import java.util.List;
import java.util.Map;

/**
 * Базовый класс для inlay-hint сапплаеров вызовов методов
 * ({@link SourceDefinedMethodCallInlayHintSupplier} и
 * {@link PlatformMethodCallInlayHintSupplier}).
 * <p>
 * Содержит общую логику чтения флагов {@code showParametersWithTheSameName}
 * и {@code showDefaultValues} из {@link LanguageServerConfiguration}: оба
 * сапплаера рендерят одни и те же подсказки (имя параметра рядом с
 * передаваемым значением) и отличаются лишь источником метаданных метода —
 * пользователь не должен управлять «одним и тем же» поведением двумя
 * флагами для разных типов методов. Поэтому оба читают единый ключ конфига
 * {@code inlayHint.parameters.methodCall}; для совместимости с конфигами,
 * написанными до объединения, читается также legacy-ключ
 * {@code sourceDefinedMethodCall}.
 */
public abstract class AbstractMethodCallInlayHintSupplier implements InlayHintSupplier<DefaultInlayHintData> {

  private static final List<String> CONFIG_KEYS = List.of(
    "methodCall",
    "sourceDefinedMethodCall"
  );

  private static final boolean DEFAULT_SHOW_PARAMETERS_WITH_THE_SAME_NAME = false;
  private static final boolean DEFAULT_SHOW_DEFAULT_VALUES = true;

  protected final LanguageServerConfiguration configuration;

  protected AbstractMethodCallInlayHintSupplier(LanguageServerConfiguration configuration) {
    this.configuration = configuration;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Подсказки имён параметров не откладывают построение полей на резолв, поэтому
   * используется дефолтный дата-класс {@link DefaultInlayHintData}.
   */
  @Override
  public Class<DefaultInlayHintData> getInlayHintDataClass() {
    return DefaultInlayHintData.class;
  }

  protected boolean showParametersWithTheSameName() {
    return readFlag("showParametersWithTheSameName", DEFAULT_SHOW_PARAMETERS_WITH_THE_SAME_NAME);
  }

  protected boolean showDefaultValues() {
    return readFlag("showDefaultValues", DEFAULT_SHOW_DEFAULT_VALUES);
  }

  private boolean readFlag(String name, boolean defaultValue) {
    var parameters = configuration.getInlayHintOptions().getParameters();
    for (var key : CONFIG_KEYS) {
      Either<Boolean, Map<String, Object>> entry = parameters.get(key);
      if (entry != null && entry.isRight()) {
        var value = entry.getRight().get(name);
        if (value instanceof Boolean b) {
          return b;
        }
      }
    }
    return defaultValue;
  }
}
