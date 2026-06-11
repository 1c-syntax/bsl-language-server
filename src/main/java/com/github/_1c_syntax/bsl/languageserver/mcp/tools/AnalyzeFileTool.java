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
import com.github._1c_syntax.bsl.languageserver.mcp.McpDocumentReader;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MCP-инструмент: вычислить диагностики для одного файла.
 * <p>
 * Переиспользует {@code DocumentContext.getDiagnostics()} — тот же путь, что и LSP-режим.
 */
@Component
@Profile("mcp")
@RequiredArgsConstructor
public class AnalyzeFileTool {

  private final McpDocumentReader documentReader;

  /**
   * Результат анализа файла.
   *
   * @param file Путь к проанализированному файлу.
   * @param diagnosticsCount Количество найденных замечаний.
   * @param diagnostics Список замечаний.
   */
  public record Result(String file, int diagnosticsCount, List<DiagnosticDto> diagnostics) {
  }

  @McpTool(
    name = "analyze_file",
    description = "Run BSL diagnostics for a single 1C/OneScript file and return the list of issues.",
    // Output schema disabled: Spring AI generates a non-nullable schema that rejects null DTO
    // fields (here — nullable severity/code/source). Known upstream bug, open as of 2.0.0-M6:
    // https://github.com/spring-projects/spring-ai/issues/4825
    // https://github.com/spring-projects/spring-ai/issues/4487
    generateOutputSchema = false)
  public Result analyzeFile(
    @McpToolParam(required = true,
      description = "Path to the .bsl/.os file (absolute or relative to the working directory).")
    String file
  ) {
    return documentReader.analyze(file, documentContext -> {
      var diagnostics = documentContext.getDiagnostics().stream()
        .map(DiagnosticDto::from)
        .toList();
      return new Result(file, diagnostics.size(), diagnostics);
    });
  }
}
