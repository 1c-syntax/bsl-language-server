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

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.mdo.WebService;
import com.github._1c_syntax.bsl.mdo.children.WebServiceOperation;
import com.github._1c_syntax.bsl.types.ModuleType;
import org.eclipse.lsp4j.Range;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.CRITICAL,
  minutesToFix = 10,
  tags = {
    DiagnosticTag.SUSPICIOUS,
    DiagnosticTag.ERROR
  },
  scope = DiagnosticScope.BSL,
  modules = {
    ModuleType.WEBServiceModule
  }

)
public class WrongWebServiceHandlerDiagnostic extends AbstractDiagnostic {

  private Range diagnosticRange;

  @Override
  protected void check() {

    //todo может ли не быть модуля web-сервиса? тогда непонятно, на какой модуль вешать замечания

    diagnosticRange = documentContext.getSymbolTree().getModule().getSelectionRange();
    if (!Ranges.isEmpty(diagnosticRange)) {
      processModule();
    }
  }

  private void processModule() {
    documentContext.getMdObject()
      .filter(WebService.class::isInstance)
      .map(WebService.class::cast)
      .ifPresent(this::checkService);
  }

  private void checkService(WebService mdWebService) {
    mdWebService.getOperations()
      .forEach(webServiceOperation -> checkOperation(mdWebService.getName(), webServiceOperation));
  }

  private void checkOperation(String serviceName, WebServiceOperation webServiceOperation) {
    final var operationName = webServiceOperation.getName();
    final var handler = webServiceOperation.getProcedureName();
    if (handler.isEmpty()) {
      addMissingHandlerDiagnostic(serviceName, operationName);
      return;
    }
    checkMethod(serviceName, operationName, handler);
  }

  private void checkMethod(String serviceName, String operationName, String handlerName) {
    if (documentContext.getSymbolTree().getMethodSymbol(handlerName).isEmpty()) {
      addDefaultDiagnostic(serviceName, operationName, handlerName);
    }
  }

  private void addDefaultDiagnostic(String serviceName, String operationName, String handlerName) {
    diagnosticStorage.addDiagnostic(
      diagnosticRange,
      info.getMessage(handlerName, operationName, serviceName));
  }

  private void addMissingHandlerDiagnostic(String serviceName, String operationName) {
    diagnosticStorage.addDiagnostic(
      diagnosticRange,
      info.getResourceString("missingHandler", operationName, serviceName));
  }
}
