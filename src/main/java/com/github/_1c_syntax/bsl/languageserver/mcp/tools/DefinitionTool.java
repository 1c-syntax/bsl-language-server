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

import com.github._1c_syntax.bsl.languageserver.mcp.McpDocumentReader;
import com.github._1c_syntax.bsl.languageserver.mcp.dto.DefinitionDto;
import com.github._1c_syntax.bsl.languageserver.providers.DefinitionProvider;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MCP-инструмент: перейти к определению символа под курсором.
 * <p>
 * Переиспользует {@link DefinitionProvider} (обработчик {@code textDocument/definition}).
 * Требует свежий AST (разрешение типозависимых ссылок), поэтому читает через
 * {@link McpDocumentReader#analyze}.
 */
@Component
@Profile("mcp")
@RequiredArgsConstructor
public class DefinitionTool {

  private final McpDocumentReader documentReader;
  private final DefinitionProvider definitionProvider;

  /**
   * Результат перехода к определению.
   *
   * @param file Путь к файлу.
   * @param definitions Места объявления символа (обычно одно).
   */
  public record Result(String file, List<DefinitionDto> definitions) {
  }

  @McpTool(
    name = "definition",
    description = "Resolve the symbol at a zero-based position and return where it is declared.",
    // Output schema disabled: Spring AI generates a non-nullable schema that rejects null DTO
    // fields. Known upstream bug, open as of 2.0.0-M6:
    // https://github.com/spring-projects/spring-ai/issues/4825
    // https://github.com/spring-projects/spring-ai/issues/4487
    generateOutputSchema = false,
    // Read-only: only inspects 1C/OneScript code, never mutates anything. Hint clients so the
    // tool is not treated as destructive (#4226).
    annotations = @McpTool.McpAnnotations(
      readOnlyHint = true,
      destructiveHint = false,
      idempotentHint = true,
      openWorldHint = false))
  public Result definition(
    @McpToolParam(required = true, description = McpToolParams.FILE)
    String file,
    @McpToolParam(required = true, description = McpToolParams.LINE)
    int line,
    @McpToolParam(required = true, description = McpToolParams.CHARACTER)
    int character
  ) {
    return documentReader.analyze(file, documentContext -> {
      var params = new DefinitionParams(
        new TextDocumentIdentifier(documentContext.getUri().toString()),
        new Position(line, character)
      );

      var definitions = definitionProvider.getDefinition(documentContext, params).map(
        locations -> locations.stream().map(DefinitionDto::from).toList(),
        links -> links.stream().map(DefinitionDto::from).toList()
      );

      return new Result(file, definitions);
    });
  }
}
