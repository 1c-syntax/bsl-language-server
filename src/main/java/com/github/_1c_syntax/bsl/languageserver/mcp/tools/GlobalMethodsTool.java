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
import com.github._1c_syntax.bsl.languageserver.types.registry.GlobalScopeProvider;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * MCP-инструмент {@code global_methods}: перечисляет методы глобального контекста 1С/BSL —
 * функции и процедуры, доступные без префикса ({@code Сообщить}/{@code Message},
 * {@code СтартовыйСценарий}/{@code StartupScript} и т.п.). Каждый метод отдаётся полным
 * {@link TypeMemberDto} с сигнатурами, возвращаемыми типами и платформенной метаинформацией.
 * <p>
 * Дополняет точечный {@link GlobalMemberInfoTool}: когда точное имя метода неизвестно, ЛЛМ
 * перечисляет глобальные методы (опционально сужая выборку подстрокой {@code filter}) и
 * находит нужный, не открывая конкретный файл.
 * <p>
 * Перечисление выполняется в workspace'е, указанном клиентом через обязательный параметр
 * {@code root}.
 */
@Component
@Profile("mcp")
@RequiredArgsConstructor
public class GlobalMethodsTool {

  private static final Comparator<TypeMemberDto> BY_NAME =
    Comparator.comparing(TypeMemberDto::name, String.CASE_INSENSITIVE_ORDER);

  private final McpWorkspaceResolver workspaceResolver;
  private final GlobalScopeProvider globalScopeProvider;

  /**
   * Список методов глобального контекста.
   *
   * @param count Количество методов в выборке.
   * @param methods Методы глобального контекста (сигнатуры/типы/метаинформация).
   */
  public record Result(
    int count,
    List<TypeMemberDto> methods
  ) {
  }

  @McpTool(
    name = "global_methods",
    description = "List 1C/BSL global context methods — functions and procedures callable without a "
      + "prefix (e.g. `Сообщить`/`Message`, `СтартовыйСценарий`/`StartupScript`) with their signatures, "
      + "return types and platform metadata. Optionally narrow the result with a name substring filter. "
      + "Use it to discover a method when the exact name is unknown, then call `global_member_info` for "
      + "a single member by exact name.",
    // Output schema disabled: Spring AI generates a non-nullable schema that rejects null DTO fields.
    // Known upstream bug, open as of 2.0.0-M6.
    generateOutputSchema = false)
  public Result globalMethods(
    @McpToolParam(required = true, description = McpToolParams.FILE_TYPE)
    FileType fileType,
    @McpToolParam(required = true, description = McpToolParams.ROOT)
    String root,
    @McpToolParam(required = false, description = McpToolParams.GLOBAL_METHOD_FILTER)
    @Nullable String filter,
    @McpToolParam(required = false, description = McpToolParams.LANGUAGE)
    @Nullable Language language
  ) {
    var effectiveLanguage = language == null ? Language.RU : language;
    var needle = filter == null || filter.isBlank() ? null : filter.toLowerCase(Locale.ROOT);
    try (var ignored = WorkspaceContextHolder.forUri(workspaceResolver.resolveWorkspaceUri(root))) {
      var methods = globalScopeProvider.globalFunctions(fileType).stream()
        .filter(member -> !member.generic())
        .filter(member -> matchesFilter(member, needle))
        .map(member -> TypeMemberDto.from(member, effectiveLanguage))
        .sorted(BY_NAME)
        .toList();
      return new Result(methods.size(), methods);
    }
  }

  private static boolean matchesFilter(MemberDescriptor member, @Nullable String needle) {
    if (needle == null) {
      return true;
    }
    var name = member.bilingualName();
    return name.ru().toLowerCase(Locale.ROOT).contains(needle)
      || name.en().toLowerCase(Locale.ROOT).contains(needle);
  }
}
