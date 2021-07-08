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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Keywords;
import com.github._1c_syntax.mdclasses.mdo.MDCommonModule;
import com.github._1c_syntax.mdclasses.mdo.support.ModuleType;
import com.github._1c_syntax.mdclasses.mdo.support.ReturnValueReuse;
import com.github._1c_syntax.utils.CaseInsensitivePattern;

import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  scope = DiagnosticScope.BSL,
  modules = {
    ModuleType.CommonModule
  },
  minutesToFix = 5,
  tags = {
    DiagnosticTag.STANDARD,
    DiagnosticTag.DESIGN
  }

)
public class CachedPublicDiagnostic extends AbstractDiagnostic {

  private static final Pattern PUBLIC = CaseInsensitivePattern.compile(
    String.format("^(%s|%s)$", Keywords.PUBLIC_REGION_RU, Keywords.PUBLIC_REGION_EN));

  @Override
  protected void check() {

    if (!isCashed(documentContext)) {
      return;
    }

    documentContext.getSymbolTree().getModuleLevelRegions()
      .stream()
      .filter(regionSymbol -> !regionSymbol.getMethods().isEmpty())
      .filter(regionSymbol -> PUBLIC.matcher(regionSymbol.getName()).find())
      .forEach(regionSymbol -> diagnosticStorage.addDiagnostic(regionSymbol.getRegionNameRange()));
  }

  private boolean isCashed(DocumentContext documentContext) {
    return documentContext.getMdObject()
      .filter(MDCommonModule.class::isInstance)
      .map(MDCommonModule.class::cast)
      .map(MDCommonModule::getReturnValuesReuse)
      .filter(this::isReuseValue)
      .isPresent();
  }

  private Boolean isReuseValue(ReturnValueReuse value) {
    return value == ReturnValueReuse.DURING_REQUEST
      || value == ReturnValueReuse.DURING_SESSION;
  }

}
