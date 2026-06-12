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
package com.github._1c_syntax.bsl.languageserver.mcp.tools;

/**
 * Общие описания параметров MCP-инструментов (используются в аннотациях {@code @McpToolParam}).
 */
final class McpToolParams {

  static final String FILE = "Path to the .bsl/.os file (absolute or relative to the working directory).";
  static final String FILE_TYPE = "File language: `bsl` for 1C:Enterprise BSL (default) or `os` for OneScript.";
  static final String LINE = "Zero-based line number of the symbol.";
  static final String CHARACTER = "Zero-based character offset within the line.";
  static final String TYPE_NAME = "1C/BSL type name in Russian or English (e.g. `Массив` / `Array`).";

  private McpToolParams() {
  }
}
