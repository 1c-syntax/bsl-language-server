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

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.AopTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@CleanupContextBeforeClassAndAfterClass
class ModeIndependentFlowCacheTest extends AbstractServerContextAwareTest {
  private static final TypeSet NUMBER = TypeSet.of(new TypeRef(TypeKind.PRIMITIVE, "Number"));
  private static final TypeSet STRING = TypeSet.of(new TypeRef(TypeKind.PRIMITIVE, "String"));
  private static final String SOURCE = """
    Procedure Probe()
      Value = 1;
      Result = Value;
    EndProcedure
    """;
  @Autowired private VariableFlowAnalyzer analyzer;
  private VariableFlowAnalyzer target;
  private record Fixture(DocumentContext document, List<VariableSymbol> variables,
                         VariableSymbol value, ParserRuleContext use) { }

  @Test
  void completeLocalCalculationIsReusedByAnotherTrackedContext() throws Exception {
    var fixture = fixture(SOURCE, "Value");
    var calls = new AtomicInteger();
    assertThat(query(fixture, ExecutionContext.SERVER, calls, () -> NUMBER, true, false, true, true)).isEqualTo(NUMBER);
    var first = calls.get();
    assertThat(first).isPositive();
    assertThat(query(fixture, ExecutionContext.THIN_CLIENT, calls, () -> NUMBER, true, false, true, true)).isEqualTo(NUMBER);
    assertThat(calls.get()).as("the completed body needs no second assignment evaluation").isEqualTo(first);
  }

  @Test
  void opaqueCallbacksKeepTheirContextSpecificValues() throws Exception {
    assertSeparate(SOURCE, "Value", false, false);
  }

  @Test
  void observedContextDependentValueIsNeverShared() throws Exception {
    assertSeparate(SOURCE, "Value", true, true);
  }

  @Test
  void incompleteCalculationCannotSupplyAnotherContext() throws Exception {
    var fixture = fixture(SOURCE, "Value");
    var calls = new AtomicInteger();
    query(fixture, ExecutionContext.SERVER, calls, () -> NUMBER, true, false, false, true);
    var first = calls.get();
    assertThat(query(fixture, ExecutionContext.THIN_CLIENT, calls, () -> STRING, true, false, true, true)).isEqualTo(STRING);
    assertThat(calls.get()).isGreaterThan(first);
  }

  @Test
  void parameterEntryFactsKeepContextsSeparateEvenWhenTheParameterIsUnused() throws Exception {
    assertSeparate(SOURCE.replace("Probe()", "Probe(Input)"), "Value", true, false);
  }

  @Test
  void moduleCellsKeepTheirContextSpecificEntryValues() throws Exception {
    assertSeparate("""
      Var Shared;
      Procedure Probe()
        Result = Shared;
      EndProcedure
      """, "Shared", true, false);
  }

  @Test
  void preprocessorBranchesNeverUseAnotherModesBodyFacts() throws Exception {
    assertSeparate("""
      Procedure Probe()
        #If Server Then
          Value = 1;
        #Else
          Value = "";
        #EndIf
        Result = Value;
      EndProcedure
      """, "Value", true, false);
  }

  @Test
  void anExcludedBodyDoesNotReadAValidModesFacts() throws Exception {
    var fixture = fixture(SOURCE, "Value");
    var calls = new AtomicInteger();
    assertThat(query(fixture, ExecutionContext.SERVER, calls, () -> NUMBER, true, false, true, true)).isEqualTo(NUMBER);
    assertThat(query(fixture, ExecutionContext.THIN_CLIENT, calls, () -> NUMBER, true, false, true, false))
      .isNotEqualTo(NUMBER);
  }

  @Test
  void anOldPublisherCannotReplaceAFreshGeneration() throws Exception {
    var fixture = fixture(SOURCE, "Value");
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var oldCalls = new AtomicInteger();
    var oldInputs = inputs(fixture, ExecutionContext.SERVER, oldCalls, () -> {
      if (oldCalls.get() == 1) {
        entered.countDown();
        try {
          if (!release.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("Old producer was not released");
        } catch (InterruptedException error) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException(error);
        }
      }
      return NUMBER;
    }, true, false, true);
    var failure = new AtomicReference<Throwable>();
    var workspace = context.getWorkspaceUri();
    var old = new Thread(() -> {
      try (var scope = WorkspaceContextHolder.forUri(workspace, "old-independent-flow")) {
        target.typeAtRoot(fixture.document(), fixture.use(), fixture.value(), oldInputs, () -> true);
      } catch (Throwable error) {
        failure.set(error);
      }
    });
    try {
      old.start();
      assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
      target.clear(fixture.document().getUri());
      var freshCalls = new AtomicInteger();
      assertThat(query(fixture, ExecutionContext.THIN_CLIENT, freshCalls, () -> STRING, true, false, true, true))
        .isEqualTo(STRING);
      release.countDown();
      old.join(20_000);
      assertThat(old.isAlive()).isFalse();
      assertThat(failure.get()).isNull();
      var afterFresh = freshCalls.get();
      assertThat(query(fixture, ExecutionContext.WEB_CLIENT, freshCalls, () -> STRING, true, false, true, true))
        .isEqualTo(STRING);
      assertThat(freshCalls.get()).as("the old completion must leave the fresh result reusable").isEqualTo(afterFresh);
    } finally {
      release.countDown();
      old.join(20_000);
    }
  }

  private void assertSeparate(String source, String name, boolean tracking, boolean sensitive) throws Exception {
    var fixture = fixture(source, name);
    var calls = new AtomicInteger();
    assertThat(query(fixture, ExecutionContext.SERVER, calls, () -> NUMBER, tracking, sensitive, true, true)).isEqualTo(NUMBER);
    var first = calls.get();
    assertThat(query(fixture, ExecutionContext.THIN_CLIENT, calls, () -> STRING, tracking, sensitive, true, true)).isEqualTo(STRING);
    assertThat(calls.get()).isGreaterThan(first);
  }

  private TypeSet query(Fixture fixture, ExecutionContext mode, AtomicInteger calls, Supplier<TypeSet> value,
                        boolean tracking, boolean sensitive, boolean complete, boolean reachable) {
    return target.typeAtRoot(fixture.document(), fixture.use(), fixture.value(),
      inputs(fixture, mode, calls, value, tracking, sensitive, reachable), () -> complete);
  }

  private VariableFlowAnalyzer.FlowInputs inputs(Fixture fixture, ExecutionContext mode, AtomicInteger calls,
      Supplier<TypeSet> value, boolean tracking, boolean sensitive, boolean reachable) {
    var session = new VariableFlowAnalyzer.FlowSession();
    if (tracking) target.startContextTracking(session);
    return new VariableFlowAnalyzer.FlowInputs(session, body -> {}, true, mode,
      body -> Optional.of(reachable ? Set.of(mode) : Set.of()), () -> false, body -> fixture.variables(),
      variable -> variable.getKind() == VariableKind.MODULE,
      variable -> variable.getKind() == VariableKind.MODULE ? value.get() : TypeSet.EMPTY,
      variable -> definitions(fixture.document(), variable), variable -> List.of(),
      (variable, statement, position) -> {
        calls.incrementAndGet();
        session.contextSensitive |= sensitive;
        return value.get();
      }, (variable, position, incoming) -> incoming, mock(VariableFlowAnalyzer.GuardNarrowing.class));
  }

  private Fixture fixture(String source, String name) {
    initServerContext();
    var document = TestUtils.getDocumentContext(source, context);
    var tree = document.getSymbolTree();
    var method = tree.getMethodSymbol("Probe").orElseThrow();
    var variables = new ArrayList<>(tree.getVariablesOfScope(method));
    tree.getVariablesOfScope(tree.getModule()).stream().filter(variable -> variable.getKind() == VariableKind.MODULE)
      .forEach(variables::add);
    var value = variables.stream().filter(variable -> variable.getName().equalsIgnoreCase(name)).findFirst().orElseThrow();
    var offset = source.lastIndexOf("Result = " + name) + "Result = ".length() + 1;
    var position = new Position((int) source.substring(0, offset).chars().filter(character -> character == '\n').count(),
      offset - source.lastIndexOf('\n', offset - 1) - 1);
    var use = (ParserRuleContext) Trees.findTerminalNodeContainsPosition(document.getAst(), position).orElseThrow().getParent();
    target = AopTestUtils.getUltimateTargetObject(analyzer);
    target.clear(document.getUri());
    return new Fixture(document, variables, value, use);
  }

  private static List<Position> definitions(DocumentContext document, VariableSymbol variable) {
    return Trees.findAllRuleNodes(document.getAst(), BSLParser.RULE_assignment).stream()
      .map(BSLParser.AssignmentContext.class::cast)
      .filter(assignment -> assignment.lValue().IDENTIFIER() != null
        && assignment.lValue().IDENTIFIER().getText().equalsIgnoreCase(variable.getName()))
      .map(assignment -> new Position(assignment.lValue().IDENTIFIER().getSymbol().getLine() - 1,
        assignment.lValue().IDENTIFIER().getSymbol().getCharPositionInLine())).toList();
  }
}
