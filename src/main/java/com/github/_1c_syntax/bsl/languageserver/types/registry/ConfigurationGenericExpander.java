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

import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberSource;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.mdclasses.CF;
import com.github._1c_syntax.bsl.mdo.ExternalDataSource;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.mdo.MDObject;
import com.github._1c_syntax.bsl.mdo.support.TemplateType;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Извлечение «верхнего» уровня специализации generic-семейств из
 * {@link ConfigurationTypesProvider}: специализация по внешним источникам данных
 * (с multi-placeholder bindings) и материализация generic-property у global
 * library-types (БиблиотекаКартинок/Стилей/МакетовОформленияКомпоновкиДанных).
 * Помогает соблюсти S104/S1448/S1200 в CTP.
 */
@Component
@WorkspaceScope
@RequiredArgsConstructor
public class ConfigurationGenericExpander {

  private static final List<String> EDS_FAMILY_CORES = List.of(
    "ВнешнийИсточникДанных", "ExternalDataSource");

  private final TypeRegistry typeRegistry;
  private final ServerContextProvider serverContextProvider;

  /**
   * Multi-placeholder type-level специализация для семейства внешних источников
   * данных. Идёт по иерархии конфигурации: источник → куб/таблица → таблица
   * измерения/измерение, и на каждом уровне вызывает
   * {@link #registerFamilySpecializations(String, Map)} с расширенным binding'ом.
   */
  public void registerExternalDataSourceSpecializations(Iterable<MD> children) {
    for (var md : children) {
      if (md instanceof ExternalDataSource eds && !eds.getName().isBlank()) {
        registerExternalDataSourceSpecialization(eds);
      }
    }
  }

  private void registerExternalDataSourceSpecialization(ExternalDataSource eds) {
    var sourceBindings = externalSourceBindings(eds.getName());
    registerFamilySpecializations(EDS_FAMILY_CORES, sourceBindings);

    for (var table : eds.getTables()) {
      var tableName = table.getName();
      if (tableName.isBlank()) {
        continue;
      }
      var bindings = new LinkedHashMap<>(sourceBindings);
      putTableName(bindings, tableName);
      registerFamilySpecializations(EDS_FAMILY_CORES, bindings);
    }

    for (var cube : eds.getCubes()) {
      var cubeName = cube.getName();
      if (cubeName.isBlank()) {
        continue;
      }
      registerCubeBindings(sourceBindings, cube, cubeName);
    }
  }

  private void registerCubeBindings(Map<String, String> sourceBindings,
                                    com.github._1c_syntax.bsl.mdo.children.ExternalDataSourceCube cube, String cubeName) {
    var cubeBindings = new LinkedHashMap<>(sourceBindings);
    cubeBindings.put("Имя куба", cubeName);
    cubeBindings.put("Cube name", cubeName);
    registerFamilySpecializations(EDS_FAMILY_CORES, cubeBindings);

    for (var dimTable : cube.getDimensionTables()) {
      var dimTableName = dimTable.getName();
      if (dimTableName.isBlank()) {
        continue;
      }
      var b = new LinkedHashMap<>(cubeBindings);
      putTableName(b, dimTableName);
      registerFamilySpecializations(EDS_FAMILY_CORES, b);
    }
    for (var dim : cube.getDimensions()) {
      var dimName = dim.getName();
      if (dimName.isBlank()) {
        continue;
      }
      var b = new LinkedHashMap<>(cubeBindings);
      b.put("Имя измерения", dimName);
      b.put("Dimension name", dimName);
      registerFamilySpecializations(EDS_FAMILY_CORES, b);
    }
  }

  private void registerFamilySpecializations(Collection<String> familyCores, Map<String, String> bindings) {
    for (var familyCore : familyCores) {
      registerFamilySpecializations(familyCore, bindings);
    }
  }

  private static void putTableName(Map<String, String> bindings, String tableName) {
    bindings.put("Имя таблицы", tableName);
    bindings.put("Имя таблицы внешнего источника данных", tableName);
    bindings.put("Table name", tableName);
    bindings.put("External data source table name", tableName);
  }

  /**
   * Multi-placeholder вариант: специализирует только те generic'и семейства,
   * у которых ВСЕ placeholder'ы покрыты {@code bindings}.
   */
  public void registerFamilySpecializations(String familyCore, Map<String, String> bindings) {
    if (bindings.isEmpty()) {
      return;
    }
    for (var generic : typeRegistry.findAllGenericsByFamilyCore(familyCore)) {
      var parameters = typeRegistry.getTypeParameters(generic);
      if (!parameters.isEmpty() && bindings.keySet().containsAll(parameters)) {
        specializeGeneric(generic, bindings);
      }
    }
  }

  private void specializeGeneric(TypeRef generic, Map<String, String> bindings) {
    var specializedName = TypeRef.specialize(generic, bindings).qualifiedName();
    if (!specializedName.equals(generic.qualifiedName())) {
      typeRegistry.registerSpecialization(specializedName, generic, bindings, FileType.BSL);
    }
  }

  private static Map<String, String> externalSourceBindings(String edsName) {
    var b = new LinkedHashMap<String, String>();
    b.put("Имя внешнего источника", edsName);
    b.put("Имя внешнего источника данных", edsName);
    b.put("External data source name", edsName);
    return b;
  }

  /**
   * Разворачивает generic-property у global-types «общих библиотек» конфигурации:
   * БиблиотекаМакетовОформленияКомпоновкиДанных/Стилей/Картинок.
   */
  public void registerCommonLibraryExpansions() {
    var cf = currentConfiguration();
    if (cf == null) {
      return;
    }
    // resolve() работает по aliasIndex (ru+en синхронно), поэтому достаточно
    // одного из имён — TypeRef один и тот же. Кладём ru как канон.
    registerCommonLibraryExpansion("БиблиотекаМакетовОформленияКомпоновкиДанных",
      namesOf(appearanceTemplatesOf(cf)));
    registerCommonLibraryExpansion("БиблиотекаСтилей", namesOf(cf.getStyles()));
    registerCommonLibraryExpansion("БиблиотекаКартинок", namesOf(cf.getCommonPictures()));
  }

  static List<MDObject> appearanceTemplatesOf(CF configuration) {
    return configuration.getCommonTemplates().stream()
      .filter(t -> t.getTemplateType() == TemplateType.DATA_COMPOSITION_APPEARANCE_TEMPLATE)
      .map(MDObject.class::cast)
      .toList();
  }

  private void registerCommonLibraryExpansion(String typeName, List<String> childNames) {
    registerCommonLibraryExpansion(typeRegistry, typeName, childNames);
  }

  /** package-private для теста: материализовать generic-template по списку имён детей. */
  static void registerCommonLibraryExpansion(TypeRegistry typeRegistry, String typeName,
                                             List<String> childNames) {
    if (childNames.isEmpty()) {
      return;
    }
    var ref = typeRegistry.resolve(typeName).orElse(null);
    if (ref == null) {
      return;
    }
    var placeholder = ConfigurationTypesProvider.memberPlaceholderName(typeRegistry, ref);
    if (placeholder.isBlank()) {
      return;
    }
    var snapshot = typeRegistry.expandedMembers(ref, Map.of(),
      Map.of(placeholder, childNames));
    if (snapshot.isEmpty()) {
      return;
    }
    MemberSource source = () -> snapshot;
    typeRegistry.registerMemberSource(ref, source, FileType.BSL);
  }

  static <T extends MDObject> List<String> namesOf(List<? extends T> items) {
    if (items.isEmpty()) {
      return List.of();
    }
    var result = new ArrayList<String>(items.size());
    for (var it : items) {
      var name = it.getName();
      if (!name.isBlank()) {
        result.add(name);
      }
    }
    return List.copyOf(result);
  }

  @Nullable
  private CF currentConfiguration() {
    var workspaceUri = WorkspaceContextHolder.get();
    if (workspaceUri == null) {
      return null;
    }
    var ctx = serverContextProvider.getAllContexts().get(workspaceUri);
    if (ctx == null) {
      return null;
    }
    var c = ctx.getConfiguration();
    if (c.isEmpty()) {
      return null;
    }
    return c;
  }
}
