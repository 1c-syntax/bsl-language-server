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
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import org.antlr.v4.runtime.ParserRuleContext;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.AopTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;

@CleanupContextBeforeClassAndAfterClass
class ModuleCellRootCoalescingTest extends AbstractServerContextAwareTest {
  private static final TypeSet NUMBER = TypeSet.of(new TypeRef(TypeKind.PRIMITIVE, "Number"));
  private static final TypeSet STRING = TypeSet.of(new TypeRef(TypeKind.PRIMITIVE, "String"));
  private static final String SOURCE = """
    Var Shared;
    Procedure Setter()
      Shared = 1;
    EndProcedure
    Procedure Alpha()
      ResultA = Shared;
    EndProcedure
    Procedure Beta()
      ResultB = Shared;
    EndProcedure
    """;

  @Autowired
  private VariableFlowAnalyzer analyzer;

  @Test
  void independentBodiesWaitForOneSharedCellCalculation() throws Exception {
    runConcurrentRoots(ExecutionContext.SERVER, 2, NUMBER, false, false);
  }

  @Test
  void differentCompilationModesKeepSeparateCellCalculations() throws Exception {
    runConcurrentRoots(ExecutionContext.THIN_CLIENT, 4, STRING, false, false);
  }

  @Test
  void failedCellCalculationReleasesTheOtherRootForARetry() throws Exception {
    runConcurrentRoots(ExecutionContext.SERVER, 3, NUMBER, true, false);
  }

  @Test
  void cutRootDoesNotPublishItsIntermediateCellsToAnotherRoot() throws Exception {
    runConcurrentRoots(ExecutionContext.SERVER, 3, NUMBER, false, true);
  }

  private void runConcurrentRoots(ExecutionContext secondMode, int expectedAssignments,
                                  TypeSet expectedSecond, boolean failFirst, boolean partialFirst) throws Exception {
    initServerContext();
    var document = TestUtils.getDocumentContext(SOURCE, context);
    VariableFlowAnalyzer target = AopTestUtils.getUltimateTargetObject(analyzer);
    target.clear(document.getUri());
    var shared = document.getSymbolTree().getVariablesOfScope(document.getSymbolTree().getModule()).stream()
      .filter(variable -> variable.getName().equals("Shared")).findFirst().orElseThrow();
    var firstEntered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var calls = new AtomicInteger();
    var firstResult = new AtomicReference<TypeSet>();
    var secondResult = new AtomicReference<TypeSet>();
    var failure = new AtomicReference<Throwable>();
    var firstComplete = new AtomicBoolean(true);
    var firstInputs = inputs(shared, ExecutionContext.SERVER, calls, firstEntered, release, failFirst,
      partialFirst, () -> firstComplete.set(false));
    var secondInputs = inputs(shared, secondMode, calls, firstEntered, release, failFirst, false, () -> {});
    var firstUse = use(document, 5);
    var secondUse = use(document, 8);
    var workspace = context.getWorkspaceUri();
    var first = new Thread(() -> {
      try (var ignored = WorkspaceContextHolder.forUri(workspace, "first-cell-root")) {
        firstResult.set(target.typeAtRoot(document, firstUse, shared, firstInputs, firstComplete::get));
      } catch (Throwable error) {
        failure.compareAndSet(null, error);
      }
    }, "first-cell-root");
    var second = new Thread(() -> {
      try (var ignored = WorkspaceContextHolder.forUri(workspace, "second-cell-root")) {
        secondResult.set(target.typeAtRoot(document, secondUse, shared, secondInputs, () -> true));
      } catch (Throwable error) {
        failure.compareAndSet(null, error);
      }
    }, "second-cell-root");
    try {
      first.start();
      assertThat(firstEntered.await(10, TimeUnit.SECONDS)).isTrue();
      second.start();
      await().atMost(Duration.ofSeconds(10)).until(() -> blockedOrFinished(first) && blockedOrFinished(second));
    } finally {
      release.countDown();
      first.join(20_000);
      second.join(20_000);
    }
    assertThat(first.isAlive()).isFalse();
    assertThat(second.isAlive()).isFalse();
    if (failFirst) {
      assertThat(failure.get()).isInstanceOf(IllegalStateException.class).hasMessage("Expected first-root failure");
      assertThat(firstResult.get()).isNull();
    } else {
      assertThat(failure.get()).isNull();
      if (partialFirst) {
        assertThat(firstResult.get()).isIn(TypeSet.EMPTY, NUMBER);
      } else {
        assertThat(firstResult.get()).isEqualTo(NUMBER);
      }
    }
    assertThat(secondResult.get()).isEqualTo(expectedSecond);
    assertThat(calls).as("one initial and one confirming pass per independent set of module cells")
      .hasValue(expectedAssignments);
  }

  private static boolean blockedOrFinished(Thread thread) {
    return thread.getState() == Thread.State.WAITING || thread.getState() == Thread.State.TIMED_WAITING
      || thread.getState() == Thread.State.TERMINATED;
  }

  private static ParserRuleContext use(DocumentContext document, int line) {
    return (ParserRuleContext) Trees.findTerminalNodeContainsPosition(document.getAst(), new Position(line, 12))
      .orElseThrow().getParent();
  }

  private static VariableFlowAnalyzer.FlowInputs inputs(VariableSymbol shared, ExecutionContext mode,
      AtomicInteger calls, CountDownLatch firstEntered, CountDownLatch release, boolean failFirst,
      boolean partialRoot, Runnable markIncomplete) {
    return new VariableFlowAnalyzer.FlowInputs(new VariableFlowAnalyzer.FlowSession(), body -> {}, true, mode,
      body -> Optional.empty(), () -> false, body -> List.of(shared), variable -> true, variable -> TypeSet.EMPTY,
      variable -> List.of(new Position(2, 2)), variable -> List.of(),
      (variable, statement, position) -> {
        var call = calls.incrementAndGet();
        firstEntered.countDown();
        try {
          if (!release.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("Cell-root release timed out");
        } catch (InterruptedException error) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException(error);
        }
        if (failFirst && call == 1) throw new IllegalStateException("Expected first-root failure");
        if (partialRoot) {
          markIncomplete.run();
          return TypeSet.EMPTY;
        }
        return mode == ExecutionContext.SERVER ? NUMBER : STRING;
      }, (variable, position, incoming) -> incoming, mock(VariableFlowAnalyzer.GuardNarrowing.class));
  }
}
