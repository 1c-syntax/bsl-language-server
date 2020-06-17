/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.context.symbol.RegionSymbol;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticCompatibilityMode;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Keywords;
import com.github._1c_syntax.mdclasses.metadata.additional.ModuleType;
import com.github._1c_syntax.utils.CaseInsensitivePattern;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  scope = DiagnosticScope.BSL,
  minutesToFix = 1,
  compatibilityMode = DiagnosticCompatibilityMode.COMPATIBILITY_MODE_8_3_1,
  tags = {
    DiagnosticTag.STANDARD
  }
)
public class NonStandardRegionDiagnostic extends AbstractDiagnostic {

  private static final Pattern PUBLIC_REGION_NAME =
    createPattern(Keywords.PUBLIC_REGION_RU, Keywords.PUBLIC_REGION_EN);

  private static final Pattern INTERNAL_REGION_NAME =
    createPattern(Keywords.INTERNAL_REGION_RU, Keywords.INTERNAL_REGION_EN);

  private static final Pattern PRIVATE_REGION_NAME =
    createPattern(Keywords.PRIVATE_REGION_RU, Keywords.PRIVATE_REGION_EN);

  private static final Pattern EVENT_HANDLERS_REGION_NAME =
    createPattern(Keywords.EVENT_HANDLERS_REGION_RU, Keywords.EVENT_HANDLERS_REGION_EN);

  private static final Pattern FORM_EVENT_HANDLERS_REGION_NAME =
    createPattern(Keywords.FORM_EVENT_HANDLERS_REGION_RU, Keywords.FORM_EVENT_HANDLERS_REGION_EN);

  private static final Pattern FORM_HEADER_ITEMS_EVENT_HANDLERS_REGION_NAME =
    createPattern(Keywords.FORM_HEADER_ITEMS_EVENT_HANDLERS_REGION_RU,
      Keywords.FORM_HEADER_ITEMS_EVENT_HANDLERS_REGION_EN);

  private static final Pattern FORM_TABLE_ITEMS_EVENT_HANDLERS_REGION_NAME =
    createPattern(Keywords.FORM_TABLE_ITEMS_EVENT_HANDLERS_REGION_START_RU,
      Keywords.FORM_TABLE_ITEMS_EVENT_HANDLERS_REGION_START_EN,
      "^(?:%s|%s)[\\wа-яёЁ]*$");

  private static final Pattern FORM_COMMANDS_EVENT_HANDLERS_REGION_NAME =
    createPattern(Keywords.FORM_COMMANDS_EVENT_HANDLERS_REGION_RU, Keywords.FORM_COMMANDS_EVENT_HANDLERS_REGION_EN);

  private static final Pattern VARIABLES_REGION_NAME =
    createPattern(Keywords.VARIABLES_REGION_RU,
      Keywords.VARIABLES_REGION_EN);

  private static final Pattern INITIALIZE_REGION_NAME =
    createPattern(Keywords.INITIALIZE_REGION_RU, Keywords.INITIALIZE_REGION_EN);

  private static final Map<ModuleType, Set<Pattern>> standardRegionsByModuleType = makeStandardRegions();

  public NonStandardRegionDiagnostic(DiagnosticInfo info) {
    super(info);

  }

  private static Map<ModuleType, Set<Pattern>> makeStandardRegions() {
    Map<ModuleType, Set<Pattern>> standardRegions = new EnumMap<>(ModuleType.class);
    for (ModuleType moduleType : ModuleType.values()) {
      standardRegions.put(moduleType, getStandardRegionsByModuleType(moduleType));
    }

    return standardRegions;
  }

  private static Set<Pattern> getStandardRegionsByModuleType(ModuleType moduleType) {

    if (moduleType == ModuleType.UNKNOWN) {
      return Collections.emptySet();
    }

    Set<Pattern> standardRegions = new HashSet<>();

    switch (moduleType) {
      case FormModule:
        standardRegions.add(VARIABLES_REGION_NAME);
        standardRegions.add(FORM_EVENT_HANDLERS_REGION_NAME);
        standardRegions.add(FORM_HEADER_ITEMS_EVENT_HANDLERS_REGION_NAME);
        standardRegions.add(FORM_TABLE_ITEMS_EVENT_HANDLERS_REGION_NAME);
        standardRegions.add(FORM_COMMANDS_EVENT_HANDLERS_REGION_NAME);
        standardRegions.add(INITIALIZE_REGION_NAME);
        break;
      case ObjectModule:
      case RecordSetModule:
        standardRegions.add(VARIABLES_REGION_NAME);
        standardRegions.add(PUBLIC_REGION_NAME);
        standardRegions.add(EVENT_HANDLERS_REGION_NAME);
        standardRegions.add(INTERNAL_REGION_NAME);
        standardRegions.add(INITIALIZE_REGION_NAME);
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
      case HTTPServiceModule:
        standardRegions.add(EVENT_HANDLERS_REGION_NAME);
        break;
      default:
        // для Unknown ничего
    }

    // у всех типов модулей есть такая область
    standardRegions.add(PRIVATE_REGION_NAME);
    return standardRegions;
  }

  private static Pattern createPattern(String keywordRu, String keywordEn) {
    return createPattern(keywordRu, keywordEn, "^(?:%s|%s)$");
  }

  private static Pattern createPattern(String keywordRu, String keywordEn, String template) {
    return CaseInsensitivePattern.compile(
      String.format(template, keywordRu, keywordEn)
    );
  }

  @Override
  public void check() {

    // нет смысла говорить о стандартах для неизвестных модулях
    Set<Pattern> standardRegions = standardRegionsByModuleType.getOrDefault(
      documentContext.getModuleType(), Collections.emptySet());

    if (standardRegions.isEmpty()) {
      return;
    }

    List<RegionSymbol> regions = documentContext.getSymbolTree().getModuleLevelRegions();

    // чтобы не было лишних FP, анализировать модуль без областей не будем
    // вешать диагностику тож не будем, пусть вешается "CodeOutOfRegionDiagnostic"
    if (regions.isEmpty()) {
      return;
    }

    // проверим, что область находится в списке доступных
    regions.forEach((RegionSymbol region) -> {
      if (standardRegions.stream().noneMatch(regionName -> regionName.matcher(region.getName()).find())) {
        diagnosticStorage.addDiagnostic(
          region.getStartRange(),
          info.getMessage(region.getName())
        );
      }
    });
  }
}
