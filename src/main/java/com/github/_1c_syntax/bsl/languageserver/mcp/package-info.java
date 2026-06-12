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
/**
 * Прототип режима Model Context Protocol (MCP).
 * <p>
 * MCP-сервер поверх stdio поднимает автоконфигурация Spring AI (профиль {@code mcp});
 * инструменты объявлены аннотацией {@code @McpTool} и переиспользуют движок анализа и
 * провайдеры языкового сервера. Демонстрирует, что MCP — это ещё один транспорт над уже
 * отвязанным от LSP ядром (наряду с режимами {@code lsp}, {@code websocket}, {@code analyze}).
 */
@NullMarked
package com.github._1c_syntax.bsl.languageserver.mcp;

import org.jspecify.annotations.NullMarked;
