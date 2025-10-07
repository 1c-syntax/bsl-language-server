/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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
package com.github._1c_syntax.bsl.languageserver.utils;

import com.github._1c_syntax.bsl.types.ModuleType;
import com.github._1c_syntax.bsl.types.MultiName;
import com.github._1c_syntax.bsl.types.ScriptVariant;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import lombok.experimental.UtilityClass;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Вспомогательный класс, содержащий методы для работы с программными областями 1С
 */
@UtilityClass
public class Regions {

  private final Pattern PUBLIC_REGION_NAME = createPattern(Keywords.PUBLIC_REGION);
  private final Pattern INTERNAL_REGION_NAME = createPattern(Keywords.INTERNAL_REGION);
  private final Pattern PRIVATE_REGION_NAME = createPattern(Keywords.PRIVATE_REGION);
  private final Pattern EVENT_HANDLERS_REGION_NAME = createPattern(Keywords.EVENT_HANDLERS_REGION);
  private final Pattern FORM_EVENT_HANDLERS_REGION_NAME = createPattern(Keywords.FORM_EVENT_HANDLERS_REGION);
  private final Pattern FORM_HEADER_ITEMS_EVENT_HANDLERS_REGION_NAME =
    createPattern(Keywords.FORM_HEADER_ITEMS_EVENT_HANDLERS_REGION);
  private final Pattern FORM_TABLE_ITEMS_EVENT_HANDLERS_REGION_NAME =
    createPattern(Keywords.FORM_TABLE_ITEMS_EVENT_HANDLERS_REGION_START, "^(?:%s|%s)[\\wа-яёЁ]*$");
  private final Pattern FORM_COMMANDS_EVENT_HANDLERS_REGION_NAME =
    createPattern(Keywords.FORM_COMMANDS_EVENT_HANDLERS_REGION);
  private final Pattern VARIABLES_REGION_NAME = createPattern(Keywords.VARIABLES_REGION);
  private final Pattern INITIALIZE_REGION_NAME = createPattern(Keywords.INITIALIZE_REGION);

  /**
   * Метод возвращает паттерны регулярных выражений
   * удовлетворяющих стандартным наименованиям областей 1С на русском и английском языках
   *
   * @param moduleType тип программного модуля 1С
   * @return множество паттернов имен областей 1С для конкретного типа модуля
   */
  public Set<Pattern> getStandardRegionsPatternsByModuleType(ModuleType moduleType) {

    if (moduleType == ModuleType.UNKNOWN) {
      return Collections.emptySet();
    }

    Set<Pattern> standardRegions = new HashSet<>();

    switch (moduleType) {
      case FormModule:
        addFormModuleRegions(standardRegions);
        break;
      case ObjectModule:
      case RecordSetModule:
        addRecordSetRegions(standardRegions);
        break;
      case ValueManagerModule:
        standardRegions.add(VARIABLES_REGION_NAME);
        standardRegions.add(PUBLIC_REGION_NAME);
        standardRegions.add(EVENT_HANDLERS_REGION_NAME);
        standardRegions.add(INTERNAL_REGION_NAME);
        break;
      case CommonModule:
        standardRegions.add(PUBLIC_REGION_NAME);
        standardRegions.add(INTERNAL_REGION_NAME);
        break;
      case ApplicationModule:
      case ManagedApplicationModule:
      case OrdinaryApplicationModule:
        standardRegions.add(VARIABLES_REGION_NAME);
        standardRegions.add(PUBLIC_REGION_NAME);
        standardRegions.add(EVENT_HANDLERS_REGION_NAME);
        break;
      case CommandModule:
      case SessionModule:
      case HTTPServiceModule:
      case WEBServiceModule:
        standardRegions.add(EVENT_HANDLERS_REGION_NAME);
        break;
      case ExternalConnectionModule:
        standardRegions.add(PUBLIC_REGION_NAME);
        standardRegions.add(EVENT_HANDLERS_REGION_NAME);
        break;
      case ManagerModule:
        standardRegions.add(PUBLIC_REGION_NAME);
        standardRegions.add(EVENT_HANDLERS_REGION_NAME);
        standardRegions.add(INTERNAL_REGION_NAME);
        break;
      default:
        // для Unknown ничего
    }

    // у всех типов модулей есть такая область
    standardRegions.add(PRIVATE_REGION_NAME);
    return standardRegions;
  }

  /**
   * Получает стандартные области OneScript, на основании языка
   *
   * @param configurationLanguage язык конфигурации, может быть русским или английским
   * @return множество имен стандартных областей OneSCript
   */
  public static Set<String> getOneScriptStandardRegions(ScriptVariant configurationLanguage) {
    Set<String> regionsName = new LinkedHashSet<>();
    regionsName.add(Keywords.VARIABLES_REGION.get(configurationLanguage));
    regionsName.add(Keywords.PUBLIC_REGION.get(configurationLanguage));
    regionsName.add(Keywords.INTERNAL_REGION.get(configurationLanguage));
    regionsName.add(Keywords.PRIVATE_REGION.get(configurationLanguage));
    return regionsName;
  }

  /**
   * Получает стандартные имена областей 1С, на основании типа программного модуля
   * и языка конфигурации
   *
   * @param moduleType тип программного модуля 1С
   * @param language   язык конфигурации, может быть русским или английским
   * @return множество имен стандартных областей 1С
   */
  public Set<String> getStandardRegionsNamesByModuleType(ModuleType moduleType, ScriptVariant language) {
    return getStandardRegionNames(moduleType, language);
  }

  private static Set<String> getStandardRegionNames(ModuleType moduleType, ScriptVariant language) {
    Set<String> regionsName = new LinkedHashSet<>();
    switch (moduleType) {
      case FormModule:
        addFormModuleRegionsNames(regionsName, language);
        break;
      case ObjectModule:
      case RecordSetModule:
        addObjectAndRecordSetRegionsName(regionsName, language);
        break;
      case ValueManagerModule:
        addValueManageRegionsName(regionsName, language);
        break;
      case CommonModule:
        addCommonModuleRegionNames(regionsName, language);
        break;
      case ApplicationModule:
      case ManagedApplicationModule:
      case OrdinaryApplicationModule:
        addApplicationModulesRegionsNames(regionsName, language);
        break;
      case CommandModule:
      case SessionModule:
      case HTTPServiceModule:
      case WEBServiceModule:
        addCommandAndSessionModulesRegionsNames(regionsName, language);
        break;
      case ExternalConnectionModule:
        addExternalConnectionRegionsNames(regionsName, language);
        break;
      case ManagerModule:
        addManagerModuleRegionsNames(regionsName, language);
        break;
      default:
        // для Unknown ничего
    }

    // у всех типов модулей есть такая область
    regionsName.add(Keywords.PRIVATE_REGION.get(language));

    return regionsName;
  }

  private static void addManagerModuleRegionsNames(Set<String> regionsName, ScriptVariant language) {
    regionsName.add(Keywords.PUBLIC_REGION.get(language));
    regionsName.add(Keywords.EVENT_HANDLERS_REGION.get(language));
    regionsName.add(Keywords.INTERNAL_REGION.get(language));
  }

  private static void addExternalConnectionRegionsNames(Set<String> regionsName, ScriptVariant language) {
    regionsName.add(Keywords.PUBLIC_REGION.get(language));
    regionsName.add(Keywords.EVENT_HANDLERS_REGION.get(language));
  }

  private static void addCommandAndSessionModulesRegionsNames(Set<String> regionsName, ScriptVariant language) {
    regionsName.add(Keywords.EVENT_HANDLERS_REGION.get(language));
  }

  private static void addApplicationModulesRegionsNames(Set<String> regionsName, ScriptVariant language) {
    regionsName.add(Keywords.VARIABLES_REGION.get(language));
    regionsName.add(Keywords.PUBLIC_REGION.get(language));
    regionsName.add(Keywords.EVENT_HANDLERS_REGION.get(language));
  }

  private static void addCommonModuleRegionNames(Set<String> regionsName, ScriptVariant language) {
    regionsName.add(Keywords.PUBLIC_REGION.get(language));
    regionsName.add(Keywords.INTERNAL_REGION.get(language));
  }

  private static void addValueManageRegionsName(Set<String> regionsName, ScriptVariant language) {
    regionsName.add(Keywords.VARIABLES_REGION.get(language));
    regionsName.add(Keywords.PUBLIC_REGION.get(language));
    regionsName.add(Keywords.EVENT_HANDLERS_REGION.get(language));
    regionsName.add(Keywords.INTERNAL_REGION.get(language));
  }

  private static void addObjectAndRecordSetRegionsName(Set<String> regionsName, ScriptVariant language) {
    regionsName.add(Keywords.VARIABLES_REGION.get(language));
    regionsName.add(Keywords.PUBLIC_REGION.get(language));
    regionsName.add(Keywords.EVENT_HANDLERS_REGION.get(language));
    regionsName.add(Keywords.INTERNAL_REGION.get(language));
    regionsName.add(Keywords.INITIALIZE_REGION.get(language));
  }

  private static void addFormModuleRegionsNames(Set<String> regionsName, ScriptVariant language) {
    regionsName.add(Keywords.VARIABLES_REGION.get(language));
    regionsName.add(Keywords.FORM_EVENT_HANDLERS_REGION.get(language));
    regionsName.add(Keywords.FORM_HEADER_ITEMS_EVENT_HANDLERS_REGION.get(language));
    regionsName.add(Keywords.FORM_TABLE_ITEMS_EVENT_HANDLERS_REGION_START.get(language));
    regionsName.add(Keywords.FORM_COMMANDS_EVENT_HANDLERS_REGION.get(language));
    regionsName.add(Keywords.INITIALIZE_REGION.get(language));
  }


  private void addRecordSetRegions(Set<Pattern> standardRegions) {
    standardRegions.add(VARIABLES_REGION_NAME);
    standardRegions.add(PUBLIC_REGION_NAME);
    standardRegions.add(EVENT_HANDLERS_REGION_NAME);
    standardRegions.add(INTERNAL_REGION_NAME);
    standardRegions.add(INITIALIZE_REGION_NAME);
  }

  private void addFormModuleRegions(Set<Pattern> standardRegions) {
    standardRegions.add(VARIABLES_REGION_NAME);
    standardRegions.add(FORM_EVENT_HANDLERS_REGION_NAME);
    standardRegions.add(FORM_HEADER_ITEMS_EVENT_HANDLERS_REGION_NAME);
    standardRegions.add(FORM_TABLE_ITEMS_EVENT_HANDLERS_REGION_NAME);
    standardRegions.add(FORM_COMMANDS_EVENT_HANDLERS_REGION_NAME);
    standardRegions.add(INITIALIZE_REGION_NAME);
  }

  private static Pattern createPattern(MultiName keyword) {
    return createPattern(keyword, "^(?:%s|%s)$");
  }

  private static Pattern createPattern(MultiName keyword, String template) {
    return CaseInsensitivePattern.compile(
      String.format(template, keyword.getRu(), keyword.getEn())
    );
  }
}
