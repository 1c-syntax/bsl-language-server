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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.types.index.MethodDeclarationKey;
import com.github._1c_syntax.bsl.languageserver.types.index.MethodReturnTypeIndexer.ConfigurationGeneration;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.index.SymbolTypeIndex.ReturnTypeInputs;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Изменяемое состояние одного запроса вывода типов; между потоками не разделяется.
 * <p>
 * Хранит защиту от рекурсии, текущие приближения, зависимости и сессию расчёта потока.
 * При смене документа или режима создаётся вложенный контекст с собственной сессией потока;
 * карта возвращаемых типов передаётся из родительского запроса. Она не переживает запрос
 * и не заменяет общий индекс типов, объединяющий режимы.
 */
final class InferenceContext {
  final DocumentContext documentContext;
  /** Выбранный режим; {@code null} определяет возможные режимы по модулю, методу и позиции выражения. */
  final @Nullable ExecutionContext executionContext;
  /** Общая для вложенных контекстов одного запроса карта завершённых результатов функций. */
  final ContextualReturnCache contextualReturns;
  final CompilationContextMemo compilation;
  final Set<SourceDefinedSymbol> visited;
  /**
   * Тип, накопленный к текущему моменту для символа, инференс которого ещё не
   * завершён. Self-reference (например, {@code Строка = Строка + "..."}) резолвится
   * в это частичное значение вместо {@link TypeSet#EMPTY}, что даёт one-pass
   * фикс-точку по присваиваниям вместо потери типа на guard'е циклов (#4205).
   */
  final Map<SourceDefinedSymbol, TypeSet> inProgress;
  /**
   * Расчёты по потоку, идущие прямо сейчас в рамках этого вывода. Вывод типа
   * присваивания просит типы переменных из правой части, и если они из того же тела,
   * запрос приходит посреди его же расчёта — тогда он читает строящееся окружение,
   * а не запускает расчёт тела заново.
   */
  final VariableFlowAnalyzer.FlowSession flowSession;
  /** Исходные данные потока текущего запроса; признак разрешения кеша проверяется при каждом обращении. */
  VariableFlowAnalyzer.@Nullable FlowInputs flowInputs;
  /**
   * Документы, чьё содержимое участвовало в расчёте: их правка делает
   * закэшированный результат недействительным.
   */
  final Set<URI> dependencies;
  /** Методы, типы возврата которых понадобились расчёту. */
  final Set<MethodSymbol> consulted = new HashSet<>();
  /** Вызовы, отсечённые защитой от повторного входа вместо чтения результата метода. */
  final Set<MethodSymbol> cutMethods = new HashSet<>();
  /** Тела, чьи текущие окружения были проверены при вычислении. */
  final Set<BSLParser.CodeBlockContext> flowBodiesRead = new HashSet<>();
  /** Расчёт упёрся в уже считающийся метод и оборвал цикл. */
  boolean cycleCut;
  /**
   * Расчёт упёрся в метод, тело которого уже считается не ради его значения, и оборвал
   * вход. Как и обрыв цикла, делает результат зависимым от точки входа, но уточняющего
   * прохода не требует.
   */
  boolean bodyInFlowCut;
  /** Идёт уточняющий проход по телу рекурсивной функции с её же приближением. */
  boolean refining;
  /** Отсутствие состояния означает, что расчёт пока не встретил недостающих входов. */
  private @Nullable MissingInputs missingInputs;
  /** Расчёт сорвался либо поток не разместил обращение: пустой ответ нельзя сохранять как полный. */
  boolean failed;
  /** Расчёт оборван ограничением глубины; результат зависит от оставшегося бюджета. */
  boolean depthCut;
  int depth;
  /** Запрос начат публичным выводом выражения; вложенный расчёт потока всё равно не ждёт других владельцев. */
  boolean sharedRootFlow;

  InferenceContext(DocumentContext documentContext, @Nullable ExecutionContext executionContext) {
    this(documentContext, executionContext, new CompilationContextMemo(), null);
  }

  InferenceContext(DocumentContext documentContext, @Nullable ExecutionContext executionContext,
                   CompilationContextMemo compilation) {
    this(documentContext, executionContext, compilation, null);
  }

  InferenceContext(DocumentContext documentContext, @Nullable ExecutionContext executionContext,
                   CompilationContextMemo compilation, @Nullable ConfigurationGeneration returnGeneration) {
    this(documentContext, executionContext, new ContextualReturnCache(returnGeneration), compilation);
  }

  private InferenceContext(DocumentContext documentContext, @Nullable ExecutionContext executionContext,
                           ContextualReturnCache contextualReturns, CompilationContextMemo compilation) {
    this(documentContext, executionContext, contextualReturns, flowSessionFor(contextualReturns), compilation);
  }

  private InferenceContext(DocumentContext documentContext, @Nullable ExecutionContext executionContext,
                           ContextualReturnCache contextualReturns, VariableFlowAnalyzer.FlowSession flowSession,
                           CompilationContextMemo compilation) {
    this.documentContext = documentContext;
    this.executionContext = executionContext;
    this.contextualReturns = contextualReturns;
    this.compilation = compilation;
    this.flowSession = flowSession;
    this.visited = new HashSet<>();
    this.inProgress = new HashMap<>();
    this.dependencies = new HashSet<>();
  }

  private static VariableFlowAnalyzer.FlowSession flowSessionFor(ContextualReturnCache cache) {
    return new VariableFlowAnalyzer.FlowSession(cache::invalidate, cache::noteCellRead);
  }

  /** Недоступны данные, а не только возвращаемое значение известной функции. */
  void noteMissingData() {
    missingInputs().methods = null;
  }

  void noteIncompleteCellInput() {
    missingInputs().incompleteCell = true;
  }

  void noteMissingMethod(ReturnTypeInputs inputs) {
    missingInputs().record(inputs);
  }

  void inheritMissingInputs(InferenceContext nested) {
    if (nested.flowSession.cellsIncomplete()) {
      inheritMissingInputs(null, nested.hasIncompleteCellInput());
    } else if (nested.missingInputs != null) {
      missingInputs().inherit(nested.missingInputs);
    }
  }

  void inheritMissingInputs(@Nullable List<ReturnTypeInputs> missing, boolean incompleteCell) {
    if (missing == null || !missing.isEmpty() || incompleteCell) {
      missingInputs().inherit(missing, incompleteCell);
    }
  }

  /** Снимок недостающих входов сохраняет причину неполноты при повторном чтении результата. */
  @Nullable List<ReturnTypeInputs> missingInputSnapshot() {
    if (flowSession.cellsIncomplete()) {
      return null;
    }

    return missingInputs == null ? List.of() : missingInputs.snapshot();
  }

  boolean sawMissing() {
    return hasIncompleteCellInput() || hasOtherMissingInputs();
  }

  boolean hasIncompleteCellInput() {
    return missingInputs != null && missingInputs.incompleteCell;
  }

  boolean hasOtherMissingInputs() {
    return missingInputs != null && missingInputs.hasMissingMethods();
  }

  private MissingInputs missingInputs() {
    if (missingInputs == null) {
      missingInputs = new MissingInputs();
    }
    return missingInputs;
  }

  void noteIncompleteCells() {
    if (flowSession.cellsIncomplete()) {
      noteMissingData();
    }
  }

  /** Только перечисленные возвращаемые значения мешают завершению; null сохраняет прочую неполноту. */
  @Nullable List<ReturnTypeInputs> incompleteMethodReturns() {
    if (!sawMissing() && !failed) {
      return List.of();
    }
    if (hasIncompleteCellInput() || failed || depthCut || bodyInFlowCut) {
      return null;
    }

    var methods = missingInputSnapshot();
    return methods == null || methods.isEmpty() ? null : methods;
  }

  Optional<MethodSymbol> methodOfBody(BSLParser.CodeBlockContext body) {
    BSLParser.SubContext sub = Trees.getAncestorByRuleIndex(body, BSLParser.RULE_sub);
    return sub == null ? Optional.empty() : documentContext.getSymbolTreeNoLock().getMethodSymbol(sub);
  }

  private static final class MissingInputs {
    /** Недостающие возвраты. {@code null} также допускает другую причину неполноты. */
    private @Nullable Map<MethodDeclarationKey, ReturnTypeInputs> methods;
    private boolean incompleteCell;

    private MissingInputs() {
      methods = new HashMap<>();
    }

    private void record(ReturnTypeInputs inputs) {
      if (methods != null) {
        var previous = methods.putIfAbsent(new MethodDeclarationKey(inputs.method()), inputs);
        if (previous != null && !previous.sameSources(inputs)) {
          methods = null;
        }
      }
    }

    private void inherit(MissingInputs other) {
      inherit(other.methods == null ? null : other.methods.values(), other.incompleteCell);
    }

    private void inherit(@Nullable Collection<ReturnTypeInputs> inherited, boolean incomplete) {
      incompleteCell |= incomplete;
      if (inherited == null) {
        methods = null;
      } else {
        inherited.forEach(this::record);
      }
    }

    private boolean hasMissingMethods() {
      return methods == null || !methods.isEmpty();
    }

    private @Nullable List<ReturnTypeInputs> snapshot() {
      return methods == null ? null : List.copyOf(methods.values());
    }
  }

  /** Запрос без выбора одного режима объединяет результаты доступных контекстов. */
  static InferenceContext neutral(DocumentContext documentContext) {
    return new InferenceContext(documentContext, null);
  }

  /** Новый расчёт приближения с общими метаданными, но без прежних значений типов и потока. */
  InferenceContext forRefinement() {
    return new InferenceContext(documentContext, null, compilation, contextualReturns.returnGeneration);
  }

  /** Состояние одного вложенного расчёта; признаки его неполноты не наследуются от соседних вызовов. */
  InferenceContext nested(DocumentContext owner, @Nullable ExecutionContext target) {
    var session = owner == documentContext && target == executionContext
      ? flowSession : flowSessionFor(contextualReturns);
    var result = new InferenceContext(owner, target, contextualReturns, session, compilation);
    if (session != flowSession) {
      session.contextTracking = flowSession.contextTracking;
      session.contextFreeDeclarations = flowSession.contextFreeDeclarations;
      session.contextSensitive = flowSession.contextSensitive;
      session.contextGeneration = flowSession.contextGeneration;
    }
    result.visited.addAll(visited);
    result.inProgress.putAll(inProgress);
    result.depth = depth;
    result.refining = refining;
    result.sharedRootFlow = sharedRootFlow;
    return result;
  }
}
