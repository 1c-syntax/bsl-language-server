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
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.Map;
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
  @SuppressWarnings("java:S6411") // Ключ содержит символ конкретной версии; порядок символов не определён.
  final Map<ContextualReturnKey, TypeSet> contextualReturns;
  final Set<SourceDefinedSymbol> visited;
  /**
   * Тип, накопленный к текущему моменту для символа, инференс которого ещё не
   * завершён. Self-reference (например, {@code Строка = Строка + "..."}) резолвится
   * в это частичное значение вместо {@link TypeSet#EMPTY}, что даёт one-pass
   * фикс-точку по присваиваниям вместо потери типа на guard'е циклов (#4205).
   */
  @SuppressWarnings("java:S6411") // Приближения адресуются символами текущего запроса, без их упорядочивания.
  final Map<SourceDefinedSymbol, TypeSet> inProgress;
  /**
   * Расчёты по потоку, идущие прямо сейчас в рамках этого вывода. Вывод типа
   * присваивания просит типы переменных из правой части, и если они из того же тела,
   * запрос приходит посреди его же расчёта — тогда он читает строящееся окружение,
   * а не запускает расчёт тела заново.
   */
  final VariableFlowAnalyzer.FlowSession flowSession = new VariableFlowAnalyzer.FlowSession();
  /**
   * Документы, чьё содержимое участвовало в расчёте: их правка делает
   * закэшированный результат недействительным.
   */
  final Set<URI> dependencies;
  /** Методы, типы возврата которых понадобились расчёту. */
  final Set<MethodSymbol> consulted = new HashSet<>();
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
  /**
   * Расчёт опирался на то, что ещё не посчитано или не разобрано: значение чужого метода,
   * член либо объявление, ведущие в модуль, до которого очередь ещё не дошла.
   */
  boolean sawMissing;
  int depth;

  InferenceContext(DocumentContext documentContext) {
    this(documentContext, null);
  }

  InferenceContext(DocumentContext documentContext, @Nullable ExecutionContext executionContext) {
    this(documentContext, executionContext, executionContext == null ? Map.of() : new HashMap<>());
  }

  @SuppressWarnings("java:S6411") // Общая карта сохраняет те же символьные ключи во вложенных запросах.
  InferenceContext(DocumentContext documentContext, @Nullable ExecutionContext executionContext,
                   Map<ContextualReturnKey, TypeSet> contextualReturns) {
    this.documentContext = documentContext;
    this.executionContext = executionContext;
    this.contextualReturns = contextualReturns;
    this.visited = new HashSet<>();
    this.inProgress = new HashMap<>();
    this.dependencies = new HashSet<>();
  }
}
