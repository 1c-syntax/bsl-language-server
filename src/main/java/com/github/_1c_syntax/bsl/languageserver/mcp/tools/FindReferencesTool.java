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
import com.github._1c_syntax.bsl.languageserver.mcp.McpDocumentReader;
import com.github._1c_syntax.bsl.languageserver.providers.ReferencesProvider;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.ReferenceContext;
import org.eclipse.lsp4j.ReferenceParams;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MCP-инструмент: найти все ссылки на символ в позиции курсора.
 * <p>
 * Переиспользует {@link ReferencesProvider} (обработчик {@code textDocument/references}).
 * Кросс-файловые ссылки корректны, так как контекст сервера проиндексирован на старте.
 */
@Component
@Profile("mcp")
@RequiredArgsConstructor
public class FindReferencesTool {

  private final McpDocumentReader documentReader;
  private final ReferencesProvider referencesProvider;

  /**
   * Результат поиска ссылок.
   *
   * @param file Путь к файлу.
   * @param referencesCount Количество найденных ссылок.
   * @param references Список местоположений ссылок.
   */
  public record Result(String file, int referencesCount, List<LocationDto> references) {
  }

  @McpTool(
    name = "find_references",
    description = "Find all references to the symbol located at the given zero-based position in a file.",
    // Output schema disabled: Spring AI generates a non-nullable schema that rejects null DTO
    // fields. Known upstream bug, open as of 2.0.0-M6:
    // https://github.com/spring-projects/spring-ai/issues/4825
    // https://github.com/spring-projects/spring-ai/issues/4487
    generateOutputSchema = false)
  public Result findReferences(
    @McpToolParam(required = true,
      description = "Path to the .bsl/.os file (absolute or relative to the working directory).")
    String file,
    @McpToolParam(required = true, description = "Zero-based line number of the symbol.")
    int line,
    @McpToolParam(required = true, description = "Zero-based character offset within the line.")
    int character
  ) {
    return documentReader.read(file, documentContext -> {
      var params = new ReferenceParams();
      params.setPosition(new Position(line, character));
      params.setContext(new ReferenceContext(true));

      var references = referencesProvider.getReferences(documentContext, params).stream()
        .map(LocationDto::from)
        .toList();
      return new Result(file, references.size(), references);
    });
  }
}
