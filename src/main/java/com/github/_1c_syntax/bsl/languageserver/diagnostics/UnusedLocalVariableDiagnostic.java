/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2026
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

import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceIndex;
import com.github._1c_syntax.bsl.languageserver.references.model.OccurrenceType;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.types.ModuleType;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.Range;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Диагностика неиспользуемых локальных и модульных переменных.
 * 
 * Срабатывает, если переменная объявлена, но к ней нет обращений по ссылке.
 * Счётчик цикла {@code Для} не считается неиспользуемым, даже если он не
 * упоминается в теле цикла.
 */
@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.MAJOR,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.BRAINOVERLOAD,
    DiagnosticTag.BADPRACTICE,
    DiagnosticTag.UNUSED
  },
  modules = {
    ModuleType.CommandModule,
    ModuleType.CommonModule,
    ModuleType.ManagerModule,
    ModuleType.ValueManagerModule,
    ModuleType.SessionModule,
    ModuleType.UNKNOWN
  }
)
@RequiredArgsConstructor
public class UnusedLocalVariableDiagnostic extends AbstractDiagnostic {
  private final ReferenceIndex referenceIndex;
  private static final Set<VariableKind> CHECKING_VARIABLE_KINDS = EnumSet.of(
    VariableKind.MODULE,
    VariableKind.LOCAL,
    VariableKind.DYNAMIC
  );

  /**
   * Ищет объявленные, но неиспользуемые переменные модуля и метода.
   */
  @Override
  public void check() {
    Set<Range> forLoopCounterRanges = getForLoopCounterRanges();
    documentContext.getSymbolTree().getVariables().stream()
      .filter(variable -> CHECKING_VARIABLE_KINDS.contains(variable.getKind()))
      .filter(variable -> !variable.isExport())
      .filter(variable -> !isForLoopCounter(variable, forLoopCounterRanges))
      .filter(variable -> referenceIndex.getReferencesTo(variable).stream()
        .filter(ref -> ref.occurrenceType() == OccurrenceType.REFERENCE).findFirst().isEmpty()
      )
      .forEach(variable -> diagnosticStorage.addDiagnostic(
        variable.getSelectionRange(), info.getMessage(variable.getName()))
      );
  }

  /**
   * Возвращает диапазоны имён переменных-счётчиков в конструкциях {@code Для ... По ... Цикл}.
   *
   * @return множество диапазонов идентификаторов счётчиков цикла в текущем модуле
   */
  private Set<Range> getForLoopCounterRanges() {
    return Trees.findAllRuleNodes(documentContext.getAst(), BSLParser.RULE_forStatement).stream()
      .map(BSLParser.ForStatementContext.class::cast)
      .map(BSLParser.ForStatementContext::IDENTIFIER)
      .filter(Objects::nonNull)
      .map(Ranges::create)
      .collect(Collectors.toSet());
  }

  /**
   * Проверяет, что переменная объявлена как счётчик цикла {@code Для}.
   *
   * @param variable               проверяемая переменная
   * @param forLoopCounterRanges   диапазоны имён счётчиков цикла
   * @return {@code true}, если переменная определена в заголовке цикла {@code Для}
   */
  private boolean isForLoopCounter(VariableSymbol variable,
                                   Collection<Range> forLoopCounterRanges) {
    return referenceIndex.getReferencesTo(variable).stream()
      .filter(ref -> ref.occurrenceType() == OccurrenceType.DEFINITION)
      .anyMatch(ref -> forLoopCounterRanges.contains(ref.selectionRange()));
  }
}
