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

import com.github._1c_syntax.bsl.languageserver.cfg.ForeachLoopVertex;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.ReturnedLocalFlow.Key;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.VariableFlowAnalyzer.Facts;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.VariableFlowAnalyzer.FlowSession;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;


/** Узкие проекции при чтении получателя и их кеш по документу. */
final class ReceiverReadFlow {
  /** Тип объявленного параметра проверяется отдельно от неизменного графа. */
  private record LocalReadFacts(@Nullable TypeSet declared, @Nullable Facts facts) {
    boolean matches(@Nullable TypeSet current) {
      if (current == null) {
        return declared == null;
      }
      return declared != null && declared.equals(current)
        && List.copyOf(declared.refs()).equals(List.copyOf(current.refs()));
    }
  }

  private final VariableFlowAnalyzer analyzer;
  private final ReturnedLocalFlow projections;
  /** Окружения независимого локального значения по AST и точному режиму компиляции. */
  private final Map<URI, Map<Key, LocalReadFacts>> localReadFactsByUri = new ConcurrentHashMap<>();

  ReceiverReadFlow(VariableFlowAnalyzer analyzer, ReturnedLocalFlow projections) {
    this.analyzer = analyzer;
    this.projections = projections;
  }

  void clear(URI uri) {
    localReadFactsByUri.remove(uri);
  }

  void clear() {
    localReadFactsByUri.clear();
  }

  static boolean ordinaryRead(FlowLayout layout, ParserRuleContext statement, @Nullable ParserRuleContext use,
                              FlowInputs inputs) {
    return inputs.session().contextTracking && isReceiverUse(use) && !layout.isCondition(statement);
  }

  private static boolean isReceiverUse(@Nullable ParserRuleContext use) {
    return use instanceof BSLParser.ComplexIdentifierContext || use instanceof BSLParser.CallStatementContext
      || (use instanceof BSLParser.LValueContext target && target.acceptor() != null);
  }

  /** Пустая структура задаёт вход следующего оператора той же базовой вершины. */
  @Nullable TypeSet afterStructureReset(DocumentContext document, BSLParser.CodeBlockContext body, FlowLayout layout,
    ParserRuleContext use, VariableSymbol variable, FlowInputs inputs, BooleanSupplier complete) {
    if (!canReadModuleReset(document, body, inputs, complete)) {
      return null;
    }
    if (!(layout.previousInBasicBlock(use) instanceof BSLParser.AssignmentContext assignment)
      || !IndependentReturnedStructure.resetsToEmptyStructure(document, assignment, variable.getName())) {
      return null;
    }
    var variables = analyzer.variablesOf(document, body, layout, inputs);
    var change = variables.changesByStatement().getOrDefault(assignment, List.of()).stream()
      .filter(candidate -> candidate.definition() && candidate.variable() == variable).findFirst().orElse(null);
    if (change == null || !isCompiled(body, layout, use, inputs)) {
      return null;
    }
    return inputs.assigned().at(variable, assignment, change.position());
  }

  private boolean canReadModuleReset(DocumentContext document, BSLParser.CodeBlockContext body,
                                     FlowInputs inputs, BooleanSupplier complete) {
    if (document.getFileType() != FileType.BSL || !inputs.cacheable() || inputs.session().computing()) {
      return false;
    }
    return !inputs.sawMissing().getAsBoolean() && complete.getAsBoolean()
      && analyzer.cachedFacts(document.getUri(), body, inputs) == null;
  }

  private static boolean isCompiled(BSLParser.CodeBlockContext body, FlowLayout layout,
                                    ParserRuleContext use, FlowInputs inputs) {
    var contexts = inputs.compilationContexts().apply(body);
    if (contexts.isEmpty()) {
      return true;
    }
    var vertex = layout.vertexOf(use);
    return vertex != null && layout.bslReachableVertices(contexts.orElseThrow()).contains(vertex);
  }

  @Nullable Facts projectedReadFacts(DocumentContext document, BSLParser.CodeBlockContext body, FlowLayout layout,
    FlowInputs inputs, BooleanSupplier complete, VariableSymbol variable, @Nullable ParserRuleContext readAt) {
    var vertex = readAt == null ? null : layout.vertexOf(readAt);
    var whole = independentReadFacts(document, body, layout, inputs, complete, variable,
      vertex instanceof ForeachLoopVertex);
    if (whole != null || !(vertex instanceof ForeachLoopVertex loop)) {
      return whole;
    }
    if (!isBslParameter(document, variable) || inputs.externalDeclaration().test(variable)
      || inputs.sawMissing().getAsBoolean()) {
      return null;
    }
    var declared = inputs.declaredFact().apply(variable);
    if (inputs.sawMissing().getAsBoolean() || hasDeferredTypes(declared)) {
      return null;
    }
    // Срез обслуживает одну точку. Обычный кеш хранит факты всего тела.
    var key = new Key(body, variable, inputs.executionContext());
    return projections.readParameterBeforeLoop(document, layout, key, loop, inputs);
  }

  private @Nullable Facts independentReadFacts(DocumentContext document, BSLParser.CodeBlockContext body,
    FlowLayout layout, FlowInputs inputs, BooleanSupplier complete, VariableSymbol variable, boolean foreachRead) {
    var parameter = isBslParameter(document, variable);
    if (!independentReadInput(document, variable, inputs, parameter)) {
      return null;
    }
    // Готовая проекция параметра обслуживает все заголовки цикла в одном теле.
    if (!(parameter && foreachRead) && !analyzer.variablesOf(document, body, layout, inputs).hasVisibleModuleVariables()) {
      return null;
    }
    var key = new Key(body, variable, inputs.executionContext());
    var byLocal = localReadFactsByUri.computeIfAbsent(document.getUri(), ignored -> new ConcurrentHashMap<>());
    return readLocalProjection(document, layout, inputs, complete, key, byLocal, parameter);
  }

  private @Nullable Facts readLocalProjection(DocumentContext document, FlowLayout layout, FlowInputs inputs,
    BooleanSupplier complete, Key key, Map<Key, LocalReadFacts> byLocal, boolean parameter) {
    var existing = byLocal.get(key);
    if (existing != null && existing.facts() == null) {
      return null;
    }
    var declared = parameter ? inputs.declaredFact().apply(key.variable()) : null;
    if (parameter && inputs.sawMissing().getAsBoolean()) {
      return null;
    }
    if (existing != null && existing.matches(declared)) {
      return existing.facts();
    }
    if (declared != null && hasDeferredTypes(declared)) {
      return null;
    }
    return computeIndependentRead(document, layout, inputs, complete, key, byLocal, declared);
  }

  private @Nullable Facts computeIndependentRead(DocumentContext document, FlowLayout layout, FlowInputs inputs,
    BooleanSupplier complete, Key key, Map<Key, LocalReadFacts> byLocal, @Nullable TypeSet declared) {
    var facts = projections.readFacts(document, layout, key, inputs);
    if (complete.getAsBoolean() && !inputs.sawMissing().getAsBoolean()) {
      byLocal.put(key, new LocalReadFacts(declared, facts));
    }
    return facts;
  }

  private static boolean hasDeferredTypes(TypeSet declared) {
    return FlowSession.hasDeferredType(declared, Collections.newSetFromMap(new IdentityHashMap<>()));
  }

  private static boolean isBslParameter(DocumentContext document, VariableSymbol variable) {
    return document.getFileType() == FileType.BSL && variable.getKind() == VariableKind.PARAMETER;
  }

  private static boolean independentReadInput(DocumentContext document, VariableSymbol variable,
                                              FlowInputs inputs, boolean parameter) {
    return parameter ? !inputs.externalDeclaration().test(variable)
      : (IndependentReturnedStructure.localVariable(document, variable) && variable.getDescription().isEmpty());
  }

}
