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

import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.bsl.languageserver.mcp.McpWorkspaceResolver;
import com.github._1c_syntax.bsl.languageserver.mcp.dto.WorkspaceSymbolDto;
import com.github._1c_syntax.bsl.languageserver.providers.SymbolProvider;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MCP-инструмент {@code workspace_symbol_search}: поиск символов по рабочей папке —
 * та же ранжированная выдача, что у LSP-запроса {@code workspace/symbol}.
 * <p>
 * Клиенту MCP нечем принять потоковые чанки {@code $/progress}, поэтому ответ собирается целиком:
 * быстрая выдача индекса и, по умолчанию, fuzzy-хвост.
 */
@Component
@Profile("mcp")
@RequiredArgsConstructor
public class WorkspaceSymbolSearchTool {

  private static final int DEFAULT_LIMIT = 200;
  private static final CancelChecker NEVER_CANCELLED = () -> {
  };

  private final McpWorkspaceResolver workspaceResolver;
  private final SymbolProvider symbolProvider;

  /**
   * Результат поиска.
   *
   * @param query Строка поиска.
   * @param symbolsCount Количество символов в ответе.
   * @param truncated {@code true}, если найдено больше, чем вошло в ответ по {@code limit}.
   * @param symbols Найденные символы по убыванию релевантности.
   */
  public record Result(String query, int symbolsCount, boolean truncated, List<WorkspaceSymbolDto> symbols) {
  }

  @McpTool(
    name = "workspace_symbol_search",
    description = "Search methods, variables and regions of a registered workspace folder by name: prefix and "
      + "substring matches first, then fuzzy ones (subsequence), ranked like the editor's workspace/symbol request.",
    // Output schema disabled: Spring AI generates a non-nullable schema that rejects null DTO
    // fields (here — nullable containerName). Known upstream bug, open as of 2.0.0-M6:
    // https://github.com/spring-projects/spring-ai/issues/4825
    // https://github.com/spring-projects/spring-ai/issues/4487
    generateOutputSchema = false,
    // Read-only: only inspects 1C/OneScript code, never mutates anything. Hint clients so the
    // tool is not treated as destructive.
    annotations = @McpTool.McpAnnotations(
      readOnlyHint = true,
      destructiveHint = false,
      idempotentHint = true,
      openWorldHint = false))
  public Result search(
    @McpToolParam(required = true, description = McpToolParams.WORKSPACE_SYMBOL_QUERY)
    String query,
    @McpToolParam(required = true, description = McpToolParams.WORKSPACE_FOLDER)
    String workspaceFolder,
    @McpToolParam(required = false, description = McpToolParams.WORKSPACE_SYMBOL_FUZZY)
    @Nullable Boolean fuzzy,
    @McpToolParam(required = false, description = McpToolParams.LIMIT)
    @Nullable Integer limit
  ) {
    var includeFuzzyTail = fuzzy == null || fuzzy;
    var maxResults = limit == null ? DEFAULT_LIMIT : limit;
    if (maxResults <= 0) {
      throw new IllegalArgumentException("`limit` must be a positive number, got " + limit + ".");
    }

    var workspaceUri = workspaceResolver.resolveWorkspaceFolderUri(workspaceFolder);
    try (var ignored = WorkspaceContextHolder.forUri(workspaceUri)) {
      var found = symbolProvider.searchSymbols(query.trim(), includeFuzzyTail, NEVER_CANCELLED);
      var symbols = found.stream()
        .limit(maxResults)
        .map(WorkspaceSymbolDto::from)
        .toList();
      return new Result(query, symbols.size(), found.size() > symbols.size(), symbols);
    }
  }
}
