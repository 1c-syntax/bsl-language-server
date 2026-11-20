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
package com.github._1c_syntax.bsl.languageserver.types.inferencer;

import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.VariableFlowAnalyzer.AssignedTypes;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.VariableFlowAnalyzer.FlowSession;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.VariableFlowAnalyzer.GuardNarrowing;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.VariableFlowAnalyzer.Mutations;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.parser.BSLParser.CodeBlockContext;
import org.eclipse.lsp4j.Position;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Исходные данные расчёта по телу.
 *
 * @param session             расчёты, идущие прямо сейчас в рамках этого вывода типов.
 * @param activeBodyRead      уведомление о проверке текущего окружения тела, включая
 *                            проверку при отсутствии активного расчёта.
 * @param cacheable           можно ли запоминать результат: вложенный расчёт мог быть
 *                            усечён защитой от циклов, такой результат переиспользовать нельзя.
 * @param executionContext    режим для выбора кеша; {@code null} использует общий результат
 *                            по всем возможным контекстам модуля и метода.
 * @param compilationContexts возможные режимы каждого тела с учётом выбранного контекста,
 *                            модуля, директивы и ветки объявления метода. Пустое множество
 *                            исключает тело; пустой Optional отключает модель контекстов 1С.
 * @param sawMissing          видел ли вывод типов значения, которых ещё нет: пока рабочая
 *                            область наполняется, так бывает с функциями модулей, до которых
 *                            очередь не дошла. Посчитанное на них неполно и в кэши, живущие
 *                            дольше вывода, не попадает: следующий расчёт взял бы оттуда
 *                            неполное значение, не узнав, что оно неполное.
 * @param variables           переменные тела, за типами которых следит расчёт. Зависят
 *                            только от самого тела: окружение считается на всё тело разом
 *                            и переиспользуется всеми запросами, поэтому набор переменных
 *                            не может зависеть от того, про какую из них спросили первой.
 * @param readModuleVariables чтения переменных модуля; при отсутствии сведений все видимые
 *                            переменные консервативно считаются входами расчёта.
 * @param sharedWithOtherBodies видна ли переменная другим телам модуля: тогда её тип
 *                            задаёт не только это тело, и любой вызов мог его сменить.
 * @param declaredFact        что о переменной объявлено помимо кода: типы параметра,
 *                            типизирующий комментарий и прочее, известное до первого
 *                            присваивания. У переменной, видимой другим телам, это лишь
 *                            начальное приближение её ячейки.
 * @param definitionPositions позиции всех присваиваний переменной в документе;
 *                            учитываются только попавшие в это тело.
 * @param mutationPositions   позиции операторов, меняющих тип переменной на месте.
 * @param assigned            колбэк, отдающий присваиваемые типы по позиции.
 * @param mutations           колбэк, применяющий изменение оператора-мутатора.
 * @param narrowing           колбэк, сужающий тип по охраняющему условию ветки.
 * @param noteIncompleteCellInput отметить, что результат опирается на неполный вход ячейки.
 * @param prepareCells        самостоятельный расчёт ячеек при первом обращении во вложенном выводе.
 * @param preparingCells     изолированная подготовка, которой нужны проверенные независимые входы.
 *     <p>
 *     Переменные и места изменений отдаются поставщиками, а не значениями: за ними стоит
 *     обход индекса ссылок и разбор документирующих комментариев. При готовом ответе в
 *     кэше ничего этого не нужно, а тип спрашивают на каждое обращение к переменной.
 */
record FlowInputs(
  FlowSession session, Consumer<CodeBlockContext> activeBodyRead, boolean cacheable,
  @Nullable ExecutionContext executionContext,
  Function<CodeBlockContext, Optional<Set<ExecutionContext>>> compilationContexts,
  BooleanSupplier sawMissing,
  Function<CodeBlockContext, Collection<VariableSymbol>> variables,
  @Nullable Function<CodeBlockContext, Set<VariableSymbol>> readModuleVariables,
  Predicate<VariableSymbol> sharedWithOtherBodies, Function<VariableSymbol, TypeSet> declaredFact,
  Function<VariableSymbol, Collection<Position>> definitionPositions,
  Function<VariableSymbol, Collection<Position>> mutationPositions,
  AssignedTypes assigned, Mutations mutations, GuardNarrowing narrowing, Runnable noteIncompleteCellInput,
  @Nullable Consumer<List<VariableSymbol>> prepareCells, boolean preparingCells
) {
  /** Входы без отдельного списка чтений сохраняют консервативный расчёт всех видимых переменных. */
  public FlowInputs(FlowSession session, Consumer<CodeBlockContext> activeBodyRead, boolean cacheable,
    @Nullable ExecutionContext executionContext,
    Function<CodeBlockContext, Optional<Set<ExecutionContext>>> compilationContexts, BooleanSupplier sawMissing,
    Function<CodeBlockContext, Collection<VariableSymbol>> variables,
    Predicate<VariableSymbol> sharedWithOtherBodies, Function<VariableSymbol, TypeSet> declaredFact,
    Function<VariableSymbol, Collection<Position>> definitionPositions,
    Function<VariableSymbol, Collection<Position>> mutationPositions,
    AssignedTypes assigned, Mutations mutations, GuardNarrowing narrowing) {
    this(session, activeBodyRead, cacheable, executionContext, compilationContexts, sawMissing, variables, null,
      sharedWithOtherBodies, declaredFact, definitionPositions, mutationPositions, assigned, mutations, narrowing,
      () -> {}, null, false);
  }

  public FlowInputs(FlowSession session, Consumer<CodeBlockContext> activeBodyRead, boolean cacheable,
    @Nullable ExecutionContext executionContext,
    Function<CodeBlockContext, Optional<Set<ExecutionContext>>> compilationContexts, BooleanSupplier sawMissing,
    Function<CodeBlockContext, Collection<VariableSymbol>> variables,
    @Nullable Function<CodeBlockContext, Set<VariableSymbol>> readModuleVariables,
    Predicate<VariableSymbol> sharedWithOtherBodies, Function<VariableSymbol, TypeSet> declaredFact,
    Function<VariableSymbol, Collection<Position>> definitionPositions,
    Function<VariableSymbol, Collection<Position>> mutationPositions,
    AssignedTypes assigned, Mutations mutations, GuardNarrowing narrowing, Runnable noteIncompleteCellInput) {
    this(session, activeBodyRead, cacheable, executionContext, compilationContexts, sawMissing, variables,
      readModuleVariables, sharedWithOtherBodies, declaredFact, definitionPositions, mutationPositions, assigned,
      mutations, narrowing, noteIncompleteCellInput, null, false);
  }
}
