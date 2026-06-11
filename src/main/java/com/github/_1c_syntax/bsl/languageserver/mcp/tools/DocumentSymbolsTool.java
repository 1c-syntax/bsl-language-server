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
import com.github._1c_syntax.bsl.languageserver.mcp.McpDocumentReader;
import com.github._1c_syntax.bsl.languageserver.providers.DocumentSymbolProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MCP-инструмент: получить дерево символов документа.
 * <p>
 * Переиспользует {@link DocumentSymbolProvider} — тот же провайдер, что
 * отвечает на запрос {@code textDocument/documentSymbol} в LSP-режиме.
 */
@Component
@Profile("mcp")
@RequiredArgsConstructor
public class DocumentSymbolsTool {

  private final McpDocumentReader documentReader;
  private final DocumentSymbolProvider documentSymbolProvider;

  /**
   * Результат разбора символов файла.
   *
   * @param file Путь к файлу.
   * @param symbols Дерево символов.
   */
  public record Result(String file, List<SymbolDto> symbols) {
  }

  @McpTool(
    name = "document_symbols",
    description = "Return the symbol tree (regions, methods, variables) of a 1C/OneScript file.",
    // Output schema disabled: Spring AI generates a non-nullable schema that rejects null DTO
    // fields (here — nullable symbol detail). Known upstream bug, open as of 2.0.0-M6:
    // https://github.com/spring-projects/spring-ai/issues/4825
    // https://github.com/spring-projects/spring-ai/issues/4487
    generateOutputSchema = false)
  public Result documentSymbols(
    @McpToolParam(required = true, description = McpToolParams.FILE)
    String file
  ) {
    return documentReader.read(file, documentContext -> {
      var symbols = documentSymbolProvider.getDocumentSymbols(documentContext).stream()
        .map(SymbolDto::from)
        .toList();
      return new Result(file, symbols);
    });
  }
}
