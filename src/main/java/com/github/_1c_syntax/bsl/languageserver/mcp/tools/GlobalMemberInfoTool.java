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
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.bsl.languageserver.mcp.McpWorkspaceResolver;
import com.github._1c_syntax.bsl.languageserver.mcp.dto.TypeMemberDto;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.registry.GlobalScopeProvider;
import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import com.github._1c_syntax.bsl.languageserver.types.scope.GlobalSymbolScope;
import com.github._1c_syntax.bsl.languageserver.types.symbol.SyntheticSymbol;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MCP-инструмент {@code global_member_info}: по имени глобального члена 1С/BSL
 * (например, {@code Сообщить}/{@code Message}, {@code Метаданные}/{@code Metadata})
 * возвращает его описание — функцию, свойство либо системное перечисление.
 * <p>
 * Резолв проходит в порядке: глобальная функция → глобальное свойство → системное
 * перечисление. Для функций отдаётся полный {@link TypeMemberDto} с сигнатурами и
 * платформенной метаинформацией; для свойств/перечислений — упрощённый дескриптор
 * с типом значения и описанием.
 * <p>
 * Резолв выполняется в workspace'е, указанном клиентом через обязательный параметр
 * {@code root}.
 */
@Component
@Profile("mcp")
@RequiredArgsConstructor
public class GlobalMemberInfoTool {

  private final McpWorkspaceResolver workspaceResolver;
  private final GlobalScopeProvider globalScopeProvider;

  /**
   * Описание глобального члена.
   *
   * @param name Имя члена в запрошенной локали.
   * @param kind Вид члена: {@code FUNCTION}, {@code PROPERTY} или {@code ENUM}.
   * @param member Описание члена (сигнатуры/типы/метаинформация).
   */
  public record Result(
    String name,
    String kind,
    TypeMemberDto member
  ) {
  }

  @McpTool(
    name = "global_member_info",
    description = "Look up a 1C/BSL global member by name (function, property or system enum) and "
      + "return its signatures, return types and platform metadata. Resolves global functions like "
      + "`Сообщить`/`Message`, global properties like `Метаданные`/`Metadata`, and system enums.",
    // Output schema disabled: Spring AI generates a non-nullable schema that rejects null DTO fields.
    // Known upstream bug, open as of 2.0.0-M6.
    generateOutputSchema = false)
  public Result globalMemberInfo(
    @McpToolParam(required = true, description = McpToolParams.GLOBAL_MEMBER_NAME)
    String name,
    @McpToolParam(required = true, description = McpToolParams.FILE_TYPE)
    FileType fileType,
    @McpToolParam(required = true, description = McpToolParams.ROOT)
    String root,
    @McpToolParam(required = false, description = McpToolParams.LANGUAGE)
    @Nullable Language language
  ) {
    var effectiveLanguage = language == null ? Language.RU : language;
    try (var ignored = WorkspaceContextHolder.forUri(workspaceResolver.resolveWorkspaceUri(root))) {
      var function = globalScopeProvider.findFunction(name, fileType);
      if (function.isPresent()) {
        return functionResult(function.get(), effectiveLanguage);
      }

      var propertyType = globalScopeProvider.findGlobalProperty(name, fileType);
      if (propertyType.isPresent()) {
        return globalValueResult(name, fileType, propertyType.get(), "PROPERTY");
      }

      var enumType = globalScopeProvider.findGlobalEnum(name, fileType);
      if (enumType.isPresent()) {
        return globalValueResult(name, fileType, enumType.get(), "ENUM");
      }

      throw new IllegalArgumentException("Global member is not found: " + name);
    }
  }

  private static Result functionResult(MemberDescriptor descriptor, Language language) {
    return new Result(
      descriptor.displayName(language),
      "FUNCTION",
      TypeMemberDto.from(descriptor, language)
    );
  }

  private Result globalValueResult(String requestedName, FileType fileType,
                                   TypeRef valueType, String kind) {
    var entry = globalScopeProvider.findGlobalEntry(requestedName, fileType);
    var canonicalName = entry.map(GlobalSymbolScope.Entry::symbol)
      .map(Symbol::getName)
      .orElse(requestedName);
    var description = entry.map(GlobalSymbolScope.Entry::symbol)
      .filter(SyntheticSymbol.class::isInstance)
      .map(SyntheticSymbol.class::cast)
      .map(symbol -> symbol.getSymbolDescription().getPurposeDescription())
      .filter(text -> !text.isBlank())
      .orElse(null);
    var member = new TypeMemberDto(
      canonicalName,
      "PROPERTY",
      List.of(valueType.qualifiedName()),
      description,
      List.of(),
      false,
      null
    );
    return new Result(canonicalName, kind, member);
  }
}
