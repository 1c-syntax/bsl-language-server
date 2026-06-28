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
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.utils.FuzzyMatcher;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * MCP-инструмент {@code global_member_search}: ищет члены глобального контекста 1С/BSL,
 * доступные без префикса — глобальные функции ({@code Сообщить}/{@code Message},
 * {@code СтартовыйСценарий}/{@code StartupScript}), глобальные свойства
 * ({@code Метаданные}/{@code Metadata}) и системные перечисления. Каждый член отдаётся полным
 * {@link TypeMemberDto} с сигнатурами/типом значения и платформенной метаинформацией,
 * сгруппированным по категории.
 * <p>
 * Совпадение запроса {@code query} считается тем же нечётким матчером {@link FuzzyMatcher},
 * что и автодополнение редактора: точное имя → префикс → подстрока → подпоследовательность,
 * по обоим написаниям (ru/en). Результаты внутри категории ранжируются по релевантности
 * (наиболее релевантные — первыми), как в автодополнении.
 * <p>
 * Дополняет точечный {@link GlobalMemberInfoTool}: когда точное имя неизвестно, ЛЛМ ищет
 * глобальные члены (опционально сужая выборку категориями {@code categories}) и находит нужный,
 * не открывая конкретный файл.
 * <p>
 * Поиск выполняется в workspace'е, указанном клиентом через обязательный параметр {@code root}.
 */
@Component
@Profile("mcp")
@RequiredArgsConstructor
public class GlobalMemberSearchTool {

  private final McpWorkspaceResolver workspaceResolver;
  private final GlobalScopeProvider globalScopeProvider;
  private final TypeRegistry typeRegistry;
  private final FuzzyMatcher fuzzyMatcher;

  /**
   * Найденные члены глобального контекста, сгруппированные по категории. Категории, не запрошенные
   * через {@code categories}, представлены пустыми списками. Внутри категории — по убыванию
   * релевантности к {@code query} (или по имени, если запрос не задан).
   *
   * @param count Общее количество найденных членов.
   * @param functions Глобальные функции/процедуры.
   * @param properties Глобальные свойства.
   * @param enums Системные перечисления.
   */
  public record Result(
    int count,
    List<TypeMemberDto> functions,
    List<TypeMemberDto> properties,
    List<TypeMemberDto> enums
  ) {
  }

  @McpTool(
    name = "global_member_search",
    description = "Search 1C/BSL global context members callable without a prefix — global functions "
      + "(`Сообщить`/`Message`, `СтартовыйСценарий`/`StartupScript`), global properties "
      + "(`Метаданные`/`Metadata`) and system enums — grouped by category, with signatures, value/return "
      + "types and platform metadata. The query is matched with the same fuzzy matcher as the editor's "
      + "autocomplete (exact > prefix > substring > subsequence, over both Russian and English spelling) "
      + "and results are ranked by relevance. Optionally restrict to categories. Omit the query to list "
      + "every member. Use it to discover a member when the exact name is unknown, then call "
      + "`global_member_info` for a single member by exact name.",
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
  public Result globalMemberSearch(
    @McpToolParam(required = true, description = McpToolParams.FILE_TYPE)
    FileType fileType,
    @McpToolParam(required = true, description = McpToolParams.ROOT)
    String root,
    @McpToolParam(required = false, description = McpToolParams.GLOBAL_MEMBER_QUERY)
    @Nullable String query,
    @McpToolParam(required = false, description = McpToolParams.GLOBAL_MEMBER_CATEGORIES)
    @Nullable List<GlobalMemberCategory> categories,
    @McpToolParam(required = false, description = McpToolParams.LANGUAGE)
    @Nullable Language language
  ) {
    var effectiveLanguage = language == null ? Language.RU : language;
    var requested = categories == null || categories.isEmpty()
      ? EnumSet.allOf(GlobalMemberCategory.class)
      : EnumSet.copyOf(categories);
    var lowerQuery = query == null || query.isBlank() ? null : query.toLowerCase(Locale.ROOT);

    try (var ignored = WorkspaceContextHolder.forUri(workspaceResolver.resolveWorkspaceUri(root))) {
      var functions = requested.contains(GlobalMemberCategory.FUNCTION)
        ? search(globalScopeProvider.globalFunctions(fileType), lowerQuery, effectiveLanguage)
        : List.<TypeMemberDto>of();

      var propertyMembers = new ArrayList<MemberDescriptor>();
      var enumMembers = new ArrayList<MemberDescriptor>();
      if (requested.contains(GlobalMemberCategory.PROPERTY) || requested.contains(GlobalMemberCategory.ENUM)) {
        classifyProperties(fileType, requested, propertyMembers, enumMembers);
      }
      var properties = search(propertyMembers, lowerQuery, effectiveLanguage);
      var enums = search(enumMembers, lowerQuery, effectiveLanguage);

      return new Result(functions.size() + properties.size() + enums.size(), functions, properties, enums);
    }
  }

  /**
   * Разносит глобальные свойства на собственно свойства и системные перечисления — классификация
   * по типу значения ({@link TypeRegistry#isEnumType}), как в {@link GlobalMemberInfoTool}.
   * Учитываются только запрошенные категории.
   */
  private void classifyProperties(FileType fileType, Set<GlobalMemberCategory> requested,
                                  List<MemberDescriptor> propertyMembers, List<MemberDescriptor> enumMembers) {
    for (var member : globalScopeProvider.globalProperties(fileType)) {
      if (member.generic()) {
        continue;
      }
      var valueType = member.returnTypes().refs().stream().findFirst().orElse(TypeRef.UNKNOWN);
      if (typeRegistry.isEnumType(valueType, fileType)) {
        if (requested.contains(GlobalMemberCategory.ENUM)) {
          enumMembers.add(member);
        }
      } else if (requested.contains(GlobalMemberCategory.PROPERTY)) {
        propertyMembers.add(member);
      }
    }
  }

  /**
   * Фильтрует и ранжирует члены по запросу. Без запроса — все члены по алфавиту; с запросом —
   * только совпавшие по {@link FuzzyMatcher}, отсортированные по релевантности (меньший скор —
   * релевантнее), затем по имени.
   */
  private List<TypeMemberDto> search(List<MemberDescriptor> members,
                                     @Nullable String lowerQuery, Language language) {
    var nonGeneric = members.stream().filter(member -> !member.generic());
    if (lowerQuery == null) {
      return nonGeneric
        .map(member -> TypeMemberDto.from(member, language))
        .sorted(Comparator.comparing(TypeMemberDto::name, String.CASE_INSENSITIVE_ORDER))
        .toList();
    }
    return nonGeneric
      .map(member -> new Scored(member, score(member, lowerQuery)))
      .filter(scored -> scored.score() != FuzzyMatcher.NO_MATCH)
      .sorted(Comparator.comparingInt(Scored::score)
        .thenComparing(scored -> scored.member().displayName(language), String.CASE_INSENSITIVE_ORDER))
      .map(scored -> TypeMemberDto.from(scored.member(), language))
      .toList();
  }

  /** Лучший (минимальный) fuzzy-скор имени члена по обоим написаниям; {@link FuzzyMatcher#NO_MATCH}, если нет. */
  private int score(MemberDescriptor member, String lowerQuery) {
    var name = member.bilingualName();
    var best = FuzzyMatcher.NO_MATCH;
    for (var spelling : List.of(name.ru(), name.en())) {
      if (spelling.isBlank()) {
        continue;
      }
      var current = fuzzyMatcher.score(spelling, lowerQuery);
      if (current != FuzzyMatcher.NO_MATCH && (best == FuzzyMatcher.NO_MATCH || current < best)) {
        best = current;
      }
    }
    return best;
  }

  /** Член с его релевантностью к запросу. */
  private record Scored(MemberDescriptor member, int score) {
  }
}
