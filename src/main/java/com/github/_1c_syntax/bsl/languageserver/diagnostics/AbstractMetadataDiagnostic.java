/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2022
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

import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.types.MDOType;
import com.github._1c_syntax.bsl.types.ModuleType;
import com.github._1c_syntax.mdclasses.mdo.AbstractMDObjectBSL;
import com.github._1c_syntax.mdclasses.mdo.AbstractMDObjectBase;
import org.eclipse.lsp4j.Range;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Базовый класс для анализа объектов метаданных, когда диагностика региструется на первый токен модуля
 */
public abstract class AbstractMetadataDiagnostic extends AbstractDiagnostic {

  private static final List<ModuleType> OBJECT_MODULES = List.of(
    ModuleType.ObjectModule,
    ModuleType.ManagerModule,
    ModuleType.RecordSetModule,
    ModuleType.ValueManagerModule,
    ModuleType.BotModule,
    ModuleType.CommonModule,
    ModuleType.HTTPServiceModule,
    ModuleType.IntegrationServiceModule,
    ModuleType.WEBServiceModule
  );

  /**
   * Для фильтрации только нужны типов метаданных
   */
  private final List<MDOType> filterMdoTypes;

  /**
   * Область для регистрации замечания
   */
  private Range diagnosticRange;

  protected AbstractMetadataDiagnostic(List<MDOType> types) {
    filterMdoTypes = new ArrayList<>(types);
  }

  protected AbstractMetadataDiagnostic() {
    filterMdoTypes = List.of(
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
  }

  @Override
  protected void check() {
    if (computeDiagnosticRange()) {
      if (documentContext.getModuleType() == ModuleType.SessionModule) {
        checkMetadataWithoutModules();
      } else {
        checkMetadataWithModules();
      }
    }
  }

  protected boolean computeDiagnosticRange() {
    diagnosticRange = documentContext.getSymbolTree().getModule().getSelectionRange();
    return !Ranges.isEmpty(diagnosticRange);
  }

  protected void addDiagnostic(String message) {
    diagnosticStorage.addDiagnostic(diagnosticRange, message);
  }

  protected abstract void checkMetadata(AbstractMDObjectBase mdo);

  private void checkMetadataWithModules() {
    documentContext.getMdObject()
      .filter(mdo -> filterMdoTypes.contains(mdo.getMdoType()))
      .filter(AbstractMDObjectBSL.class::isInstance)
      .filter(this::haveMatchingModule)
      .ifPresent(this::checkMetadata);
  }

  private boolean haveMatchingModule(AbstractMDObjectBase mdo) {
    var modules = ((AbstractMDObjectBSL) mdo).getModules().stream()
      .filter(mdoModule -> OBJECT_MODULES.contains(mdoModule.getModuleType()))
      .collect(Collectors.toList());

    // чтобы не анализировать несколько раз, выберем только один модуль, например модуль менеджера
    return modules.size() == 1 || documentContext.getModuleType() == ModuleType.ManagerModule;
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
      .filter(mdo -> filterMdoTypes.contains(mdo.getMdoType()))
      .filter(mdo -> !(mdo instanceof AbstractMDObjectBSL)
        || (((AbstractMDObjectBSL) mdo).getModules().stream()
        .noneMatch(module -> OBJECT_MODULES.contains(module.getModuleType()))))
      .forEach(this::checkMetadata);
  }
}
