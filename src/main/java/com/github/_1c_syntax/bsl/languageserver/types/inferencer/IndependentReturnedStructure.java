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

import com.github._1c_syntax.bsl.languageserver.cfg.ControlFlowGraph;
import com.github._1c_syntax.bsl.languageserver.cfg.PreprocessorFlow;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.ExpressionNodeType;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.ExpressionTreeBuildingVisitor;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.ConstructorCallNode;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Predicate;

/** Достаточные условия независимости локальной структуры, изменяемой только вставками литеральных ключей. */
final class IndependentReturnedStructure {
  private IndependentReturnedStructure() {
  }

  static boolean localVariable(DocumentContext document, VariableSymbol variable) {
    return switch (variable.getKind()) {
      case DYNAMIC, LOCAL -> variable.getScope() instanceof MethodSymbol && document.getFileType() == FileType.BSL;
      default -> false;
    };
  }

  static @Nullable TypeSet typeBefore(VariableFlowAnalyzer.@Nullable Facts facts, ParserRuleContext statement,
                                     VariableSymbol variable) {
    if (facts == null) {
      return null;
    }
    var index = facts.indexOf(variable);
    return index == null ? null : facts.beforeStatement().getOrDefault(statement, FlowEnvironment.EMPTY).get(index);
  }

  static boolean supports(DocumentContext document, BSLParser.CodeBlockContext body, VariableSymbol variable,
                          FlowVariables variables, ControlFlowGraph graph, VariableFlowAnalyzer.FlowInputs inputs) {
    return variables.indexes().containsKey(variable) && closedChanges(document, variable, variables)
      && closedConditions(variable, graph, inputs) && !escapes(document, body, variable);
  }

  /** Собрать замкнутые локальные результаты для одного прохода, не добавляя остальные переменные тела. */
  static Set<VariableSymbol> returnedLocals(DocumentContext document, BSLParser.CodeBlockContext body,
      FlowVariables variables, ControlFlowGraph graph, VariableFlowAnalyzer.FlowInputs inputs, VariableSymbol required,
      Predicate<ParserRuleContext> reachable) {
    Set<VariableSymbol> selected = Collections.newSetFromMap(new IdentityHashMap<>(0));
    selected.add(required);
    for (var node : Trees.findAllRuleNodes(body, BSLParser.RULE_returnStatement)) {
      var candidate = returnedVariable(document, body, (BSLParser.ReturnStatementContext) node, reachable);
      if (candidate != null && !selected.contains(candidate) && localVariable(document, candidate)
        && supports(document, body, candidate, variables, graph, inputs)) {
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

  static boolean reachable(ParserRuleContext statement, FlowLayout layout, @Nullable PreprocessorFlow compiled) {
    var placed = statement;
    while (placed != null && !layout.hasStatement(placed)) {
      placed = placed.getParent();
    }
    return placed != null && (compiled == null || compiled.vertices().contains(layout.vertexOf(placed)));
  }

  private static boolean closedChanges(DocumentContext document, VariableSymbol variable, FlowVariables variables) {
    var definitions = 0;
    for (var entry : variables.changesByStatement().entrySet()) {
      for (var change : entry.getValue()) {
        if (change.variable() != variable) {
          continue;
        }
        if ((change.definition() && !Trees.getTrailingComment(document.getTokens(), entry.getKey().getStop()).isEmpty())
          || !supportedChange(entry.getKey(), change.definition(), variable.getName())) {
          return false;
        }
        definitions += change.definition() ? 1 : 0;
      }
    }
    return definitions == 1;
  }

  private static boolean supportedChange(ParserRuleContext statement,
                                         boolean definition, String variableName) {
    if (definition) {
      return statement instanceof BSLParser.AssignmentContext assignment
        && emptyStructure(assignment);
    }
    return statement instanceof BSLParser.CallStatementContext call && literalInsert(call, variableName);
  }

  private static boolean emptyStructure(BSLParser.AssignmentContext assignment) {
    var expression = ExpressionTreeBuildingVisitor.buildExpressionTree(assignment.expression());
    if (!(expression instanceof ConstructorCallNode constructor)
      || !constructor.isStaticallyTyped() || !parameterless(constructor)) {
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

  private static boolean closedConditions(VariableSymbol variable, ControlFlowGraph graph,
                                           VariableFlowAnalyzer.FlowInputs inputs) {
    for (var vertex : graph.vertexSet()) {
      var condition = FlowConditionNarrowing.conditionOf(vertex);
      if (condition == null) {
        continue;
      }
      var mentioned = inputs.narrowing().variablesOf(condition);
      if (mentioned.contains(variable) && (mentioned.stream().anyMatch(other -> other != variable)
        || !org.antlr.v4.runtime.tree.Trees.findAllRuleNodes(condition, BSLParser.RULE_methodCall).isEmpty())) {
        return false;
      }
    }
    return true;
  }

  private static boolean escapes(DocumentContext document, BSLParser.CodeBlockContext body, VariableSymbol variable) {
    for (var node : org.antlr.v4.runtime.tree.Trees.findAllRuleNodes(body, BSLParser.RULE_callParam)) {
      for (var identifier : org.antlr.v4.runtime.tree.Trees.findAllTokenNodes(node, BSLParser.IDENTIFIER)) {
        if (identifier.getParent() instanceof BSLParser.ComplexIdentifierContext complex
          && complex.IDENTIFIER() == identifier && identifier.getText().equalsIgnoreCase(variable.getName())
          && document.getSymbolTreeNoLock().getVariableSymbolInScope(body, identifier.getText()).orElse(null) == variable) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean literalInsert(BSLParser.CallStatementContext call, String variableName) {
    if (call.IDENTIFIER() == null || !call.IDENTIFIER().getText().equalsIgnoreCase(variableName)
      || !call.modifier().isEmpty()) {
      return false;
    }
    var access = call.accessCall();
    var method = access == null ? null : access.methodCall();
    return method != null && literalInsert(method);
  }

  private static boolean literalInsert(BSLParser.MethodCallContext method) {
    var name = method.methodName();
    if (name == null || !("Insert".equalsIgnoreCase(name.getText()) || "Вставить".equalsIgnoreCase(name.getText()))) {
      return false;
    }
    var call = method.doCall();
    if (call == null || call.callParamList() == null) {
      return false;
    }
    var params = call.callParamList().callParam();
    return params.size() == 1 && stringLiteral(params.getFirst().expression());
  }

  private static boolean stringLiteral(BSLParser.@Nullable ExpressionContext expression) {
    if (expression == null) {
      return false;
    }
    var literal = ExpressionTreeBuildingVisitor.buildExpressionTree(expression);
    return literal != null && literal.getNodeType() == ExpressionNodeType.LITERAL
      && expression.getText().startsWith("\"");
  }
}
