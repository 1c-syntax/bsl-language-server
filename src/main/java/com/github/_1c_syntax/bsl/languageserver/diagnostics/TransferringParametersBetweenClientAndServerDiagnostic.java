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
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.eclipse.lsp4j.SymbolKind;

import java.util.Collection;
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
  // TODO учесть подходящие типы модулей - только клиентские, только серверные или все?
)

@RequiredArgsConstructor
public class TransferringParametersBetweenClientAndServerDiagnostic extends AbstractDiagnostic {
  private static final Set<CompilerDirectiveKind> SERVER_COMPILER_DIRECTIVE_KINDS = EnumSet.of(
    CompilerDirectiveKind.AT_SERVER,
//    CompilerDirectiveKind.AT_CLIENT_AT_SERVER_NO_CONTEXT,
    CompilerDirectiveKind.AT_SERVER_NO_CONTEXT
  );
  private final ReferenceIndex referenceIndex;

  // Не учитываются вложенные вызовы. Только прямые - клиентский метод вызывает серверный метод напрямую

  @Override
  protected void check() {
    getMethodParamsStream()
      // сначала получаю вызовы из клиентских методов, а уже потом проверяю использование параметров внутри метода,
      // чтобы исключить лишний анализ серверных методов, которые вызываются из серверных методов
      .map(pair -> Triple.of(pair.getLeft(),
        pair.getRight(),
        getRefCalls(pair.getLeft())))
      .filter(triple -> !triple.getRight().isEmpty())
      .map(triple -> Triple.of(triple.getLeft(),
        notAssignedParams(triple.getLeft(), triple.getMiddle()),
        triple.getRight()))
      .forEach(triple -> triple.getMiddle().forEach(parameterDefinition ->
        diagnosticStorage.addDiagnostic(parameterDefinition.getRange(),
          info.getMessage(parameterDefinition.getName(), triple.getLeft().getName())))
      ); // TODO добавить места вызовов как связанную информацию
  }

  private Stream<Pair<MethodSymbol, List<ParameterDefinition>>> getMethodParamsStream() {
    return documentContext.getSymbolTree().getMethods().stream()
      .filter(methodSymbol -> isEqualCompilerDirective(methodSymbol, SERVER_COMPILER_DIRECTIVE_KINDS))
      .map(methodSymbol -> Pair.of(methodSymbol,
        methodSymbol.getParameters().stream()
          .filter(parameterDefinition -> !parameterDefinition.isByValue())
          .collect(Collectors.toUnmodifiableList())))
      .filter(pair -> !pair.getRight().isEmpty());
  }

  private List<Reference> getRefCalls(MethodSymbol methodSymbol) {
    return referenceIndex.getReferencesTo(methodSymbol).stream()
      // в будущем могут появиться и другие виды ссылок
      .filter(ref -> ref.getOccurrenceType() == OccurrenceType.REFERENCE)
      .filter(TransferringParametersBetweenClientAndServerDiagnostic::isClientCall)
      .collect(Collectors.toUnmodifiableList());
  }

  private static boolean isClientCall(Reference ref) {
    // TODO учесть возможность вызова из клиентского модуля, в котором не нужны\не указаны директивы компиляции
    return Optional.of(ref.getFrom())
      .filter(MethodSymbol.class::isInstance)
      .map(MethodSymbol.class::cast)
      .filter(methodSymbol -> isEqualCompilerDirective(methodSymbol, CompilerDirectiveKind.AT_CLIENT))
      .isPresent();
  }

  private List<ParameterDefinition> notAssignedParams(MethodSymbol method, List<ParameterDefinition> parameterDefinitions) {
    return parameterDefinitions.stream()
      .filter(parameterDefinition -> nonAssignedParam(method, parameterDefinition))
      .collect(Collectors.toUnmodifiableList());
  }

  private boolean nonAssignedParam(MethodSymbol method, ParameterDefinition parameterDefinition) {
    return getVariableByParameter(method, parameterDefinition)
      .anyMatch(variableSymbol -> referenceIndex.getReferencesTo(variableSymbol).stream()
        .noneMatch(ref -> ref.getOccurrenceType() == OccurrenceType.DEFINITION));
  }

  private static Stream<VariableSymbol> getVariableByParameter(MethodSymbol method, ParameterDefinition parameterDefinition) {
    return method.getChildren().stream()
      // в будущем могут появиться и другие символы, подчиненные методам
      .filter(sourceDefinedSymbol -> sourceDefinedSymbol.getSymbolKind() == SymbolKind.Variable)
      .filter(variable -> parameterDefinition.getRange().getStart().equals(variable.getSelectionRange().getStart()))
      .filter(VariableSymbol.class::isInstance)
      .map(VariableSymbol.class::cast)
      .findFirst().stream();
  }

  private static boolean isEqualCompilerDirective(MethodSymbol method, Collection<CompilerDirectiveKind> compilerDirectiveKinds) {
    return method.getCompilerDirectiveKind()
      .filter(compilerDirectiveKinds::contains)
      .isPresent();
  }

  private static boolean isEqualCompilerDirective(MethodSymbol method, CompilerDirectiveKind compilerDirectiveKind) {
    return method.getCompilerDirectiveKind()
      .filter(compilerDirective -> compilerDirective == compilerDirectiveKind)
      .isPresent();
  }
}
