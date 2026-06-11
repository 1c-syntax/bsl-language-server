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
import com.github._1c_syntax.bsl.languageserver.mcp.McpDtos.RangeDto;
import com.github._1c_syntax.bsl.languageserver.providers.HoverProvider;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * MCP-инструмент: подсказка (hover) по символу под курсором.
 * <p>
 * Переиспользует {@link HoverProvider} (обработчик {@code textDocument/hover}).
 * Требует свежий AST (вывод типов), поэтому читает через {@link McpDocumentReader#analyze}.
 */
@Component
@Profile("mcp")
@RequiredArgsConstructor
public class HoverTool {

  private final McpDocumentReader documentReader;
  private final HoverProvider hoverProvider;

  /**
   * Результат подсказки.
   *
   * @param file Путь к файлу.
   * @param contents Содержимое подсказки в формате Markdown ({@code null}, если подсказки нет).
   * @param range Диапазон, к которому относится подсказка ({@code null}, если подсказки нет).
   */
  public record Result(String file, @Nullable String contents, @Nullable RangeDto range) {
  }

  @McpTool(
    name = "hover",
    description = "Return hover information (signature, type, documentation) for the symbol at a "
      + "zero-based position.",
    // Output schema disabled: Spring AI generates a non-nullable schema that rejects null DTO
    // fields (here — null contents/range when there is no hover). Known upstream bug, open as of 2.0.0-M6:
    // https://github.com/spring-projects/spring-ai/issues/4825
    // https://github.com/spring-projects/spring-ai/issues/4487
    generateOutputSchema = false)
  public Result hover(
    @McpToolParam(required = true,
      description = "Path to the .bsl/.os file (absolute or relative to the working directory).")
    String file,
    @McpToolParam(required = true, description = "Zero-based line number of the symbol.")
    int line,
    @McpToolParam(required = true, description = "Zero-based character offset within the line.")
    int character
  ) {
    return documentReader.analyze(file, documentContext -> {
      var params = new HoverParams(
        new TextDocumentIdentifier(documentContext.getUri().toString()),
        new Position(line, character)
      );

      return hoverProvider.getHover(documentContext, params)
        .map(hover -> new Result(
          file,
          contents(hover),
          hover.getRange() == null ? null : RangeDto.from(hover.getRange())
        ))
        .orElseGet(() -> new Result(file, null, null));
    });
  }

  private static String contents(Hover hover) {
    var either = hover.getContents();
    if (either.isRight()) {
      return either.getRight().getValue();
    }
    return either.getLeft().stream()
      .map(part -> part.isLeft() ? part.getLeft() : part.getRight().getValue())
      .collect(Collectors.joining("\n"));
  }
}
