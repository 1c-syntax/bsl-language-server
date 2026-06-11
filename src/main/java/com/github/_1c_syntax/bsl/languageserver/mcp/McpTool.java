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

import tools.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Инструмент (tool) MCP-сервера.
 * <p>
 * Каждая реализация представляет собой отдельную операцию, доступную
 * AI-агенту по протоколу Model Context Protocol. Инструменты работают
 * поверх существующих провайдеров языкового сервера, переиспользуя
 * единый {@link com.github._1c_syntax.bsl.languageserver.context.ServerContext}.
 * <p>
 * Это прототип: реализован минимальный набор инструментов для проверки
 * концепции «MCP-режим поверх работающего движка анализа».
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
   * @return JSON Schema входных параметров инструмента.
   */
  Map<String, Object> inputSchema();

  /**
   * Выполнить инструмент.
   *
   * @param arguments Аргументы вызова (объект {@code arguments} из {@code tools/call}).
   * @return Результат, который будет сериализован в JSON и возвращён модели.
   */
  Object call(JsonNode arguments);
}
