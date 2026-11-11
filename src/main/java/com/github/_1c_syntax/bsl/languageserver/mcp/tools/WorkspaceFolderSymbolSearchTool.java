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

import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.bsl.languageserver.mcp.McpWorkspaceFolders;
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

import java.net.URI;
import java.util.List;

/**
 * MCP-инструмент {@code workspace_folder_symbol_search}: поиск символов по рабочей папке —
 * та же ранжированная выдача, что у LSP-запроса {@code workspace/symbol}.
 * <p>
 * Клиенту MCP нечем принять потоковые чанки {@code $/progress}, поэтому ответ собирается целиком:
 * быстрая выдача индекса и, по умолчанию, fuzzy-хвост. Индекс символов общий на все рабочие папки,
 * как и сам {@code workspace/symbol}, поэтому выдача дополнительно сужается до символов запрошенной
 * папки: без этого символы другой папки и попадали бы в ответ, и съедали бы {@code limit}.
 */
@Component
@Profile("mcp")
@RequiredArgsConstructor
public class WorkspaceFolderSymbolSearchTool {

  private static final int DEFAULT_LIMIT = 200;
  private static final CancelChecker NEVER_CANCELLED = () -> {
  };

  private final McpWorkspaceResolver workspaceResolver;
  private final ServerContextProvider serverContextProvider;
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

  /**
   * Поиск символов запрошенной рабочей папки по имени.
   * <p>
   * Сначала идут точные совпадения, префикс имени, camel-hump и начало слова внутри имени; при
   * {@code fuzzy} (по умолчанию) за ними следуют подстрока внутри слова и подпоследовательность.
   * Ранжирование то же, что у {@code workspace/symbol}; ответ ограничен {@code limit} символами
   * (по умолчанию 200), признак {@code truncated} сообщает, что найдено больше.
   * Символы других зарегистрированных папок в ответ не попадают.
   *
   * @param query Строка поиска.
   * @param workspaceFolder Рабочая папка (URI или путь из {@code list_workspace_folders}).
   * @param fuzzy Дописывать ли fuzzy-хвост; {@code null} равносильно {@code true}.
   * @param limit Максимум символов в ответе; {@code null} равносильно значению по умолчанию.
   * @return Найденные символы по убыванию релевантности.
   * @throws IllegalArgumentException Если {@code limit} не положительный или папка не зарегистрирована.
   */
  @McpTool(
    name = "workspace_folder_symbol_search",
    description = "Search methods and module-level variables of a registered workspace folder by name (regions are "
      + "not indexed): exact, prefix, camel-hump and word-start matches first, then (with `fuzzy`) "
      + "substring-inside-word and subsequence ones, ranked like the editor's workspace/symbol request. "
      + "Only symbols of the requested folder are returned.",
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
    @McpToolParam(required = true, description = McpToolParams.FOLDER_SYMBOL_QUERY)
    String query,
    @McpToolParam(required = true, description = McpToolParams.WORKSPACE_FOLDER)
    String workspaceFolder,
    @McpToolParam(required = false, description = McpToolParams.FOLDER_SYMBOL_FUZZY)
    @Nullable Boolean fuzzy,
    @McpToolParam(required = false, description = McpToolParams.FOLDER_SYMBOL_LIMIT)
    @Nullable Integer limit
  ) {
    var includeFuzzyTail = fuzzy == null || fuzzy;
    var maxResults = limit == null ? DEFAULT_LIMIT : limit;
    if (maxResults <= 0) {
      throw new IllegalArgumentException("`limit` must be a positive number, got " + limit + ".");
    }

    var workspaceUri = workspaceResolver.resolveWorkspaceFolderUri(workspaceFolder);
    var serverContext = serverContextProvider.getAllContexts().get(workspaceUri);
    if (serverContext == null) {
      // Папку сняли с регистрации между разрешением и чтением карты контекстов.
      throw new IllegalArgumentException("Workspace folder `" + workspaceUri + "` is no longer registered. "
        + McpWorkspaceFolders.registrationHint(serverContextProvider.getAllContexts().keySet()));
    }
    // Символ сверх лимита показывает, что найдено больше, чем вошло в ответ.
    var requested = maxResults == Integer.MAX_VALUE ? maxResults : maxResults + 1;
    try (var ignored = WorkspaceContextHolder.forUri(workspaceUri)) {
      var found = symbolProvider.searchSymbols(
        query.trim(),
        documentUri -> belongsTo(serverContext, documentUri),
        includeFuzzyTail,
        requested,
        NEVER_CANCELLED
      );
      var symbols = found.stream()
        .limit(maxResults)
        .map(WorkspaceSymbolDto::from)
        .toList();
      return new Result(query, symbols.size(), found.size() > symbols.size(), symbols);
    }
  }

  // Членство проверяется по карте документов самой папки: у вложенных папок один документ входит
  // в оба контекста, а сопоставление по префиксу пути выбрало бы из них произвольный.
  private static boolean belongsTo(ServerContext serverContext, URI documentUri) {
    return serverContext.getDocumentNoLock(documentUri) != null;
  }
}
