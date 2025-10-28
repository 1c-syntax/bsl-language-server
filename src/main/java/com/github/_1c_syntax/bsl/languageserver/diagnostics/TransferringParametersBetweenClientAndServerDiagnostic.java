/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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

import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.CompilerDirectiveKind;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceIndex;
import com.github._1c_syntax.bsl.languageserver.references.model.OccurrenceType;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.utils.RelatedInformation;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;
import org.eclipse.lsp4j.SymbolKind;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 2,
  tags = {
    DiagnosticTag.BADPRACTICE,
    DiagnosticTag.PERFORMANCE,
    DiagnosticTag.STANDARD
  }
)
@RequiredArgsConstructor
public class TransferringParametersBetweenClientAndServerDiagnostic extends AbstractDiagnostic {
  private static final Set<CompilerDirectiveKind> SERVER_COMPILER_DIRECTIVE_KINDS = EnumSet.of(
    CompilerDirectiveKind.AT_SERVER,
    CompilerDirectiveKind.AT_SERVER_NO_CONTEXT
  );

  private final ReferenceIndex referenceIndex;

  // Не учитываются вложенные вызовы. Только прямые - клиентский метод вызывает серверный метод напрямую

  @Override
  protected void check() {
    calcIssues()
      .forEach(paramReference -> paramReference.parameterDefinitions().forEach(parameterDefinition ->
        diagnosticStorage.addDiagnostic(parameterDefinition.getRange(),
          info.getMessage(parameterDefinition.getName(), paramReference.methodSymbol().getName()),
          getRelatedInformation(paramReference.references())))
      );
  }

  private Stream<ParamReference> calcIssues() {
    return documentContext.getSymbolTree().getMethods().stream()
      .filter(TransferringParametersBetweenClientAndServerDiagnostic::isEqualCompilerDirectives)
      .flatMap(methodSymbol -> getParamReference(methodSymbol).stream());
  }

  private Optional<ParamReference> getParamReference(MethodSymbol method) {
    var parameterDefinitions = calcNotAssignedParams(method);
    if (parameterDefinitions.isEmpty()) {
      return Optional.empty();
    }
    final var refsFromClientCalls = getRefsFromClientCalls(method);
    if (refsFromClientCalls.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new ParamReference(method, parameterDefinitions,
      refsFromClientCalls));
  }

  private List<ParameterDefinition> calcNotAssignedParams(MethodSymbol method) {
    var parameterDefinitions = getMethodParamsByRef(method);
    if (parameterDefinitions.isEmpty()) {
      return Collections.emptyList();
    }
    return calcNotAssignedParams(method, parameterDefinitions);
  }

  private List<ParameterDefinition> calcNotAssignedParams(MethodSymbol method,
                                                          List<ParameterDefinition> parameterDefinitions) {
    return parameterDefinitions.stream()
      .filter(parameterDefinition -> isAssignedParam(method, parameterDefinition))
      .toList();
  }

  private boolean isAssignedParam(MethodSymbol method, ParameterDefinition parameterDefinition) {
    return getVariableByParameter(method, parameterDefinition)
      .noneMatch(variableSymbol -> referenceIndex.getReferencesTo(variableSymbol).stream()
        .anyMatch(ref -> ref.getOccurrenceType() == OccurrenceType.DEFINITION));
  }

  private static Stream<VariableSymbol> getVariableByParameter(MethodSymbol method,
                                                               ParameterDefinition parameterDefinition) {
    return method.getChildren().stream()
      // в будущем могут появиться и другие символы, подчиненные методам
      .filter(sourceDefinedSymbol -> sourceDefinedSymbol.getSymbolKind() == SymbolKind.Variable)
      .filter(variable -> parameterDefinition.getRange().getStart().equals(variable.getSelectionRange().getStart()))
      .filter(VariableSymbol.class::isInstance)
      .map(VariableSymbol.class::cast)
      .findFirst().stream();
  }

  private List<Reference> getRefsFromClientCalls(MethodSymbol method) {
    return referenceIndex.getReferencesTo(method).stream()
      // в будущем могут появиться и другие виды ссылок
      .filter(ref -> ref.getOccurrenceType() == OccurrenceType.REFERENCE)
      .filter(TransferringParametersBetweenClientAndServerDiagnostic::isClientCall)
      .toList();
  }

  private static boolean isClientCall(Reference ref) {
    return Optional.of(ref.getFrom())
      .filter(MethodSymbol.class::isInstance)
      .map(MethodSymbol.class::cast)
      .filter(TransferringParametersBetweenClientAndServerDiagnostic::isEqualCompilerDirective)
      .isPresent();
  }

  private static boolean isEqualCompilerDirectives(MethodSymbol method) {
    return method.getCompilerDirectiveKind()
      .filter(((Collection<CompilerDirectiveKind>) SERVER_COMPILER_DIRECTIVE_KINDS)::contains)
      .isPresent();
  }

  private static boolean isEqualCompilerDirective(MethodSymbol method) {
    return method.getCompilerDirectiveKind()
      .filter(compilerDirective -> compilerDirective == CompilerDirectiveKind.AT_CLIENT)
      .isPresent();
  }

  private static List<ParameterDefinition> getMethodParamsByRef(MethodSymbol methodSymbol) {
    return methodSymbol.getParameters().stream()
      .filter(parameterDefinition -> !parameterDefinition.isByValue())
      .toList();
  }

  private static List<DiagnosticRelatedInformation> getRelatedInformation(List<Reference> references) {
    return references.stream()
      .map(reference -> RelatedInformation.create(reference.getUri(), reference.getSelectionRange(), "+1"))
      .collect(Collectors.toList());
  }

  private record ParamReference(MethodSymbol methodSymbol, List<ParameterDefinition> parameterDefinitions,
                                List<Reference> references) {
  }
}
