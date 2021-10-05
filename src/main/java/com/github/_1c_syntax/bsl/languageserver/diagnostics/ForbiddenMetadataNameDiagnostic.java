/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2021
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

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.mdclasses.mdo.AbstractMDObjectBSL;
import com.github._1c_syntax.mdclasses.mdo.AbstractMDObjectBase;
import com.github._1c_syntax.mdclasses.mdo.AbstractMDObjectComplex;
import com.github._1c_syntax.mdclasses.mdo.attributes.TabularSection;
import com.github._1c_syntax.mdclasses.mdo.support.MDOReference;
import com.github._1c_syntax.mdclasses.mdo.support.MDOType;
import com.github._1c_syntax.mdclasses.mdo.support.ModuleType;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.Range;

import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.BLOCKER,
  minutesToFix = 60,
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
public class ForbiddenMetadataNameDiagnostic extends AbstractDiagnostic {

  private static final List<ModuleType> OBJECT_MODULES = List.of(
    ModuleType.ObjectModule,
    ModuleType.ManagerModule,
    ModuleType.ValueManagerModule);

  private static final List<MDOType> ROOT_MDO_TYPES = List.of(
    MDOType.ACCOUNTING_REGISTER,
    MDOType.ACCUMULATION_REGISTER,
    MDOType.BUSINESS_PROCESS,
    MDOType.CALCULATION_REGISTER,
    MDOType.CATALOG,
    MDOType.CHART_OF_ACCOUNTS,
    MDOType.CHART_OF_CALCULATION_TYPES,
    MDOType.CHART_OF_CHARACTERISTIC_TYPES,
    MDOType.CONSTANT,
    MDOType.DOCUMENT,
    MDOType.DOCUMENT_JOURNAL,
    MDOType.ENUM,
    MDOType.EXCHANGE_PLAN,
    MDOType.FILTER_CRITERION,
    MDOType.INFORMATION_REGISTER,
    MDOType.TASK
  );

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
  private Range diagnosticRange;

  @Override
  protected void check() {
    Ranges.getFirstSignificantTokenRange(documentContext.getTokens())
      .ifPresent(range -> diagnosticRange = range);

    if (diagnosticRange == null) {
      // нет ренджа - нет и диагностик :)
      return;
    }

    if (documentContext.getModuleType() == ModuleType.SessionModule) {
      checkMetadataWithoutModules();
    } else {
      checkMetadataWithModules();
    }
  }

  private void checkMetadataWithModules() {
    documentContext.getMdObject().ifPresent((AbstractMDObjectBase mdo) -> {
      if (mdo instanceof AbstractMDObjectBSL) {
        var modules = ((AbstractMDObjectBSL) mdo).getModules().stream()
          .filter(mdoModule -> OBJECT_MODULES.contains(mdoModule.getModuleType()))
          .collect(Collectors.toList());

        // чтобы не анализировать несколько раз, выберем только один модуль, например модуль менеджера
        if (modules.size() == 1 || documentContext.getModuleType() == ModuleType.ManagerModule) {
          checkMetadata(mdo);
        }
      }
    });
  }

  /**
   * Анализируется только те объекты метаданных, которые либо не имеют модулей вообще, либо в конкретной реализации.
   * Замечания по таким объектам будут повешены на модуль сеанса
   * При фильтрации есть потенциальная проблема, связанная с определением ренджа для размещения замечания:
   * если есть модуль, но в нем нет кода, то он будет отсеян, и замечание не будет диагностировано
   * чтож, жизнь - боль!
   */
  private void checkMetadataWithoutModules() {
    documentContext.getServerContext().getConfiguration().getChildren().stream()
      .filter(mdo -> ROOT_MDO_TYPES.contains(mdo.getType()))
      .filter(mdo -> !(mdo instanceof AbstractMDObjectBSL)
        || (((AbstractMDObjectBSL) mdo).getModules().stream()
        .noneMatch(module -> OBJECT_MODULES.contains(module.getModuleType()))))
      .forEach(this::checkMetadata);
  }

  private void checkMetadata(AbstractMDObjectBase mdo) {

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

  private void checkName(String name, MDOReference mdoReference) {
    if (FORBIDDEN_NAMES_PATTERN.matcher(name).matches()) {
      String mdoRef;
      if (serverConfiguration.getLanguage() == Language.RU) {
        mdoRef = mdoReference.getMdoRefRu();
      } else {
        mdoRef = mdoReference.getMdoRef();
      }
      diagnosticStorage.addDiagnostic(diagnosticRange, info.getMessage(name, mdoRef));
    }
  }
}
