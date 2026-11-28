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

import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

/**
 * Разбор полезной нагрузки LSP-объектов ({@code data} линз, подсказок, элементов автодополнения,
 * аргументов команд).
 * <p>
 * Внутри сервера нагрузка — уже объект своего класса; после передачи клиенту и обратно LSP4J
 * отдаёт её как JSON-дерево ({@code JsonElement}) или как {@link java.util.Map}.
 */
@UtilityClass
public class LspPayloads {

  /**
   * Прочитать нагрузку в объект указанного класса.
   *
   * @param mapper  JSON-маппер с зарегистрированными подтипами нагрузок
   * @param payload нагрузка из LSP-объекта
   * @param type    класс нагрузки
   * @param <T>     тип нагрузки
   * @return нагрузка; {@code null}, если её нет
   */
  public static <T> @Nullable T read(JsonMapper mapper, @Nullable Object payload, Class<T> type) {
    if (payload == null) {
      return null;
    }
    if (type.isInstance(payload)) {
      return type.cast(payload);
    }
    if (payload instanceof Map<?, ?>) {
      return mapper.convertValue(payload, type);
    }
    return mapper.readValue(payload.toString(), type);
  }
}
