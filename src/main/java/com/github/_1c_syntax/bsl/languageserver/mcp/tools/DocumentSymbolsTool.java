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

import com.github._1c_syntax.bsl.languageserver.mcp.McpDtos.SymbolDto;
import com.github._1c_syntax.bsl.languageserver.mcp.McpTool;
import com.github._1c_syntax.bsl.languageserver.mcp.McpToolArguments;
import com.github._1c_syntax.bsl.languageserver.mcp.McpWorkspace;
import com.github._1c_syntax.bsl.languageserver.providers.DocumentSymbolProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * MCP-инструмент: получить дерево символов документа.
 * <p>
 * Переиспользует {@link DocumentSymbolProvider} — тот же провайдер, что
 * отвечает на запрос {@code textDocument/documentSymbol} в LSP-режиме.
 */
@Component
@RequiredArgsConstructor
public class DocumentSymbolsTool implements McpTool {

  private final McpWorkspace workspace;
  private final DocumentSymbolProvider documentSymbolProvider;

  @Override
  public String name() {
    return "document_symbols";
  }

  @Override
  public String description() {
    return "Return the symbol tree (regions, methods, variables) of a 1C/OneScript file.";
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
  public Object call(Map<String, Object> arguments) {
    var file = McpToolArguments.requireString(arguments, "file");

    var documentContext = workspace.resolveDocument(file);
    var symbols = documentSymbolProvider.getDocumentSymbols(documentContext).stream()
      .map(SymbolDto::from)
      .toList();

    return Map.of(
      "file", file,
      "symbols", symbols
    );
  }
}
