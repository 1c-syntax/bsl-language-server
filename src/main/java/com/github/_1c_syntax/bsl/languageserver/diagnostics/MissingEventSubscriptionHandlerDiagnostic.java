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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.mdo.CommonModule;
import com.github._1c_syntax.bsl.mdo.EventSubscription;
import com.github._1c_syntax.bsl.types.ConfigurationSource;
import com.github._1c_syntax.bsl.types.ModuleType;
import org.eclipse.lsp4j.Range;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.BLOCKER,
  minutesToFix = 5,
  tags = {
    DiagnosticTag.ERROR
  },
  scope = DiagnosticScope.BSL,
  modules = {
    // todo переделать, когда появится привязка к объектам метаданных
    ModuleType.SessionModule
  }
)
public class MissingEventSubscriptionHandlerDiagnostic extends AbstractDiagnostic {

  /**
   * Рендж на который будут повешены замечания
   * Костыль, но пока так
   */
  private Range diagnosticRange;

  @Override
  protected void check() {

    var configuration = documentContext.getServerContext().getConfiguration();
    if (configuration.getConfigurationSource() == ConfigurationSource.EMPTY) {
      return;
    }

    diagnosticRange = documentContext.getSymbolTree().getModule().getSelectionRange();
    if (Ranges.isEmpty(diagnosticRange)) {
      return;
    }

    // для анализа выбираются все имеющиеся подписки на события
    configuration.getEventSubscriptions()
      .forEach((EventSubscription eventSubs) -> {
        // проверка на пустой обработчик
        if (eventSubs.getHandler().isEmpty()) {
          addDiagnostic(eventSubs);
          return;
        }

        // правильный обработчик состоит из трех частей:
        //  - CommonModule - тип объекта, всегда постоянный: общий модуль
        //  - Имя - имя модуля
        //  - ИмяМетода - имя метода в модуле

        // если имя метода пустое, то дальше и смотреть нет смысла
        if (eventSubs.getHandler().getMethodName().isEmpty()) {
          addDiagnostic("incorrectHandler", eventSubs, eventSubs.getHandler().getMethodPath());
          return;
        }

        // проверка на существование модуля
        var module = configuration.findCommonModule(eventSubs.getHandler().getModuleName());

        if (module.isEmpty()) {
          addDiagnostic("missingModule", eventSubs, eventSubs.getHandler().getModuleName());
          return;
        }

        var commonModule = module.get();
        // проверка наличия у модуля серверного флага
        if (!commonModule.isServer()) {
          addDiagnostic("shouldBeServer", eventSubs, eventSubs.getHandler().getModuleName());
        }

        // проверка на наличие метода и его экспортности
        checkMethod(eventSubs, eventSubs.getHandler().getMethodName(), commonModule);
      });
  }

  private void checkMethod(EventSubscription eventSubs, String methodName, CommonModule commonModule) {
    documentContext.getServerContext()
      .getDocument(commonModule.getMdoReference().getMdoRef(), ModuleType.CommonModule)
      .ifPresent((DocumentContext commonModuleContext) -> {
        var method = commonModuleContext.getSymbolTree().getMethods().stream()
          .filter(methodSymbol -> methodSymbol.getName().equalsIgnoreCase(methodName))
          .findFirst();
        if (method.isEmpty()) {
          addDiagnostic("missingMethod", eventSubs, commonModule.getName() + "." + methodName);
          return;
        }
        if (!method.get().isExport()) {
          addDiagnostic("nonExportMethod", eventSubs, commonModule.getName() + "." + methodName);
        }
      });
  }

  private void addDiagnostic(String messageString, EventSubscription eventSubs, String text) {
    diagnosticStorage.addDiagnostic(diagnosticRange,
      info.getResourceString(messageString, text, eventSubs.getName()));
  }

  private void addDiagnostic(EventSubscription eventSubs) {
    diagnosticStorage.addDiagnostic(diagnosticRange, info.getMessage(eventSubs.getName()));
  }
}
