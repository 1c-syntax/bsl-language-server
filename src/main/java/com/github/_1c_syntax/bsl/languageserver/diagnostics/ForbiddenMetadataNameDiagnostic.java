/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2023
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
import com.github._1c_syntax.bsl.types.MdoReference;
import com.github._1c_syntax.bsl.types.ModuleType;
import com.github._1c_syntax.mdclasses.mdo.AbstractMDObjectBase;
import com.github._1c_syntax.mdclasses.mdo.AbstractMDObjectComplex;
import com.github._1c_syntax.mdclasses.mdo.attributes.TabularSection;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import lombok.RequiredArgsConstructor;

import java.util.Collection;
import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.BLOCKER,
  minutesToFix = 30,
  tags = {
    DiagnosticTag.STANDARD,
    DiagnosticTag.SQL,
    DiagnosticTag.DESIGN
  },
  modules = {
    ModuleType.ManagerModule,
    ModuleType.ObjectModule,
    ModuleType.ValueManagerModule,
    ModuleType.SessionModule
  },
  scope = DiagnosticScope.BSL
)
@RequiredArgsConstructor
public class ForbiddenMetadataNameDiagnostic extends AbstractMetadataDiagnostic {

  // Запрещенные имена метаданных
  private static final String FORBIDDEN_NAMES =
    "AccountingRegister|" +
      "AccountingRegisters|" +
      "AccumulationRegister|" +
      "AccumulationRegisters|" +
      "BusinessProcess|" +
      "BusinessProcesses|" +
      "CalculationRegister|" +
      "CalculationRegisters|" +
      "Catalog|" +
      "Catalogs|" +
      "ChartOfAccounts|" +
      "ChartOfCalculationTypes|" +
      "ChartOfCharacteristicTypes|" +
      "ChartsOfAccounts|" +
      "ChartsOfCalculationTypes|" +
      "ChartsOfCharacteristicTypes|" +
      "Constant|" +
      "Constants|" +
      "Document|" +
      "DocumentJournal|" +
      "DocumentJournals|" +
      "Documents|" +
      "Enum|" +
      "Enums|" +
      "ExchangePlan|" +
      "ExchangePlans|" +
      "FilterCriteria|" +
      "FilterCriterion|" +
      "InformationRegister|" +
      "InformationRegisters|" +
      "Task|" +
      "Tasks|" +
      "БизнесПроцесс|" +
      "БизнесПроцессы|" +
      "Документ|" +
      "Документы|" +
      "ЖурналДокументов|" +
      "ЖурналыДокументов|" +
      "Задача|" +
      "Задачи|" +
      "Константа|" +
      "Константы|" +
      "КритерииОтбора|" +
      "КритерийОтбора|" +
      "Перечисление|" +
      "Перечисления|" +
      "ПланВидовРасчета|" +
      "ПланВидовХарактеристик|" +
      "ПланОбмена|" +
      "ПланСчетов|" +
      "ПланыВидовРасчета|" +
      "ПланыВидовХарактеристик|" +
      "ПланыОбмена|" +
      "ПланыСчетов|" +
      "РегистрБухгалтерии|" +
      "РегистрНакопления|" +
      "РегистрРасчета|" +
      "РегистрСведений|" +
      "РегистрыБухгалтерии|" +
      "РегистрыНакопления|" +
      "РегистрыРасчета|" +
      "РегистрыСведений|" +
      "Справочник|" +
      "Справочники";

  private static final Pattern FORBIDDEN_NAMES_PATTERN = CaseInsensitivePattern.compile(FORBIDDEN_NAMES);

  private final LanguageServerConfiguration serverConfiguration;

  @Override
  protected void checkMetadata(AbstractMDObjectBase mdo) {

    // проверка имени метаданного
    checkName(mdo.getName(), mdo.getMdoReference());

    if (mdo instanceof AbstractMDObjectComplex) {
      // проверка имен реквизитов и табличных частей
      ((AbstractMDObjectComplex) mdo).getAttributes()
        .forEach(attribute -> checkName(attribute.getName(), attribute.getMdoReference()));

      // проверка имен реквизитов табличных частей
      ((AbstractMDObjectComplex) mdo).getAttributes().stream()
        .filter(TabularSection.class::isInstance)
        .map(TabularSection.class::cast)
        .map(TabularSection::getAttributes)
        .flatMap(Collection::stream)
        .forEach(attribute -> checkName(attribute.getName(), attribute.getMdoReference()));
    }
  }

  private void checkName(String name, MdoReference mdoReference) {
    if (FORBIDDEN_NAMES_PATTERN.matcher(name).matches()) {
      String mdoRef;
      if (serverConfiguration.getLanguage() == Language.RU) {
        mdoRef = mdoReference.getMdoRefRu();
      } else {
        mdoRef = mdoReference.getMdoRef();
      }
      addDiagnostic(info.getMessage(name, mdoRef));
    }
  }
}
