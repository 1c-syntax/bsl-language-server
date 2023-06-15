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
import com.github._1c_syntax.bsl.languageserver.references.model.LocationRepository;
import com.github._1c_syntax.bsl.languageserver.references.model.OccurrenceType;
import com.github._1c_syntax.bsl.languageserver.references.model.SymbolOccurrence;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import com.github._1c_syntax.bsl.types.ConfigurationSource;
import com.github._1c_syntax.bsl.types.ModuleType;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;

import java.util.Optional;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.BLOCKER,
  scope = DiagnosticScope.BSL,
  minutesToFix = 5,
  tags = {
    DiagnosticTag.ERROR
  }
)

@RequiredArgsConstructor
public class MissingCommonModuleMethodDiagnostic extends AbstractDiagnostic {
  public static final String PRIVATE_METHOD_MESSAGE = "privateMethod";
  private final LocationRepository locationRepository;

  private static String getMethodNameByLocation(BSLParserRuleContext node, Range range) {
    return Trees.findTerminalNodeContainsPosition(node, range.getEnd())
      .map(ParseTree::getText)
      .orElseThrow();
  }

  @Override
  protected void check() {
    if (documentContext.getServerContext().getConfiguration().getConfigurationSource() == ConfigurationSource.EMPTY){
      return;
    }
    locationRepository.getSymbolOccurrencesByLocationUri(documentContext.getUri())
      .filter(symbolOccurrence -> symbolOccurrence.getOccurrenceType() == OccurrenceType.REFERENCE)
      .filter(symbolOccurrence -> symbolOccurrence.getSymbol().getSymbolKind() == SymbolKind.Method)
      .filter(symbolOccurrence -> symbolOccurrence.getSymbol().getModuleType() == ModuleType.CommonModule)
      .map(this::getReferenceToMethodCall)
      .flatMap(Optional::stream)
      .forEach(this::fireIssue);
  }

  private Optional<CallData> getReferenceToMethodCall(SymbolOccurrence symbolOccurrence) {
    final var symbol = symbolOccurrence.getSymbol();
    final var document = documentContext.getServerContext()
      .getDocument(symbol.getMdoRef(), symbol.getModuleType());
    if (document.isEmpty()) return Optional.empty();
    final var mdObject = document.get().getMdObject();
    if (mdObject.isEmpty()) return Optional.empty();

    // т.к. через refIndex.getReferences нельзя получить приватные методы, приходится обходить символы модуля
    final var methodSymbol = document.get()
      .getSymbolTree().getMethodSymbol(symbol.getSymbolName());
    if (methodSymbol.isEmpty()){
      final var location = symbolOccurrence.getLocation();
      // Нельзя использовать symbol.getSymbolName(), т.к. имя в нижнем регистре
      return Optional.of(
        new CallData(mdObject.get().getName(),
          getMethodNameByLocation(documentContext.getAst(), location.getRange()),
          location.getRange(), false, false));
    }
    // вызовы приватных методов внутри самого модуля пропускаем
    if (document.get().getUri().equals(documentContext.getUri())){
      return Optional.empty();
    }
    return methodSymbol
      .filter(methodSymbol2 -> !methodSymbol2.isExport())
      .map(methodSymbol1 -> new CallData(mdObject.get().getName(),
        methodSymbol1.getName(),
        symbolOccurrence.getLocation().getRange(), true, true));
  }

  private void fireIssue(CallData callData) {
    final String message;
    if (!callData.exists){
      message = info.getMessage(callData.methodName, callData.moduleName);
    } else {
      message = info.getResourceString(PRIVATE_METHOD_MESSAGE, callData.methodName, callData.moduleName);
    }
    diagnosticStorage.addDiagnostic(callData.moduleMethodRange, message);
  }

  @Value
  @AllArgsConstructor
  private static class CallData {
    String moduleName;
    String methodName;
    Range moduleMethodRange;
    boolean nonExport;
    boolean exists;
  }
}
