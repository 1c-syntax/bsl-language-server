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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.mcp.McpDocumentReader;
import com.github._1c_syntax.bsl.languageserver.mcp.dto.RangeDto;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MCP-инструмент {@code symbol_text}: текст символа файла по имени или по строке.
 * <p>
 * Дополняет {@code document_symbols}: тот отдаёт дерево с диапазонами, а этот — сам текст, чтобы
 * клиент без доступа к файловой системе сервера мог прочитать тело метода, не запрашивая файл целиком.
 */
@Component
@Profile("mcp")
@RequiredArgsConstructor
public class SymbolTextTool {

  private final McpDocumentReader documentReader;

  /**
   * Найденный символ с текстом.
   *
   * @param name Имя символа.
   * @param kind Вид символа ({@link org.eclipse.lsp4j.SymbolKind}).
   * @param range Диапазон символа в файле.
   * @param text Текст диапазона символа.
   * @param description Текст комментария-описания над объявлением метода; {@code null}, если описания нет
   *   или символ не метод.
   */
  public record Match(String name, String kind, RangeDto range, String text, @Nullable String description) {
  }

  /**
   * Результат поиска символов.
   *
   * @param file Путь к файлу.
   * @param matchesCount Количество найденных символов.
   * @param matches Найденные символы в порядке объявления.
   */
  public record Result(String file, int matchesCount, List<Match> matches) {
  }

  @McpTool(
    name = "symbol_text",
    description = "Return the source text of a symbol of a 1C/OneScript file: by `name` — every method, "
      + "variable or region with that name (case-insensitive); by zero-based `line` — the method that "
      + "contains the line. For methods the description comment above the declaration is returned too.",
    // Output schema disabled: Spring AI generates a non-nullable schema that rejects null DTO
    // fields (here — nullable description). Known upstream bug, open as of 2.0.0-M6:
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
  public Result symbolText(
    @McpToolParam(required = true, description = McpToolParams.FILE)
    String file,
    @McpToolParam(required = false, description = McpToolParams.SYMBOL_NAME)
    @Nullable String name,
    @McpToolParam(required = false, description = McpToolParams.SYMBOL_LINE)
    @Nullable Integer line
  ) {
    var symbolName = name == null ? "" : name.trim();
    if (symbolName.isEmpty() && line == null) {
      throw new IllegalArgumentException("Pass `name` of the symbol or the zero-based `line` inside the method.");
    }
    var lineNumber = line == null ? -1 : line;

    // Текст нужен свежий — файл перечитывается с диска, как в analyze_file.
    return documentReader.analyze(file, documentContext -> {
      var symbols = symbolName.isEmpty()
        ? methodsContainingLine(documentContext, lineNumber)
        : symbolsNamed(documentContext, symbolName);
      var matches = symbols.stream()
        .map(symbol -> toMatch(documentContext, symbol))
        .toList();
      return new Result(file, matches.size(), matches);
    });
  }

  private static List<SourceDefinedSymbol> symbolsNamed(DocumentContext documentContext, String name) {
    return documentContext.getSymbolTree().getChildrenFlat(SourceDefinedSymbol.class).stream()
      .filter(symbol -> symbol.getName().equalsIgnoreCase(name))
      .toList();
  }

  private static List<SourceDefinedSymbol> methodsContainingLine(DocumentContext documentContext, int line) {
    return documentContext.getSymbolTree().getChildrenFlat(MethodSymbol.class).stream()
      .filter(method -> method.getRange().getStart().getLine() <= line
        && line <= method.getRange().getEnd().getLine())
      .<SourceDefinedSymbol>map(method -> method)
      .toList();
  }

  private static Match toMatch(DocumentContext documentContext, SourceDefinedSymbol symbol) {
    var range = symbol.getRange();
    String description = null;
    if (symbol instanceof MethodSymbol methodSymbol) {
      description = methodSymbol.getDescription()
        .map(methodDescription -> methodDescription.getRange())
        .filter(descriptionRange -> !descriptionRange.isEmpty())
        .map(descriptionRange -> documentContext.getText(Ranges.create(
          descriptionRange.startLine(),
          descriptionRange.startCharacter(),
          descriptionRange.endLine(),
          descriptionRange.endCharacter()
        )))
        .orElse(null);
    }
    return new Match(
      symbol.getName(),
      symbol.getSymbolKind().name(),
      RangeDto.from(range),
      documentContext.getText(range),
      description
    );
  }
}
