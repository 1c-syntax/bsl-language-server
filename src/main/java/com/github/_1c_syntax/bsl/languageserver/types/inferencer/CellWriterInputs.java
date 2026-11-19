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

import com.github._1c_syntax.bsl.languageserver.cfg.ForLoopVertex;
import com.github._1c_syntax.bsl.languageserver.cfg.ForeachLoopVertex;
import com.github._1c_syntax.bsl.languageserver.cfg.WhileLoopVertex;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTree;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.VariableFlowAnalyzer.Facts;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.VariableFlowAnalyzer.FlowInputs;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLLexer;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Окружение писателя для текущих выходов ячеек с замыканием по входным переменным. */
record CellWriterInputs(FlowVariables variables, List<VariableSymbol> readCells) {

  static Facts compute(VariableFlowAnalyzer analyzer, DocumentContext document, BSLParser.CodeBlockContext body,
                       List<VariableSymbol> requested, FlowInputs inputs,
                       Map<BSLParser.CodeBlockContext, Optional<CellWriterInputs>> writerInputs) {
    var layout = analyzer.layoutOf(document, body);
    if (inputs.readModuleVariables() == null) {
      return analyzer.compute(document, body, layout, inputs);
    }
    var choice = writerInputs.computeIfAbsent(body, key -> Optional.ofNullable(select(
      document.getSymbolTree(), key, layout, analyzer.variablesOf(document, key, layout, inputs), requested, inputs)));
    if (choice.isEmpty()) {
      return analyzer.compute(document, body, layout, inputs);
    }
    var selected = choice.orElseThrow();
    if (inputs.session().contextTracking) {
      inputs.session().contextSensitive = true;
    }
    analyzer.ensureCellsFor(document, selected.readCells(), inputs, null);
    return analyzer.computeFacts(document, body, layout, inputs, selected.variables());
  }

  private static @Nullable CellWriterInputs select(SymbolTree tree, BSLParser.CodeBlockContext body, FlowLayout layout,
                                           FlowVariables variables, List<VariableSymbol> requested, FlowInputs inputs) {
    var visible = Collections.newSetFromMap(new IdentityHashMap<VariableSymbol, Boolean>());
    visible.addAll(variables.visibleVariables());
    var selected = Collections.newSetFromMap(new IdentityHashMap<VariableSymbol, Boolean>());
    var pending = new ArrayDeque<VariableSymbol>();
    for (var variable : requested) {
      if (visible.contains(variable) && selected.add(variable)) {
        pending.add(variable);
      }
    }
    if (pending.isEmpty()) {
      return null;
    }

    var dependencies = dependencies(layout, variables, inputs);
    var examined = Collections.newSetFromMap(new IdentityHashMap<ParserRuleContext, Boolean>());
    addReferencedVariables(tree, body, controlReads(body, layout), examined, visible, selected, pending);
    while (!pending.isEmpty()) {
      var variable = pending.removeFirst();
      if (!variables.indexes().containsKey(variable)) {
        return null;
      }
      addReferencedVariables(tree, body, dependencies.getOrDefault(variable, List.of()), examined, visible,
        selected, pending);
    }
    if (selected.size() == variables.variables().size()) {
      return null;
    }

    var projected = variables.forVariables(selected);
    var readCells = projected.variables().stream()
      .filter(inputs.sharedWithOtherBodies()).filter(variables.readModuleVariables()::contains).toList();
    return new CellWriterInputs(projected, readCells);
  }

  private static Map<VariableSymbol, List<ParserRuleContext>> dependencies(FlowLayout layout,
    FlowVariables variables, FlowInputs inputs) {
    Map<VariableSymbol, List<ParserRuleContext>> result = new IdentityHashMap<>();
    variables.changesByStatement().forEach((statement, changes) ->
      changes.forEach(change -> result.computeIfAbsent(change.variable(), unused -> new ArrayList<>()).add(statement)));
    for (var vertex : layout.orderedVertices()) {
      var condition = FlowConditionNarrowing.conditionOf(vertex);
      if (condition == null) {
        continue;
      }
      for (var symbol : inputs.narrowing().variablesOf(condition)) {
        if (symbol instanceof VariableSymbol variable) {
          result.computeIfAbsent(variable, unused -> new ArrayList<>()).add(condition);
        }
      }
    }
    return result;
  }

  /** Заголовки циклов и самостоятельные вызовы читают входы при выполнении тела. */
  private static List<ParserRuleContext> controlReads(BSLParser.CodeBlockContext body, FlowLayout layout) {
    var result = new ArrayList<>(Trees.<ParserRuleContext>findAllRuleNodes(body, BSLParser.RULE_callStatement));
    for (var vertex : layout.orderedVertices()) {
      switch (vertex) {
        case ForLoopVertex loop -> result.addAll(loop.getLoopHeader().expression());
        case ForeachLoopVertex loop -> result.add(loop.getLoopHeader().expression());
        case WhileLoopVertex loop -> result.add(loop.getExpression());
        default -> {
          // Входы остальных вершин учитываются по присваиваниям, мутациям и условиям.
        }
      }
    }
    return result;
  }

  /** Учитываются переменные в операторах, включая получателей и индексы. */
  private static void addReferencedVariables(SymbolTree tree, BSLParser.CodeBlockContext body,
    List<ParserRuleContext> statements, Set<ParserRuleContext> examined, Set<VariableSymbol> visible,
    Set<VariableSymbol> selected, ArrayDeque<VariableSymbol> pending) {
    for (var statement : statements) {
      if (!examined.add(statement)) {
        continue;
      }
      for (var token : Trees.findAllTokenNodes(statement, BSLLexer.IDENTIFIER)) {
        if (!FlowVariables.isVariableIdentifier(token)) {
          continue;
        }
        tree.getVariableSymbolInScope(body, token.getText()).filter(visible::contains).filter(selected::add)
          .ifPresent(pending::addLast);
      }
    }
  }
}
