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
package com.github._1c_syntax.bsl.languageserver.types.registry;

import com.github._1c_syntax.bsl.context.api.ContextNames;
import com.github._1c_syntax.bsl.context.api.ContextQueryTable;
import com.github._1c_syntax.bsl.context.api.QueryContextProvider;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.utils.Lazy;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Таблицы языка запросов из синтакс-помощника, сопоставленные с именем таблицы
 * из конфигурации.
 * <p>
 * Имя таблицы у платформы шаблонное ({@code Справочник.<Имя справочника>},
 * {@code РегистрНакопления.<Имя регистра накопления>.Остатки}) и двуязычное, а
 * конфигурация называет ту же таблицу конкретно и на одном языке
 * ({@code Catalog.Номенклатура}). Сопоставление посегментное: сегмент-плейсхолдер
 * принимает что угодно и запоминает подставленное имя, остальные должны совпасть.
 */
@Component
@WorkspaceScope
@RequiredArgsConstructor
class PlatformQueryTables {

  /**
   * Написания сегментов, которые пишет конфигуратор, но которых в синтакс-помощнике
   * уже нет: виртуальную таблицу задач по исполнителю платформа переименовала в
   * {@code TasksByPerformer}, а в {@code MainTable} она по-прежнему записывается
   * прежним именем (22 списка в разобранных выгрузках).
   */
  private static final Map<String, String> RENAMED_SEGMENTS = Map.of(
    "tasksbyexecutive", "TasksByPerformer");

  private final BslContextHolder contextHolder;

  private final Lazy<List<ContextQueryTable>> tables = new Lazy<>(this::loadTables);

  /**
   * Таблица и подстановки в её имя.
   *
   * @param table        платформенное описание таблицы.
   * @param nameBindings имена, подставленные в плейсхолдеры имени
   *                     ({@code Имя справочника → Номенклатура}).
   */
  record Match(ContextQueryTable table, Map<String, String> nameBindings) {
  }

  /**
   * Ищет таблицу по имени из конфигурации.
   * <p>
   * Одноимённых таблиц у платформы две только у регистра бухгалтерии — с
   * поддержкой корреспонденции и без; какая из них нужна, по имени не понять,
   * поэтому берётся первая. Наборы полей у них различаются несколькими полями
   * с суффиксами {@code Дт}/{@code Кт}.
   *
   * @param tableName имя таблицы ({@code Catalog.Номенклатура}).
   * @return найденная таблица; {@code null}, если платформа не установлена либо
   *   таблицы с таким именем у неё нет.
   */
  @Nullable Match find(String tableName) {
    if (tableName.isBlank()) {
      return null;
    }
    var segments = tableName.split("\\.", -1);
    for (var table : tables.getOrCompute()) {
      var bindings = match(table.name().getName(), segments);
      if (bindings == null) {
        bindings = match(table.name().getAlias(), segments);
      }
      if (bindings != null) {
        return new Match(table, bindings);
      }
    }
    return null;
  }

  /**
   * Сопоставляет шаблонное имя таблицы с сегментами конкретного имени.
   *
   * @param templateName шаблонное имя ({@code Справочник.<Имя справочника>}).
   * @param segments     сегменты конкретного имени.
   * @return подстановки в плейсхолдеры; {@code null}, если имена не совпали.
   */
  private static @Nullable Map<String, String> match(String templateName, String[] segments) {
    if (templateName.isEmpty()) {
      return null;
    }
    var templateSegments = templateName.split("\\.", -1);
    if (templateSegments.length != segments.length) {
      return null;
    }
    var bindings = new LinkedHashMap<String, String>();
    for (var i = 0; i < templateSegments.length; i++) {
      if (!matchSegment(templateSegments[i], segments[i], bindings)) {
        return null;
      }
    }
    return QueryTablePlaceholders.withSynonyms(bindings);
  }

  /**
   * Сопоставляет один сегмент шаблона с сегментом конкретного имени. Сегмент
   * без плейсхолдера должен совпасть буквально, сегмент-плейсхолдер принимает
   * любое непустое имя и запоминает его подстановкой.
   *
   * @param templateSegment сегмент шаблона ({@code <Имя справочника>}).
   * @param segment         сегмент конкретного имени.
   * @param bindings        подстановки, куда кладётся значение плейсхолдера.
   * @return {@code true}, если сегменты совпали.
   */
  private static boolean matchSegment(String templateSegment, String segment, Map<String, String> bindings) {
    var placeholders = ContextNames.placeholders(templateSegment);
    if (placeholders.isEmpty()) {
      return segmentMatches(templateSegment, segment);
    }
    var placeholder = placeholders.get(0);
    if (placeholders.size() != 1 || placeholder.start() != 0
      || placeholder.end() != templateSegment.length() || segment.isBlank()) {
      // Сегмент с плейсхолдером внутри у имён таблиц не встречается; такой
      // шаблон сопоставить нечем.
      return false;
    }
    bindings.put(placeholder.name(), segment);
    return true;
  }

  /**
   * Совпадает ли сегмент конкретного имени с сегментом шаблона — с учётом
   * прежних написаний, см. {@link #RENAMED_SEGMENTS}.
   */
  private static boolean segmentMatches(String templateSegment, String segment) {
    if (templateSegment.equalsIgnoreCase(segment)) {
      return true;
    }
    var renamed = RENAMED_SEGMENTS.get(segment.toLowerCase(Locale.ROOT));
    return renamed != null && renamed.equalsIgnoreCase(templateSegment);
  }

  /**
   * Таблицы платформы: из синтакс-помощника установленной 1С, а без неё — из
   * встроенного пака, снятого с той же справки. Состав полей и типы в паке те
   * же, нет только описаний.
   */
  private List<ContextQueryTable> loadTables() {
    return contextHolder.getQueryProvider()
      .map(QueryContextProvider::getTables)
      .orElseGet(BuiltinQueryTables::load);
  }
}
