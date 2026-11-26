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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.context.symbol.RegionSymbol;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticCompatibilityMode;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Regions;
import com.github._1c_syntax.bsl.types.ModuleType;

import java.util.Collections;
import java.util.EnumMap;
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

  private static final Map<ModuleType, Set<Pattern>> standardRegionsByModuleType = makeStandardRegions();

  private static Map<ModuleType, Set<Pattern>> makeStandardRegions() {
    Map<ModuleType, Set<Pattern>> standardRegions = new EnumMap<>(ModuleType.class);
    for (ModuleType moduleType : ModuleType.values()) {
      standardRegions.put(moduleType, Regions.getStandardRegionsPatternsByModuleType(moduleType));
    }

    return standardRegions;
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
