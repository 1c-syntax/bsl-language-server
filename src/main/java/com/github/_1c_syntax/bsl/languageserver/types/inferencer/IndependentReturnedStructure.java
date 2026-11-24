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

import com.github._1c_syntax.bsl.languageserver.cfg.CfgVertex;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.ConstructorCallNode;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.ExpressionNodeType;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.ExpressionTreeBuildingVisitor;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Проверяет, можно ли посчитать возвращаемую переменную без остальных локальных значений тела.
 * Проекция поддерживает построение пустой структуры с независимыми вставками и прямой вызов
 * локальной функции без аргументов. Чтения и изменения проверяются на достижимых участках.
 */
final class IndependentReturnedStructure {
  private static final int INSERT_WITH_VALUE_ARITY = 2;

  private IndependentReturnedStructure() {
  }

  /**
   * @param variable символ переменной.
   * @return относится ли объявленная или динамическая переменная к телу метода.
   */
  static boolean localVariable(VariableSymbol variable) {
    return switch (variable.getKind()) {
      case DYNAMIC, LOCAL -> variable.getScope() instanceof MethodSymbol;
      default -> false;
    };
  }

  /**
   * Проверяет единственное начальное присваивание, независимость изменений и условий,
   * а также отсутствие передачи переменной в аргументах вызовов.
   *
   * @param document документ тела.
   * @param body тело метода.
   * @param variable проверяемая локальная переменная.
   * @param variables раскладка переменных и изменений.
   * @param vertices доступные вершины графа.
   * @param inputs входы текущего режима расчёта.
   * @param reachable проверка достижимости синтаксического узла.
   * @return можно ли включить переменную в сокращённое окружение.
   */
  static boolean supports(DocumentContext document, BSLParser.CodeBlockContext body, VariableSymbol variable,
                          FlowVariables variables, Set<CfgVertex> vertices, VariableFlowAnalyzer.FlowInputs inputs,
                          Predicate<ParserRuleContext> reachable) {
    return variables.indexes().containsKey(variable)
      && closedChanges(document, variable, variables, reachable, !inputs.session().cellsComputing())
      && closedConditions(variable, vertices, inputs) && !escapes(document, body, variable, reachable);
  }

  /**
   * Собирает результаты, для которых можно выполнить один проход с сокращённым окружением.
   *
   * @param document документ тела.
   * @param body тело метода.
   * @param variables раскладка переменных и изменений.
   * @param vertices доступные вершины графа.
   * @param inputs входы текущего режима расчёта.
   * @param required уже проверенный обязательный результат.
   * @param reachable проверка достижимости синтаксического узла.
   * @return обязательная переменная и остальные независимые переменные из операторов возврата.
   */
  static Set<VariableSymbol> returnedLocals(DocumentContext document, BSLParser.CodeBlockContext body,
      FlowVariables variables, Set<CfgVertex> vertices, VariableFlowAnalyzer.FlowInputs inputs, VariableSymbol required,
      Predicate<ParserRuleContext> reachable) {
    Set<VariableSymbol> selected = Collections.newSetFromMap(new IdentityHashMap<>(0));
    selected.add(required);
    for (var node : Trees.findAllRuleNodes(body, BSLParser.RULE_returnStatement)) {
      var candidate = returnedVariable(document, body, (BSLParser.ReturnStatementContext) node, reachable);
      if (candidate != null && !selected.contains(candidate) && localVariable(candidate)
        && supports(document, body, candidate, variables, vertices, inputs, reachable)) {
        selected.add(candidate);
      }
    }
    return selected;
  }

  private static @Nullable VariableSymbol returnedVariable(DocumentContext document, BSLParser.CodeBlockContext body,
      BSLParser.ReturnStatementContext returned, Predicate<ParserRuleContext> reachable) {
    if (!reachable.test(returned)) {
      return null;
    }
    var expression = ExpressionTreeBuildingVisitor.buildExpressionTree(returned.expression());
    if (expression == null || expression.getNodeType() != ExpressionNodeType.IDENTIFIER
      || !(expression.getRepresentingAst() instanceof TerminalNode terminal)) {
      return null;
    }
    return document.getSymbolTreeNoLock().getVariableSymbolInScope(body, terminal.getText()).orElse(null);
  }

  private static boolean closedChanges(DocumentContext document, VariableSymbol variable, FlowVariables variables,
                                       Predicate<ParserRuleContext> reachable, boolean allowLocalCall) {
    var definitions = 0;
    for (var entry : variables.changesByStatement().entrySet()) {
      if (!reachable.test(entry.getKey())) {
        continue;
      }
      for (var change : entry.getValue()) {
        if (change.variable() != variable) {
          continue;
        }
        if (!supportedChange(document, entry.getKey(), change.definition(), variable, variables, reachable,
          allowLocalCall)) {
          return false;
        }
        definitions += change.definition() ? 1 : 0;
      }
    }
    return definitions == 1;
  }

  private static boolean supportedChange(DocumentContext document, ParserRuleContext statement,
                                         boolean definition, VariableSymbol variable, FlowVariables variables,
                                         Predicate<ParserRuleContext> reachable, boolean allowLocalCall) {
    if (definition) {
      return statement instanceof BSLParser.AssignmentContext assignment
        && Trees.getTrailingComment(document.getTokens(), assignment.getStop()).isEmpty()
        && closedInitializer(document, assignment, variable, variables, reachable, allowLocalCall);
    }
    return statement instanceof BSLParser.CallStatementContext call && supportedInsert(call, variable.getName());
  }

  private static boolean closedInitializer(DocumentContext document, BSLParser.AssignmentContext assignment,
                                           VariableSymbol variable, FlowVariables variables,
                                           Predicate<ParserRuleContext> reachable, boolean allowLocalCall) {
    var expression = ExpressionTreeBuildingVisitor.buildExpressionTree(assignment.expression());
    if (expression instanceof ConstructorCallNode constructor) {
      return emptyStructure(constructor);
    }
    return allowLocalCall && expression != null
      && expression.getRepresentingAst() instanceof BSLParser.GlobalMethodCallContext call
      && localCallInitializer(document, call, variable, variables, reachable);
  }

  /**
   * Вызов вычисляется штатно, но не читает локальное окружение вызывающего тела.
   * Применимо только к неизменяемому после присваивания результату и телу без общих ячеек.
   */
  private static boolean localCallInitializer(DocumentContext document, BSLParser.GlobalMethodCallContext call,
                                              VariableSymbol variable, FlowVariables variables,
                                              Predicate<ParserRuleContext> reachable) {
    var arguments = call.doCall().callParamList().callParam();
    if (arguments.size() > 1 || arguments.stream().anyMatch(argument -> argument.expression() != null)
        || variables.hasVisibleModuleVariables()) {
      return false;
    }
    return document.getSymbolTreeNoLock().getMethodSymbol(call.methodName().getText())
        .filter(MethodSymbol::isFunction).isPresent()
      && variables.changesByStatement().entrySet().stream()
        .filter(entry -> reachable.test(entry.getKey()))
        .flatMap(entry -> entry.getValue().stream())
        .noneMatch(change -> change.variable() == variable && !change.definition());
  }

  private static boolean emptyStructure(ConstructorCallNode constructor) {
    if (!constructor.isStaticallyTyped() || !parameterless(constructor)) {
      return false;
    }
    var typeName = constructor.getTypeName().getRepresentingAst();
    return typeName != null && ("Structure".equalsIgnoreCase(typeName.getText())
      || "Структура".equalsIgnoreCase(typeName.getText()));
  }

  private static boolean parameterless(ConstructorCallNode constructor) {
    var arguments = constructor.arguments();
    return arguments.isEmpty() || (arguments.size() == 1
      && arguments.getFirst().getNodeType() == ExpressionNodeType.SKIPPED_CALL_ARG);
  }

  private static boolean closedConditions(VariableSymbol variable, Set<CfgVertex> vertices,
                                           VariableFlowAnalyzer.FlowInputs inputs) {
    for (var vertex : vertices) {
      var condition = FlowConditionNarrowing.conditionOf(vertex);
      if (condition == null) {
        continue;
      }
      var mentioned = inputs.narrowing().variablesOf(condition);
      if (mentioned.contains(variable) && (mentioned.stream().anyMatch(other -> other != variable)
        || !Trees.findAllRuleNodes(condition, BSLParser.RULE_methodCall).isEmpty())) {
        return false;
      }
    }
    return true;
  }

  private static boolean escapes(DocumentContext document, BSLParser.CodeBlockContext body, VariableSymbol variable,
                                 Predicate<ParserRuleContext> reachable) {
    for (var node : Trees.<ParserRuleContext>findAllRuleNodes(body, BSLParser.RULE_callParam)) {
      if (!reachable.test(node)) {
        continue;
      }
      for (var identifier : Trees.findAllTokenNodes(node, BSLParser.IDENTIFIER)) {
        if (identifier.getParent() instanceof BSLParser.ComplexIdentifierContext complex
          && complex.IDENTIFIER() == identifier && identifier.getText().equalsIgnoreCase(variable.getName())
          && document.getSymbolTreeNoLock().getVariableSymbolInScope(body, identifier.getText()).orElse(null) == variable) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean supportedInsert(BSLParser.CallStatementContext call, String variableName) {
    if (call.IDENTIFIER() == null || !call.IDENTIFIER().getText().equalsIgnoreCase(variableName)
      || !call.modifier().isEmpty()) {
      return false;
    }
    var access = call.accessCall();
    var method = access == null ? null : access.methodCall();
    return method != null && supportedInsert(method);
  }

  private static boolean supportedInsert(BSLParser.MethodCallContext method) {
    var name = method.methodName();
    if (name == null || !("Insert".equalsIgnoreCase(name.getText()) || "Вставить".equalsIgnoreCase(name.getText()))) {
      return false;
    }
    var call = method.doCall();
    if (call == null || call.callParamList() == null) {
      return false;
    }
    var params = call.callParamList().callParam();
    // Общий расчёт не добавляет поле по динамическому ключу и не вычисляет его значение.
    return switch (params.size()) {
      case 1 -> stringLiteral(params.getFirst().expression());
      case INSERT_WITH_VALUE_ARITY -> params.getFirst().expression() != null
        && (OpenDataObjectInference.literalKeyOf(params) == null || literal(params.getLast().expression()));
      default -> false;
    };
  }

  private static boolean stringLiteral(BSLParser.@Nullable ExpressionContext expression) {
    return expression != null && expression.getText().startsWith("\"") && literal(expression);
  }

  private static boolean literal(BSLParser.@Nullable ExpressionContext expression) {
    if (expression == null) {
      return false;
    }
    var tree = ExpressionTreeBuildingVisitor.buildExpressionTree(expression);
    return tree != null && tree.getNodeType() == ExpressionNodeType.LITERAL;
  }
}
