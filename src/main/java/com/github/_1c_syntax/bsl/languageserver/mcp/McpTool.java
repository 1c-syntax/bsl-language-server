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
package com.github._1c_syntax.bsl.languageserver.mcp;

import java.util.Map;

/**
 * Инструмент (tool) MCP-сервера.
 * <p>
 * Каждая реализация — тонкая обёртка над существующим провайдером языкового
 * сервера. Транспорт и протокол обеспечивает официальный MCP Java SDK
 * (см. {@link McpServerRunner}); инструменту достаточно описать себя и вернуть
 * сериализуемый результат.
 */
public interface McpTool {

  /**
   * @return Уникальное имя инструмента (используется в {@code tools/call}).
   */
  String name();

  /**
   * @return Человекочитаемое описание инструмента для модели.
   */
  String description();

  /**
   * @return JSON Schema входных параметров (как объект, будет сериализован SDK).
   */
  Map<String, Object> inputSchema();

  /**
   * Выполнить инструмент.
   *
   * @param arguments Аргументы вызова (поле {@code arguments} запроса {@code tools/call}).
   * @return Результат, который будет сериализован в JSON и возвращён модели.
   */
  Object call(Map<String, Object> arguments);
}
