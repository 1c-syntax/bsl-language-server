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

import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.mdclasses.CF;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.mdo.storage.form.FormDynamicListAttribute;
import com.github._1c_syntax.bsl.types.MDOType;
import com.github._1c_syntax.bsl.types.MdoReference;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Поля таблицы языка запросов по её имени.
 * <p>
 * Откуда поля берутся, резолвер не знает: он находит объект метаданных и
 * платформенное описание таблицы, а дальше опрашивает {@link QueryTableFieldSource}
 * по очереди. При совпадении имён выигрывает источник, спрошенный раньше;
 * бестиповое поле при этом дополняется типом из следующего источника — состав
 * полей списка называет поле, но тип у записи состава есть редко.
 */
@Component
@WorkspaceScope
@RequiredArgsConstructor
public class QueryTableResolver {

  private final List<QueryTableFieldSource> sources;
  private final PlatformQueryTables platformTables;
  private final ServerContextProvider serverContextProvider;

  /**
   * Поля таблицы.
   *
   * @param tableName имя таблицы, как его задаёт конфигурация
   *                  ({@code Catalog.Номенклатура}).
   * @return поля таблицы; пусто, если ни один источник её не знает.
   */
  public List<MemberDescriptor> fields(String tableName) {
    return resolve(tableName, null);
  }

  /**
   * Поля таблицы, которую читает динамический список: к полям таблицы
   * добавляется объявленный самим списком состав полей.
   *
   * @param tableName имя основной таблицы списка; пустое, если её нет.
   * @param list      динамический список.
   * @return поля таблицы; пусто, если ни один источник её не знает.
   */
  public List<MemberDescriptor> fields(String tableName, FormDynamicListAttribute list) {
    return resolve(tableName, list);
  }

  private List<MemberDescriptor> resolve(String tableName, @Nullable FormDynamicListAttribute list) {
    var match = platformTables.find(tableName);
    var configuration = currentConfiguration();
    var request = new QueryTableRequest(
      tableName,
      findMdo(tableName, configuration),
      configuration,
      match == null ? null : match.table(),
      match == null ? Map.of() : match.nameBindings(),
      list);

    var byName = new LinkedHashMap<String, MemberDescriptor>();
    for (var source : sources) {
      for (var member : source.fields(request)) {
        merge(byName, member);
      }
    }
    return List.copyOf(new ArrayList<>(byName.values()));
  }

  /**
   * Кладёт поле в набор. Поле, названное раньше, остаётся — но если оно без
   * типа, тип берётся у одноимённого поля из следующего источника.
   */
  private static void merge(Map<String, MemberDescriptor> byName, MemberDescriptor member) {
    var key = member.name().toLowerCase(Locale.ROOT);
    var existing = byName.get(key);
    if (existing == null) {
      byName.put(key, member);
      return;
    }
    if (existing.returnTypes().isEmpty() && !member.returnTypes().isEmpty()) {
      byName.put(key, existing.withReturnTypes(member.returnTypes()));
    }
  }

  /**
   * Объект метаданных таблицы. Имя таблицы начинается его ссылкой
   * ({@code Catalog.Номенклатура}), а у подчинённого объекта ссылка продолжается
   * такими же парами «вид.имя»
   * ({@code ExternalDataSource.X.Table.Y}) — поэтому берутся все пары подряд.
   * Сегмент, который парой не является, ссылку заканчивает: так отделяется
   * имя виртуальной таблицы ({@code AccumulationRegister.Продажи.Turnovers}).
   *
   * @return объект метаданных; {@code null}, если такого имени в конфигурации нет.
   */
  private static @Nullable MD findMdo(String tableName, @Nullable CF configuration) {
    if (configuration == null) {
      return null;
    }
    var segments = tableName.split("\\.", -1);
    MdoReference reference = null;
    for (var i = 0; i + 1 < segments.length; i += 2) {
      var mdoType = MDOType.fromValue(segments[i]);
      if (mdoType.isEmpty()) {
        break;
      }
      reference = reference == null
        ? MdoReference.create(mdoType.get(), segments[i + 1])
        : MdoReference.create(reference, mdoType.get(), segments[i + 1]);
    }
    return reference == null ? null : configuration.findChild(reference).orElse(null);
  }

  /**
   * Метаданные текущего workspace. {@code ServerContext} — прототипный бин, и
   * внедрённый напрямую он оказался бы пустым, поэтому контекст берётся у
   * провайдера по workspace текущего потока.
   *
   * @return метаданные; {@code null}, если workspace не выбран либо конфигурация
   *   ещё не прочитана.
   */
  private @Nullable CF currentConfiguration() {
    var workspaceUri = WorkspaceContextHolder.get();
    if (workspaceUri == null) {
      return null;
    }
    var serverContext = serverContextProvider.getAllContexts().get(workspaceUri);
    if (serverContext == null) {
      return null;
    }
    var configuration = serverContext.getConfiguration();
    return configuration.isEmpty() ? null : configuration;
  }
}
