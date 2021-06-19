/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2021
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

import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.RegionSymbol;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Keywords;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.mdclasses.mdo.support.ModuleType;

import java.util.Set;
import java.util.TreeSet;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  scope = DiagnosticScope.BSL,
  modules = {
    ModuleType.CommonModule
  },
  minutesToFix = 1,
  tags = {
    DiagnosticTag.BRAINOVERLOAD,
    DiagnosticTag.SUSPICIOUS
  }
)
public class CommonModuleMissingAPIDiagnostic extends AbstractDiagnostic {

  private static final Set<String> REGION_NAME = makeRegionsAPI();

  private static Set<String> makeRegionsAPI() {
    Set<String> result = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    result.add(Keywords.PUBLIC_REGION_RU);
    result.add(Keywords.PUBLIC_REGION_EN);
    result.add(Keywords.INTERNAL_REGION_RU);
    result.add(Keywords.INTERNAL_REGION_EN);

    return result;
  }

  @Override
  protected void check() {

    var symbolTree = documentContext.getSymbolTree();

    var moduleMethods = symbolTree.getMethods();
    if (moduleMethods.isEmpty()) {
      return;
    }

    var isModuleWithoutExportSub = moduleMethods
      .stream()
      .noneMatch(MethodSymbol::isExport);

    var isModuleWithoutRegionAPI = symbolTree.getModuleLevelRegions()
      .stream()
      .map(RegionSymbol::getName)
      .noneMatch(REGION_NAME::contains);

    if (isModuleWithoutExportSub || isModuleWithoutRegionAPI) {
      Ranges.getFirstSignificantTokenRange(documentContext.getTokens())
        .ifPresent(diagnosticStorage::addDiagnostic);
    }

  }

}
