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

  static final String FILE = "Path to the .bsl/.os file (absolute or relative to the working directory). "
    + "The file must lie inside a registered workspace — see the `list_workspaces` and "
    + "`register_workspace` tools.";
  static final String FILE_TYPE = "File language: `BSL` for 1C:Enterprise BSL or `OS` for OneScript.";
  static final String LINE = "Zero-based line number of the symbol.";
  static final String CHARACTER = "Zero-based character offset within the line.";
  static final String TYPE_NAME = "1C/BSL type name in Russian or English (e.g. `Массив` / `Array`).";
  static final String LANGUAGE = "Language for names and descriptions: `RU` (default) or `EN`.";
  static final String GLOBAL_MEMBER_NAME = "Global function, property or enum name in Russian or English "
    + "(e.g. `Сообщить` / `Message`, `Метаданные` / `Metadata`).";
  static final String GLOBAL_MEMBER_CATEGORIES = "Optional list of categories to include: `FUNCTION` "
    + "(global functions/procedures), `PROPERTY` (global properties) and/or `ENUM` (system enums). "
    + "When omitted or empty, all categories are searched.";
  static final String GLOBAL_MEMBER_QUERY = "Optional search query matched fuzzily against member names "
    + "(exact > prefix > substring > subsequence, case-insensitive, over both the Russian and English "
    + "spelling, e.g. `Сценар` / `Script`), like the editor's autocomplete. Results are ranked by "
    + "relevance. When omitted, every member in the selected categories is returned.";
  static final String ROOT = "Root of the workspace to scope the lookup to — one of the `root` values "
    + "returned by the `list_workspaces` tool. Required because the answer can differ between "
    + "workspaces: 1C configuration vs OneScript project, different configurations, different library "
    + "sets. For purely platform names any registered root will do. Do not guess this value: call "
    + "`list_workspaces` first and, if the project is not there yet, register it with "
    + "`register_workspace`.";
  static final String WORKSPACE_PATH = "Path to the workspace folder to register: the project root "
    + "directory, the same folder an editor opens and an LSP client sends as a workspace folder. It "
    + "holds the sources (`src/cf` of a 1C configuration, the OneScript sources) and, when present, "
    + "`.bsl-language-server.json` — which is only read from the workspace root, so pass that root and "
    + "not a sources subfolder. Must be a directory, not a single file. Accepts an absolute path, a "
    + "`file:` URI, or a relative path — a relative one is resolved against the server working "
    + "directory, which the client usually does not control, so prefer an absolute path.";
  static final String WORKSPACE_NAME = """
    Optional display name for the workspace, the same idea as the `name` of an LSP workspace folder. \
    Defaults to the name of the registered directory.""";
  static final String WORKSPACE_ROOT = """
    Root of the workspace to operate on, as returned by the `list_workspaces` tool.""";

  private McpToolParams() {
  }
}
