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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.types.ConfigurationSource;
import com.github._1c_syntax.bsl.types.MDOType;
import com.github._1c_syntax.bsl.types.ModuleType;
import com.github._1c_syntax.mdclasses.mdo.MDCommonModule;
import com.github._1c_syntax.mdclasses.mdo.MDEventSubscription;
import org.eclipse.lsp4j.Range;

import java.util.regex.Pattern;

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
  private static final Pattern SPLIT_PATTERN = Pattern.compile("\\.");

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
    configuration.getChildren().stream()
      .filter(mdo -> mdo.getMdoType() == MDOType.EVENT_SUBSCRIPTION)
      .map(MDEventSubscription.class::cast)
      .forEach((MDEventSubscription eventSubs) -> {
        // проверка на пустой обработчик
        if (eventSubs.getHandler().isEmpty()) {
          addDiagnostic(eventSubs);
          return;
        }

        var handlerParts = SPLIT_PATTERN.split(eventSubs.getHandler());

        // правильный обработчик состоит из трех частей:
        //  - CommonModule - тип объекта, всегда постоянный: общий модуль
        //  - Имя - имя модуля
        //  - ИмяМетода - имя метода в модуле

        if (handlerParts.length != 3) {
          addDiagnostic("incorrectHandler", eventSubs, eventSubs.getHandler());
          return;
        }

        // проверка на существование модуля
        var module = configuration.getCommonModule(handlerParts[1]);
        if (module.isEmpty()) {
          addDiagnostic("missingModule", eventSubs, handlerParts[1]);
          return;
        }

        var commonModule = module.get();
        // проверка наличия у модуля серверного флага
        if (!commonModule.isServer()) {
          addDiagnostic("shouldBeServer", eventSubs, handlerParts[1]);
        }

        // проверка на наличие метода и его экспортности
        checkMethod(eventSubs, handlerParts[2], commonModule);
      });
  }

  private void checkMethod(MDEventSubscription eventSubs, String methodName, MDCommonModule commonModule) {
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

  private void addDiagnostic(String messageString, MDEventSubscription eventSubs, String text) {
    diagnosticStorage.addDiagnostic(diagnosticRange,
      info.getResourceString(messageString, text, eventSubs.getName()));
  }

  private void addDiagnostic(MDEventSubscription eventSubs) {
    diagnosticStorage.addDiagnostic(diagnosticRange, info.getMessage(eventSubs.getName()));
  }
}
