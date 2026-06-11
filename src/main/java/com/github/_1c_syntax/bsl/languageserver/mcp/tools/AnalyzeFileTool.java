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

import com.github._1c_syntax.bsl.languageserver.mcp.McpDtos.DiagnosticDto;
import com.github._1c_syntax.bsl.languageserver.mcp.McpTool;
import com.github._1c_syntax.bsl.languageserver.mcp.McpWorkspace;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * MCP-инструмент: вычислить диагностики для одного файла.
 * <p>
 * Переиспользует {@link com.github._1c_syntax.bsl.languageserver.providers.DiagnosticProvider}
 * через {@code DocumentContext.getDiagnostics()} — тот же путь, что и LSP-режим.
 */
@Component
@RequiredArgsConstructor
public class AnalyzeFileTool implements McpTool {

  private final McpWorkspace workspace;

  @Override
  public String name() {
    return "analyze_file";
  }

  @Override
  public String description() {
    return "Run BSL diagnostics for a single 1C/OneScript file and return the list of issues.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Map.of(
      "type", "object",
      "properties", Map.of(
        "file", Map.of(
          "type", "string",
          "description", "Path to the .bsl/.os file (absolute or relative to the working directory)."
        )
      ),
      "required", List.of("file")
    );
  }

  @Override
  public Object call(JsonNode arguments) {
    var file = arguments.path("file").asString();
    if (file.isBlank()) {
      throw new IllegalArgumentException("Parameter 'file' is required");
    }

    var documentContext = workspace.resolveDocument(file);
    var diagnostics = documentContext.getDiagnostics().stream()
      .map(DiagnosticDto::from)
      .toList();

    return Map.of(
      "file", file,
      "diagnosticsCount", diagnostics.size(),
      "diagnostics", diagnostics
    );
  }
}
