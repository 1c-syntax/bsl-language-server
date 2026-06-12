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
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.bsl.languageserver.mcp.dto.TypeMemberDto;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * MCP-инструмент {@code type_info}: по имени типа 1С/BSL (например, {@code Массив}) возвращает его
 * методы и свойства с сигнатурами и типами — из системы типов {@link TypeService}.
 * <p>
 * Тип ищется в реестре первого зарегистрированного рабочего пространства (платформенные типы общие
 * для всех пространств). Имена и описания — на русском, как в платформе 1С.
 */
@Component
@Profile("mcp")
@RequiredArgsConstructor
public class TypeInfoTool {

  private static final Comparator<TypeMemberDto> BY_NAME =
    Comparator.comparing(TypeMemberDto::name, String.CASE_INSENSITIVE_ORDER);

  private final ServerContextProvider serverContextProvider;
  private final TypeService typeService;

  /**
   * Описание типа.
   *
   * @param name Полное имя типа.
   * @param kind Вид типа ({@code PLATFORM}, {@code PRIMITIVE}, {@code CONFIGURATION}, ...).
   * @param description Описание типа; {@code null}, если отсутствует.
   * @param properties Свойства типа.
   * @param methods Методы типа.
   */
  public record Result(
    String name,
    String kind,
    @Nullable String description,
    List<TypeMemberDto> properties,
    List<TypeMemberDto> methods
  ) {
  }

  @McpTool(
    name = "type_info",
    description = "Look up a 1C/BSL type by name (e.g. `Массив`/`Array`) and return its properties and "
      + "methods with signatures, parameters and return types.",
    // Output schema disabled: Spring AI generates a non-nullable schema that rejects null DTO fields
    // (here — nullable description/defaultValue). Known upstream bug, open as of 2.0.0-M6.
    generateOutputSchema = false)
  public Result typeInfo(
    @McpToolParam(required = true, description = McpToolParams.TYPE_NAME)
    String typeName,
    @McpToolParam(required = true, description = McpToolParams.FILE_TYPE)
    FileType fileType
  ) {
    try (var ignored = WorkspaceContextHolder.forUri(anyWorkspaceUri())) {
      var typeRef = typeService.resolve(typeName, fileType)
        .orElseThrow(() -> new IllegalArgumentException("Type is not found: " + typeName));

      var members = typeService.getMembers(typeRef, fileType);
      var properties = membersOfKind(members, MemberKind.PROPERTY);
      var methods = membersOfKind(members, MemberKind.METHOD);

      var description = typeService.getDescription(typeRef, Language.RU, fileType);
      return new Result(
        typeRef.qualifiedName(),
        typeRef.kind().name(),
        description == null || description.isBlank() ? null : description,
        properties,
        methods
      );
    }
  }

  private static List<TypeMemberDto> membersOfKind(Collection<MemberDescriptor> members, MemberKind kind) {
    return members.stream()
      .filter(member -> !member.generic() && member.kind() == kind)
      .map(member -> TypeMemberDto.from(member, Language.RU))
      .sorted(BY_NAME)
      .toList();
  }

  private URI anyWorkspaceUri() {
    return serverContextProvider.getAllContexts().values().stream()
      .findFirst()
      .map(ServerContext::getWorkspaceUri)
      .orElseThrow(() -> new IllegalArgumentException(
        "No registered workspace. Open a workspace via MCP roots first."));
  }
}
