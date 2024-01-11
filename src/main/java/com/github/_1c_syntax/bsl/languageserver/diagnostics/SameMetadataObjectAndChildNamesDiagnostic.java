/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2024
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

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.mdo.Attribute;
import com.github._1c_syntax.bsl.mdo.AttributeOwner;
import com.github._1c_syntax.bsl.mdo.ChildrenOwner;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.mdo.TabularSection;
import com.github._1c_syntax.bsl.mdo.TabularSectionOwner;
import com.github._1c_syntax.bsl.types.MDOType;
import com.github._1c_syntax.bsl.types.ModuleType;
import com.github._1c_syntax.utils.StringInterner;

import java.util.List;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.CRITICAL,
  minutesToFix = 30,
  tags = {
    DiagnosticTag.STANDARD,
    DiagnosticTag.SQL,
    DiagnosticTag.DESIGN
  },
  modules = {
    ModuleType.ManagerModule,
    ModuleType.ObjectModule,
    ModuleType.SessionModule
  },
  scope = DiagnosticScope.BSL,
  canLocateOnProject = true
)
public class SameMetadataObjectAndChildNamesDiagnostic extends AbstractMetadataDiagnostic {

  private final LanguageServerConfiguration serverConfiguration;
  private final StringInterner stringInterner;

  SameMetadataObjectAndChildNamesDiagnostic(
    LanguageServerConfiguration serverConfiguration,
    StringInterner stringInterner
  ) {
    super(List.of(
      MDOType.ACCOUNTING_REGISTER,
      MDOType.ACCUMULATION_REGISTER,
      MDOType.BUSINESS_PROCESS,
      MDOType.CALCULATION_REGISTER,
      MDOType.CATALOG,
      MDOType.CHART_OF_ACCOUNTS,
      MDOType.CHART_OF_CALCULATION_TYPES,
      MDOType.CHART_OF_CHARACTERISTIC_TYPES,
      MDOType.DOCUMENT,
      MDOType.EXCHANGE_PLAN,
      MDOType.INFORMATION_REGISTER,
      MDOType.TASK
    ));
    this.serverConfiguration = serverConfiguration;
    this.stringInterner = stringInterner;
  }

  @Override
  protected void checkMetadata(MD mdo) {
    if (!(mdo instanceof ChildrenOwner)) {
      return;
    }

    if (mdo instanceof AttributeOwner attributeOwner && !attributeOwner.getAllAttributes().isEmpty()) {
      var mdoName = stringInterner.intern(mdo.getName());
      checkkAttributes(attributeOwner.getAllAttributes(), mdoName);
    }

    if (mdo instanceof TabularSectionOwner tabularSectionOwner && !tabularSectionOwner.getTabularSections().isEmpty()) {
      tabularSectionOwner.getTabularSections().forEach((TabularSection table) -> {
        var tableName = stringInterner.intern(table.getName());
        checkkAttributes(table.getAllAttributes(), tableName);
      });
    }
  }

  private void checkkAttributes(List<Attribute> attributeOwner, String mdoName) {
    attributeOwner.stream()
      .filter(attribute -> mdoName.equalsIgnoreCase(attribute.getName()))
      .forEach(attribute -> addAttributeDiagnostic(attribute, mdoName));
  }

  private void addAttributeDiagnostic(Attribute attribute, String mdoName) {
    String mdoRef;
    if (serverConfiguration.getLanguage() == Language.RU) {
      mdoRef = attribute.getMdoReference().getMdoRefRu();
    } else {
      mdoRef = attribute.getMdoReference().getMdoRef();
    }
    addDiagnostic(info.getMessage(mdoRef, mdoName));
  }
}
