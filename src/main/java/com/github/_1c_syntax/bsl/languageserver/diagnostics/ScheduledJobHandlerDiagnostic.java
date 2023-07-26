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
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceIndex;
import com.github._1c_syntax.bsl.mdo.CommonModule;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.mdo.ScheduledJob;
import com.github._1c_syntax.bsl.types.MDOType;
import com.github._1c_syntax.bsl.types.ModuleType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.CRITICAL,
  minutesToFix = 5,
  tags = {
    DiagnosticTag.ERROR
  },
  scope = DiagnosticScope.BSL
)
public class ScheduledJobHandlerDiagnostic extends AbstractMetadataDiagnostic {

  private static final String DIAGNOSTIC_MESSAGE = "diagnosticMessage";
  private static final String MISSING_MODULE_MESSAGE = "missingModule";
  private static final String NON_SERVER_MODULE_MESSAGE = "nonServerModule";
  private static final String NON_EXPORT_METHOD_MESSAGE = "nonExportMethod";
  private static final String METHOD_WITH_PARAMETERS_MESSAGE = "methodWithParameters";
  private static final String EMPTY_METHOD_MESSAGE = "emptyMethod";
  private static final String DOUBLE_MESSAGE = "doubleMessage";

  private final ReferenceIndex referenceIndex;
  private final Map<String, List<ScheduledJob>> scheduledJobHandlers = new HashMap<>();

  public ScheduledJobHandlerDiagnostic(ReferenceIndex referenceIndex) {
    super(List.of(MDOType.SCHEDULED_JOB));
    this.referenceIndex = referenceIndex;
  }

  private static String getFullName(CommonModule commonModule, String methodName) {
    return getFullName(commonModule.getName(), methodName);
  }

  private static String getFullName(String commonModuleName, String methodName) {
    return commonModuleName.concat(".").concat(methodName);
  }

  @Override
  protected void check() {
    super.check();
    checkHandlerDoubles();
  }

  private void checkHandlerDoubles() {
    scheduledJobHandlers.values().stream()
      .filter(mdScheduledJobs -> mdScheduledJobs.size() > 1)
      .map((List<ScheduledJob> mdScheduledJobs) -> {
        mdScheduledJobs.sort(Comparator.comparing(ScheduledJob::getName));
        return mdScheduledJobs;
      })
      .forEach(this::fireIssueForDoubles);
    scheduledJobHandlers.clear();
  }

  private void fireIssueForDoubles(List<ScheduledJob> mdScheduledJobs) {
    final var scheduleJobNames = mdScheduledJobs.stream()
      .map(ScheduledJob::getName)
      .reduce((s, s2) -> s.concat(", ").concat(s2))
      .orElseThrow();
    final var mdScheduledJob = mdScheduledJobs.get(0).getMethodName();
    final var methodPath = getFullName(mdScheduledJob.getModuleName(), mdScheduledJob.getMethodName());

    addDiagnostic(info.getResourceString(DOUBLE_MESSAGE, methodPath, scheduleJobNames));
  }

  @Override
  protected void checkMetadata(MD mdo) {
    final var scheduleJob = (ScheduledJob) mdo;
    final var handler = scheduleJob.getMethodName();
    if (handler.isEmpty()) {
      addDiagnostic(scheduleJob);
      return;
    }

    final var moduleName = handler.getModuleName();

    final var commonModuleOptional = documentContext.getServerContext().getConfiguration()
      .findCommonModule(moduleName);
    if (commonModuleOptional.isEmpty()) {
      addDiagnostic(MISSING_MODULE_MESSAGE, scheduleJob, moduleName);
      return;
    }
    final var mdCommonModule = commonModuleOptional.orElseThrow();
    if (!mdCommonModule.isServer()) {
      addDiagnostic(NON_SERVER_MODULE_MESSAGE, scheduleJob, moduleName);
      return;
    }
    checkMethod(scheduleJob, mdCommonModule, handler.getMethodName());
  }

  private void checkMethod(ScheduledJob scheduleJob, CommonModule mdCommonModule, String methodName) {
    final var fullName = getFullName(mdCommonModule, methodName);
    scheduledJobHandlers.computeIfAbsent(fullName, k -> new ArrayList<>()).add(scheduleJob);

    documentContext.getServerContext().getDocument(
        mdCommonModule.getMdoReference().getMdoRef(), ModuleType.CommonModule)
      .ifPresent((DocumentContext commonModuleContext) -> {
        var method = commonModuleContext.getSymbolTree().getMethods().stream()
          .filter(methodSymbol -> methodSymbol.getName().equalsIgnoreCase(methodName))
          .findFirst();
        if (method.isEmpty()) {
          addDiagnostic(DIAGNOSTIC_MESSAGE, scheduleJob, fullName);
          return;
        }
        method.ifPresent((MethodSymbol methodSymbol) -> checkMethod(scheduleJob, fullName, methodSymbol));
      });
  }

  private void checkMethod(ScheduledJob scheduleJob, String fullName, MethodSymbol methodSymbol) {
    if (!methodSymbol.isExport()) {
      addDiagnostic(NON_EXPORT_METHOD_MESSAGE, scheduleJob, fullName);
    }
    if (scheduleJob.isPredefined() && !methodSymbol.getParameters().isEmpty()) {
      addDiagnostic(METHOD_WITH_PARAMETERS_MESSAGE, scheduleJob, fullName);
    }
    if (isEmptyMethodBody(methodSymbol)) {
      addDiagnostic(EMPTY_METHOD_MESSAGE, scheduleJob, fullName);
    }
  }

  private boolean isEmptyMethodBody(MethodSymbol methodSymbol) {
    // В методе регламентного задания точно будут или переменные или вызов внешнего метода.
    // Если их нет, значит, метод пустой
    if (!methodSymbol.getChildren().isEmpty()) {
      return false;
    }
    return referenceIndex.getReferencesFrom(methodSymbol).isEmpty();
  }

  private void addDiagnostic(String messageString, ScheduledJob scheduleJob, String text) {
    addDiagnostic(info.getResourceString(messageString, text, scheduleJob.getName()));
  }

  private void addDiagnostic(ScheduledJob scheduleJob) {
    addDiagnostic(info.getMessage("", scheduleJob.getName()));
  }
}
