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

import com.github._1c_syntax.bsl.languageserver.cfg.BasicBlockVertex;
import com.github._1c_syntax.bsl.languageserver.cfg.CfgBuildOptions;
import com.github._1c_syntax.bsl.languageserver.cfg.CfgVertex;
import com.github._1c_syntax.bsl.languageserver.cfg.ControlFlowGraphIndex;
import com.github._1c_syntax.bsl.languageserver.cfg.PreprocessorFlow;
import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BslExpression;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.ExpressionTreeBuildingVisitor;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.ParserRuleContext;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Типы возвращаемого значения функции, рассчитанные по её телу.
 * <p>
 * Точки выхода берутся из графа потока управления: в вершину выхода ведут и операторы
 * {@code Возврат}, и достижимый конец тела. Оператор {@code Возврат} даёт типы своего
 * выражения, достижимый конец тела — {@code Неопределено}, потому что функция без явного
 * возврата неявно возвращает именно его. {@code ВызватьИсключение} приходит в ту же
 * вершину, но значения не возвращает и в набор ничего не приносит.
 */
@Component
@RequiredArgsConstructor
public class ReturnTypeFromBodyInference {

  private static final TypeRef UNDEFINED = new TypeRef(TypeKind.PRIMITIVE, "Неопределено");

  private final ControlFlowGraphIndex controlFlowGraphIndex;
  private final CompilationContextResolver compilationContextResolver;

  /** Расчёт типов выражения, которым пользуется разбор точек выхода. */
  @FunctionalInterface
  public interface ExpressionTypes {

    /**
     * Типы выражения.
     *
     * @param expression узел выражения.
     * @return типы; пустой набор, если вывести их не удалось.
     */
    TypeSet of(BslExpression expression);
  }

  /**
   * Типы, которые функция возвращает по своему телу.
   *
   * @param method          метод с доступным AST владельца; у процедуры возвращаемого значения нет.
   * @param expressionTypes расчёт типов выражения — тот же, в рамках которого идёт запрос,
   *                        чтобы работали защита от рекурсии и кэш узлов.
   * @return объединение типов всех точек выхода; {@link TypeSet#EMPTY}, если метод — не
   *     функция либо тело не найдено в текущем AST.
   * @throws com.github._1c_syntax.bsl.languageserver.context.DocumentContentUnavailableException
   *     если для анализа функции требуется уже выгруженный AST.
   */
  public TypeSet of(MethodSymbol method, ExpressionTypes expressionTypes) {
    return of(method, expressionTypes, null);
  }

  /**
   * Вычислить тип результата по достижимым выходам тела в выбранном режиме.
   * <p>
   * Условия препроцессора ограничивают рёбра графа через {@link PreprocessorFlow}.
   * Достижимый конец функции добавляет {@code Неопределено}, выход по исключению — ничего.
   * Доступность самого объявления и переход в его контекст определяет вызывающий код.
   *
   * @param method метод с доступным AST владельца; у процедуры возвращаемого значения нет.
   * @param expressionTypes вывод типов выражений в рамках того же запроса и режима,
   *     с общей защитой от рекурсии.
   * @param context контекст тела или {@code null} для объединения возможных режимов модуля и метода.
   * @return объединение типов выходов; пустой набор для процедуры, отсутствующего тела
   *     или выходов без известного значения. В общий индекс результат не записывается.
   * @throws com.github._1c_syntax.bsl.languageserver.context.DocumentContentUnavailableException
   *     если для анализа функции требуется уже выгруженный AST.
   */
  public TypeSet of(MethodSymbol method, ExpressionTypes expressionTypes, @Nullable ExecutionContext context) {
    if (!method.isFunction()) {
      return TypeSet.EMPTY;
    }
    var owner = method.getOwner();
    var body = VariableFlowAnalyzer.bodyAt(owner, method.getSubNameRange().getStart());
    if (body == null) {
      return TypeSet.EMPTY;
    }
    var graph = controlFlowGraphIndex.graphOf(owner, body, CfgBuildOptions.defaults());
    var compiled = compilationContextResolver.possibleContexts(owner, body)
      .map((Set<ExecutionContext> contexts) -> {
        if (context == null) {
          return contexts;
        }
        return contexts.contains(context) ? Set.of(context) : Set.<ExecutionContext>of();
      })
      .map(contexts -> PreprocessorFlow.of(graph, contexts)).orElse(null);
    var exitPoint = graph.getExitPoint();
    var result = TypeSet.EMPTY;
    for (var edge : graph.incomingEdgesOf(exitPoint)) {
      if (compiled != null && !compiled.edges().contains(edge)) {
        continue;
      }
      result = result.union(typesOfExit(graph.getEdgeSource(edge), expressionTypes));
    }
    return result;
  }

  /**
   * Типы, которые приносит одна точка выхода.
   *
   * @param vertex          вершина, из которой ведёт ребро в вершину выхода.
   * @param expressionTypes расчёт типов выражения.
   * @return типы выражения возврата; {@code Неопределено} у достижимого конца тела;
   *     {@link TypeSet#EMPTY} у выхода по исключению.
   */
  private static TypeSet typesOfExit(CfgVertex vertex, ExpressionTypes expressionTypes) {
    var last = lastStatementOf(vertex);
    if (last instanceof BSLParser.ReturnStatementContext returnStatement) {
      var expression = returnStatement.expression();
      if (expression == null) {
        return TypeSet.EMPTY;
      }
      // Дерево выражения не строится на нераспознанном тексте — тогда и типов нет.
      var tree = ExpressionTreeBuildingVisitor.buildExpressionTree(expression);
      return tree == null ? TypeSet.EMPTY : expressionTypes.of(tree);
    }
    if (last instanceof BSLParser.RaiseStatementContext) {
      return TypeSet.EMPTY;
    }
    return TypeSet.of(UNDEFINED);
  }

  /**
   * Последний оператор вершины.
   *
   * @param vertex вершина графа.
   * @return последний оператор блока; {@code null}, если вершина не блок операторов либо
   *     операторов в ней нет — так выглядит переход в конец тела из ветвления или цикла.
   */
  private static @Nullable ParserRuleContext lastStatementOf(CfgVertex vertex) {
    if (!(vertex instanceof BasicBlockVertex block) || block.statements().isEmpty()) {
      return null;
    }
    return block.statements().getLast();
  }
}
