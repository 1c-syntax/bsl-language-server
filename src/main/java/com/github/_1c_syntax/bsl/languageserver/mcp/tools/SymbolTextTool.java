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
import com.github._1c_syntax.bsl.languageserver.context.symbol.ModuleSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.mcp.McpDocumentReader;
import com.github._1c_syntax.bsl.languageserver.mcp.dto.RangeDto;
import com.github._1c_syntax.bsl.languageserver.mcp.dto.SymbolTextDto;
import com.github._1c_syntax.bsl.languageserver.mcp.dto.SymbolTextResultDto;
import com.github._1c_syntax.bsl.languageserver.providers.DocumentSymbolProvider;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.Position;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

/**
 * MCP-инструмент {@code symbol_text}: текст символа файла по имени или по позиции.
 * <p>
 * Дополняет {@code document_symbols}: тот отдаёт дерево с диапазонами, а этот — сам текст, чтобы
 * клиент без доступа к файловой системе сервера мог прочитать тело метода, не запрашивая файл целиком.
 */
@Component
@Profile("mcp")
@RequiredArgsConstructor
public class SymbolTextTool {

  /**
   * Значение {@code scope} для символов уровня модуля.
   */
  static final String MODULE_SCOPE = "module";

  private final McpDocumentReader documentReader;

  /**
   * Текст символа файла по имени или по позиции.
   * <p>
   * Символы те же, что в дереве {@code document_symbols}: области, методы и объявленные через
   * {@code Перем} переменные. По {@code name} возвращаются все такие символы с этим именем без учёта
   * регистра; {@code scope} оставляет из них объявленные в указанном методе или, при значении
   * {@code module}, на уровне модуля. По позиции возвращается символ, на имени которого она стоит, иначе
   * самый вложенный метод или переменная, содержащие её: области по позиции находятся только по имени.
   * У каждого символа указано, где он объявлен ({@code scope}), у метода дополнительно возвращается
   * текст комментария-описания над объявлением. Текст берётся из свежего разбора документа
   * ({@link McpDocumentReader#analyze}).
   *
   * @param file      Путь к файлу.
   * @param name      Имя символа; {@code null}, если поиск по позиции. Если переданы и имя, и позиция,
   *                  используется {@code name}.
   * @param scope     Имя метода файла или {@code module}; {@code null} — без отбора. Учитывается только
   *                  вместе с {@code name}.
   * @param line      Строка позиции, zero-based; {@code null}, если поиск по имени.
   * @param character Символ позиции в строке, zero-based; {@code null} равносильно 0.
   * @return Найденные символы с текстом.
   * @throws IllegalArgumentException Если не передан ни {@code name}, ни {@code line}, позиция
   *                                  отрицательна или в {@code scope} указан метод, которого в файле нет.
   */
  @McpTool(
    name = "symbol_text",
    description = "Return the source text of a symbol of a 1C/OneScript file — a region, method or "
      + "`Перем`-declared variable, the symbols `document_symbols` shows. By `name` (case-insensitive): every "
      + "such symbol with that name; `scope` keeps the ones declared in the given method, or at the module "
      + "level with `module`. By zero-based `line` and `character`: the symbol whose name is at the position, "
      + "otherwise the innermost method or variable containing it (regions only by their name). Each match "
      + "carries `scope`: the method it is declared in, or `module`. For methods the description comment "
      + "above the declaration is returned too.",
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
  public SymbolTextResultDto symbolText(
    @McpToolParam(required = true, description = McpToolParams.FILE)
    String file,
    @McpToolParam(required = false, description = McpToolParams.SYMBOL_NAME)
    @Nullable String name,
    @McpToolParam(required = false, description = McpToolParams.SYMBOL_SCOPE)
    @Nullable String scope,
    @McpToolParam(required = false, description = McpToolParams.SYMBOL_LINE)
    @Nullable Integer line,
    @McpToolParam(required = false, description = McpToolParams.SYMBOL_CHARACTER)
    @Nullable Integer character
  ) {
    var symbolName = trimmed(name);
    if (!symbolName.isEmpty()) {
      var symbolScope = trimmed(scope);
      return collect(file, documentContext -> symbolsNamed(documentContext, symbolName, symbolScope));
    }
    if (line == null) {
      throw new IllegalArgumentException(
        "Pass `name` of the symbol, or the zero-based `line` (and optionally `character`) of a position in it.");
    }
    var position = position(line, character);
    return collect(file, documentContext -> symbolAt(documentContext, position));
  }

  private SymbolTextResultDto collect(String file, Function<DocumentContext, List<SourceDefinedSymbol>> search) {
    // У проиндексированного документа текст освобождён, поэтому нужен свежий разбор, как в analyze_file.
    return documentReader.analyze(file, documentContext -> {
      var matches = search.apply(documentContext).stream()
        .map(symbol -> toDto(documentContext, symbol))
        .toList();
      return new SymbolTextResultDto(file, matches.size(), matches);
    });
  }

  private static List<SourceDefinedSymbol> symbolsNamed(DocumentContext documentContext, String name, String scope) {
    var symbolTree = documentContext.getSymbolTree();
    if (!scope.isEmpty() && !MODULE_SCOPE.equalsIgnoreCase(scope) && symbolTree.getMethodSymbol(scope).isEmpty()) {
      // Пустой ответ не отличил бы опечатку в имени метода от символа, которого в методе нет.
      throw new IllegalArgumentException("No method `" + scope + "` in the file: `scope` takes the name of a "
        + "method of the file (see `document_symbols`) or `module`.");
    }
    return symbolTree.getChildrenFlat().stream()
      .filter(DocumentSymbolProvider::isSupported)
      .filter(symbol -> symbol.getName().equalsIgnoreCase(name))
      .filter(symbol -> scope.isEmpty() || scope.equalsIgnoreCase(scopeOf(symbol)))
      .toList();
  }

  // Позиция на имени объявления — сам объявленный символ (так находятся и области), иначе самый вложенный
  // символ, содержащий позицию: getSymbolAtPosition области пропускает. Параметры и переменные от
  // присваивания document_symbols не показывает, они часть тела метода, поэтому поднимаемся к методу.
  private static List<SourceDefinedSymbol> symbolAt(DocumentContext documentContext, Position position) {
    var symbolTree = documentContext.getSymbolTree();
    var symbol = symbolTree.findSymbolBySelectionRange(position)
      .orElseGet(() -> symbolTree.getSymbolAtPosition(position));
    while (!(symbol instanceof ModuleSymbol) && !DocumentSymbolProvider.isSupported(symbol)) {
      symbol = symbol.getParent().orElse(symbolTree.getModule());
    }
    return symbol instanceof ModuleSymbol ? List.of() : List.of(symbol);
  }

  // Где объявлен символ: у переменной — её область объявления, у метода — модуль, у области — метод,
  // внутри которого она лежит, иначе модуль.
  private static String scopeOf(SourceDefinedSymbol symbol) {
    if (symbol instanceof VariableSymbol variable) {
      return variable.getScope() instanceof MethodSymbol method ? method.getName() : MODULE_SCOPE;
    }
    return symbol.getRootParent(MethodSymbol.class)
      .map(SourceDefinedSymbol::getName)
      .orElse(MODULE_SCOPE);
  }

  private static Position position(int line, @Nullable Integer character) {
    var column = character == null ? 0 : character;
    if (line < 0 || column < 0) {
      throw new IllegalArgumentException("`line` and `character` are zero-based and cannot be negative, got "
        + line + ":" + column + ".");
    }
    return new Position(line, column);
  }

  private static String trimmed(@Nullable String value) {
    return value == null ? "" : value.trim();
  }

  private static SymbolTextDto toDto(DocumentContext documentContext, SourceDefinedSymbol symbol) {
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
    return new SymbolTextDto(
      symbol.getName(),
      symbol.getSymbolKind().name(),
      scopeOf(symbol),
      RangeDto.from(range),
      documentContext.getText(range),
      description
    );
  }
}
