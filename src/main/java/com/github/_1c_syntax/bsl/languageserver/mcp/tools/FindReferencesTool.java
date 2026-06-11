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

import com.github._1c_syntax.bsl.languageserver.mcp.McpDtos.LocationDto;
import com.github._1c_syntax.bsl.languageserver.mcp.McpTool;
import com.github._1c_syntax.bsl.languageserver.mcp.McpToolArguments;
import com.github._1c_syntax.bsl.languageserver.mcp.McpWorkspace;
import com.github._1c_syntax.bsl.languageserver.providers.ReferencesProvider;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.ReferenceContext;
import org.eclipse.lsp4j.ReferenceParams;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * MCP-инструмент: найти все ссылки на символ в позиции курсора.
 * <p>
 * Переиспользует {@link ReferencesProvider} (обработчик {@code textDocument/references}).
 * Корректность кросс-файловых ссылок обеспечивается тем, что контекст сервера
 * проиндексирован на старте MCP-режима.
 */
@Component
@RequiredArgsConstructor
public class FindReferencesTool implements McpTool {

  private final McpWorkspace workspace;
  private final ReferencesProvider referencesProvider;

  @Override
  public String name() {
    return "find_references";
  }

  @Override
  public String description() {
    return "Find all references to the symbol located at the given zero-based position in a file.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Map.of(
      "type", "object",
      "properties", Map.of(
        "file", Map.of(
          "type", "string",
          "description", "Path to the .bsl/.os file (absolute or relative to the working directory)."
        ),
        "line", Map.of(
          "type", "integer",
          "description", "Zero-based line number of the symbol."
        ),
        "character", Map.of(
          "type", "integer",
          "description", "Zero-based character offset within the line."
        )
      ),
      "required", List.of("file", "line", "character")
    );
  }

  @Override
  public Object call(Map<String, Object> arguments) {
    var file = McpToolArguments.requireString(arguments, "file");
    var line = McpToolArguments.requireInt(arguments, "line");
    var character = McpToolArguments.requireInt(arguments, "character");

    var documentContext = workspace.resolveDocument(file);

    var params = new ReferenceParams();
    params.setPosition(new Position(line, character));
    params.setContext(new ReferenceContext(true));

    var references = referencesProvider.getReferences(documentContext, params).stream()
      .map(LocationDto::from)
      .toList();

    return Map.of(
      "file", file,
      "referencesCount", references.size(),
      "references", references
    );
  }
}
