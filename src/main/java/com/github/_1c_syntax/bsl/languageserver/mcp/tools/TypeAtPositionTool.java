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

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.mcp.McpDocumentReader;
import com.github._1c_syntax.bsl.languageserver.mcp.dto.TypeMemberDto;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.Position;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * MCP-инструмент {@code type_at_position}: выводит тип(ы) выражения под курсором в файле 1С/OneScript
 * и доступные на нём члены (методы и свойства) — через вывод типов {@link TypeService}.
 */
@Component
@Profile("mcp")
@RequiredArgsConstructor
public class TypeAtPositionTool {

  private static final Comparator<TypeMemberDto> BY_NAME =
    Comparator.comparing(TypeMemberDto::name, String.CASE_INSENSITIVE_ORDER);

  private final McpDocumentReader documentReader;
  private final TypeService typeService;

  /**
   * Результат вывода типа под курсором.
   *
   * @param file Путь к файлу.
   * @param types Выведенные типы выражения (полные имена); пусто, если тип не выведен.
   * @param members Члены (методы и свойства), доступные на выведенных типах.
   */
  public record Result(
    String file,
    List<String> types,
    List<TypeMemberDto> members
  ) {
  }

  @McpTool(
    name = "type_at_position",
    description = "Infer the type(s) of the expression at a cursor position in a 1C/OneScript file, "
      + "with the members (methods and properties) available on it.",
    // Output schema disabled: Spring AI generates a non-nullable schema that rejects null DTO fields.
    // Known upstream bug, open as of 2.0.0-M6.
    generateOutputSchema = false,
    // Read-only: only inspects 1C/OneScript code, never mutates anything. Hint clients so the
    // tool is not treated as destructive (#4226).
    annotations = @McpTool.McpAnnotations(
      readOnlyHint = true,
      destructiveHint = false,
      idempotentHint = true,
      openWorldHint = false))
  public Result typeAtPosition(
    @McpToolParam(required = true, description = McpToolParams.FILE)
    String file,
    @McpToolParam(required = true, description = McpToolParams.LINE)
    int line,
    @McpToolParam(required = true, description = McpToolParams.CHARACTER)
    int character
  ) {
    return documentReader.analyze(file, documentContext -> {
      var position = new Position(line, character);
      var typeSet = typeService.expressionTypesAt(documentContext, position);

      var types = typeSet.refs().stream()
        .map(TypeRef::qualifiedName)
        .sorted()
        .toList();

      var members = typeSet.refs().stream()
        .flatMap(typeRef -> typeService.getMembers(typeRef, documentContext.getFileType()).stream())
        .filter(member -> !member.generic())
        .map(member -> TypeMemberDto.from(member, Language.RU))
        .distinct()
        .sorted(BY_NAME)
        .toList();

      return new Result(file, types, members);
    });
  }
}
