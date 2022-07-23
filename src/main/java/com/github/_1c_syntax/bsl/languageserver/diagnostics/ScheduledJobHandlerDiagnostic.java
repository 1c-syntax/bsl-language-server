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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.types.MDOType;
import com.github._1c_syntax.bsl.types.ModuleType;
import com.github._1c_syntax.mdclasses.mdo.AbstractMDObjectBase;
import com.github._1c_syntax.mdclasses.mdo.MDCommonModule;
import com.github._1c_syntax.mdclasses.mdo.MDScheduledJob;

import java.util.List;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.BLOCKER,
  minutesToFix = 5,
  tags = {
    DiagnosticTag.ERROR
  },
  scope = DiagnosticScope.BSL

)
public class ScheduledJobHandlerDiagnostic extends AbstractMetadataDiagnostic {

  public ScheduledJobHandlerDiagnostic() {
    super(List.of(MDOType.SCHEDULED_JOB));
  }

  @Override
  protected void checkMetadata(AbstractMDObjectBase mdo) {
    final var scheduleJob = (MDScheduledJob) mdo;
    final var handler = scheduleJob.getHandler();
    if (handler.isEmpty()) {
      addDiagnostic(scheduleJob);
      return;
    }

    final var moduleName = handler.getModuleName();

    final var commonModuleOptional = documentContext.getServerContext().getConfiguration().getCommonModule(moduleName);
    if (commonModuleOptional.isEmpty()){
      addDiagnostic("missingModule", scheduleJob, moduleName);
      return;
    }
    final var mdCommonModule = commonModuleOptional.orElseThrow();
    if (!mdCommonModule.isServer()){
      addDiagnostic("nonServerModule", scheduleJob, moduleName);
      return;
    }
    checkMethod(scheduleJob, mdCommonModule, handler.getMethodName());
  }

  private void checkMethod(MDScheduledJob scheduleJob, MDCommonModule mdCommonModule, String methodName) {
    documentContext.getServerContext().getDocument(
      mdCommonModule.getMdoReference().getMdoRef(), ModuleType.CommonModule)
      .ifPresent((DocumentContext commonModuleContext) -> {
        var method = commonModuleContext.getSymbolTree().getMethods().stream()
          .filter(methodSymbol -> methodSymbol.getName().equalsIgnoreCase(methodName))
          .findFirst();
        if (method.isEmpty()) {
          addDiagnostic("diagnosticMessage", scheduleJob, getFullName(mdCommonModule, methodName));
          return;
        }
        if (!method.get().isExport()) {
          addDiagnostic("nonExportMethod", scheduleJob, getFullName(mdCommonModule, methodName));
        }
        // TODO проверить, что у метода есть реализация и он не пустой
        // TODO проверить отсутствие параметров у метода-обработчика
        // TODO проверить дубли обработчиков у разных регл.заданий
      });
  }

  private static String getFullName(MDCommonModule mdCommonModule, String methodName) {
    return mdCommonModule.getName().concat(".").concat(methodName);
  }

  private void addDiagnostic(String messageString, MDScheduledJob scheduleJob, String text) {
    addDiagnostic(info.getResourceString(messageString, text, scheduleJob.getName()));
  }

  private void addDiagnostic(MDScheduledJob scheduleJob) {
    addDiagnostic(info.getMessage("", scheduleJob.getName()));
  }
}
