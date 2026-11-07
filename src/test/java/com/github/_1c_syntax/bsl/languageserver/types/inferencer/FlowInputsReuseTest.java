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
import com.github._1c_syntax.bsl.languageserver.types.inferencer.VariableFlowAnalyzer.FlowInputs;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.util.List;
import java.util.ArrayList;

import com.github._1c_syntax.bsl.languageserver.diagnostics.BSLDiagnostic;
import com.github._1c_syntax.bsl.languageserver.diagnostics.DefaultDiagnosticComputer;
import com.github._1c_syntax.bsl.languageserver.infrastructure.ContextPropagatingExecutorService;
import com.github._1c_syntax.bsl.languageserver.types.index.InferredExpressionTypeIndex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@CleanupContextBeforeClassAndAfterClass
class FlowInputsReuseTest extends AbstractServerContextAwareTest {
  @Autowired
  private ExpressionTypeInferencer inferencer;
  private static volatile Object escapedInput;

  @Autowired
  private InferredExpressionTypeIndex expressionIndex;

  @Test
  void oneDiagnosticBatchSharesCompleteDeclarationsAcrossIndependentRoots() throws Exception {
    initServerContext();
    var fixture = fixture();
    var number = TypeSet.of(new TypeRef(TypeKind.PRIMITIVE, "Число"));
    var provider = mock(VariableTypeSource.class);
    when(provider.typesOf(fixture.parameter())).thenReturn(number);
    withSource(provider, () -> {
      assertThat(diagnosticBatch(fixture, () -> {})).containsExactly(new DeclarationRead(number, false),
        new DeclarationRead(number, false));
      verify(provider).typesOf(fixture.parameter());
      verify(provider).isIncomplete(fixture.parameter());
    });
  }

  @Test
  void aNewDiagnosticBatchResolvesDeclarationsAgain() throws Exception {
    initServerContext();
    var fixture = fixture();
    var number = TypeSet.of(new TypeRef(TypeKind.PRIMITIVE, "Число"));
    var string = TypeSet.of(new TypeRef(TypeKind.PRIMITIVE, "Строка"));
    var provider = mock(VariableTypeSource.class);
    when(provider.typesOf(fixture.parameter())).thenReturn(number, string);
    withSource(provider, () -> {
      assertThat(diagnosticBatch(fixture, () -> {})).containsOnly(new DeclarationRead(number, false));
      assertThat(diagnosticBatch(fixture, () -> {})).containsOnly(new DeclarationRead(string, false));
      verify(provider, times(2)).typesOf(fixture.parameter());
    });
  }

  @Test
  void incompleteDeclarationsAreRetriedByTheNextDiagnostic() throws Exception {
    initServerContext();
    var fixture = fixture();
    var number = TypeSet.of(new TypeRef(TypeKind.PRIMITIVE, "Число"));
    var provider = mock(VariableTypeSource.class);
    when(provider.typesOf(fixture.parameter())).thenReturn(TypeSet.EMPTY, number);
    when(provider.isIncomplete(fixture.parameter())).thenReturn(true, false);
    withSource(provider, () -> {
      assertThat(diagnosticBatch(fixture, () -> {})).containsExactly(new DeclarationRead(TypeSet.EMPTY, true),
        new DeclarationRead(number, false));
      verify(provider, times(2)).typesOf(fixture.parameter());
      verify(provider, times(2)).isIncomplete(fixture.parameter());
    });
  }

  @Test
  void changedInputsRejectTheEarlierDiagnosticDeclaration() throws Exception {
    initServerContext();
    var fixture = fixture();
    var number = TypeSet.of(new TypeRef(TypeKind.PRIMITIVE, "Число"));
    var string = TypeSet.of(new TypeRef(TypeKind.PRIMITIVE, "Строка"));
    var provider = mock(VariableTypeSource.class);
    when(provider.typesOf(fixture.parameter())).thenReturn(number, string);
    withSource(provider, () -> {
      var reads = diagnosticBatch(fixture, () -> expressionIndex.clear(fixture.document().getUri()));
      assertThat(reads).containsExactly(new DeclarationRead(number, false), new DeclarationRead(string, false));
      verify(provider, times(2)).typesOf(fixture.parameter());
    });
  }

  @Test
  void diagnosticDeclarationSharingIsBypassedBeforePopulation() throws Exception {
    initServerContext();
    var fixture = fixture();
    var number = TypeSet.of(new TypeRef(TypeKind.PRIMITIVE, "Число"));
    var string = TypeSet.of(new TypeRef(TypeKind.PRIMITIVE, "Строка"));
    var provider = mock(VariableTypeSource.class);
    when(provider.typesOf(fixture.parameter())).thenReturn(number, string);
    withSource(provider, () -> {
      ExpressionTypeInferencer target = AopTestUtils.getUltimateTargetObject(inferencer);
      ReflectionTestUtils.setField(target, "workspacePopulated", false);
      assertThat(diagnosticBatch(fixture, () -> {})).containsExactly(new DeclarationRead(number, false),
        new DeclarationRead(string, false));
      verify(provider, times(2)).typesOf(fixture.parameter());
    });
  }

  private record DeclarationRead(TypeSet types, boolean incomplete) { }

  private List<DeclarationRead> diagnosticBatch(Fixture fixture, Runnable beforeSecond) {
    var executor = new ContextPropagatingExecutorService(java.util.concurrent.Executors.newSingleThreadExecutor(
      Thread.ofPlatform().daemon().factory()));
    var reads = new ArrayList<DeclarationRead>();
    var checks = new ArrayList<BSLDiagnostic>();
    var modes = List.of(ExecutionContext.SERVER, ExecutionContext.THIN_CLIENT);
    for (var index = 0; index < modes.size(); index++) {
      var mode = modes.get(index);
      var second = index == 1;
      var diagnostic = mock(BSLDiagnostic.class);
      when(diagnostic.getDiagnostics(fixture.document())).thenAnswer(call -> {
        if (second) beforeSecond.run();
        var root = new InferenceContext(fixture.document(), mode);
        var value = inputs(fixture.parameter(), root).declaredFact().apply(fixture.parameter());
        reads.add(new DeclarationRead(value, root.sawMissing));
        return List.of();
      });
      checks.add(diagnostic);
    }
    var computer = new DefaultDiagnosticComputer(context.getLanguageServerConfiguration(), executor) {
      @Override
      protected List<BSLDiagnostic> diagnostics(DocumentContext document) {
        return checks;
      }
    };
    try {
      computer.compute(fixture.document());
      return reads;
    } finally {
      executor.shutdownNow();
    }
  }

  private void withSource(VariableTypeSource provider, CheckedRunnable action) throws Exception {
    ExpressionTypeInferencer target = AopTestUtils.getUltimateTargetObject(inferencer);
    var sources = ReflectionTestUtils.getField(target, "variableTypeSources");
    var populated = ReflectionTestUtils.getField(target, "workspacePopulated");
    ReflectionTestUtils.setField(target, "variableTypeSources", List.of(provider));
    ReflectionTestUtils.setField(target, "workspacePopulated", true);
    try {
      action.run();
    } finally {
      ReflectionTestUtils.setField(target, "variableTypeSources", sources);
      ReflectionTestUtils.setField(target, "workspacePopulated", populated);
    }
  }

  @FunctionalInterface
  private interface CheckedRunnable {
    void run() throws Exception;
  }

  @Test
  void repeatedInputRequestsHaveBoundedAllocation(TestReporter reporter) throws Exception {
    var management = ManagementFactory.getThreadMXBean();
    assumeTrue(management instanceof ThreadMXBean);
    var allocations = (ThreadMXBean) management;
    assumeTrue(allocations.isThreadAllocatedMemorySupported() && allocations.isThreadAllocatedMemoryEnabled());
    var fixture = fixture();
    var ctx = new InferenceContext(fixture.document(), ExecutionContext.SERVER);
    ExpressionTypeInferencer target = AopTestUtils.getUltimateTargetObject(inferencer);
    var factory = factory();
    var arguments = new Object[]{fixture.parameter(), ctx};
    try {
      for (var warmup = 0; warmup < 2000; warmup++) {
        escapedInput = factory.invoke(target, arguments);
      }
      var thread = Thread.currentThread().threadId();
      var before = allocations.getThreadAllocatedBytes(thread);
      for (var read = 0; read < 1000; read++) {
        escapedInput = factory.invoke(target, arguments);
      }
      var allocated = allocations.getThreadAllocatedBytes(thread) - before;
      reporter.publishEntry("flowInputBytesFor1000Reads", Long.toString(allocated));
      assertThat(allocated).isBetween(0L, 64_000L);
    } finally {
      escapedInput = null;
    }
  }

  @Test
  void recursionAndRefinementKeepTheirCacheGuards() throws Exception {
    var fixture = fixture();
    var ctx = new InferenceContext(fixture.document(), ExecutionContext.SERVER);
    assertThat(inputs(fixture.parameter(), ctx).cacheable()).isTrue();
    ctx.visited.add(fixture.parameter());
    assertThat(inputs(fixture.result(), ctx).cacheable()).isTrue();
    ctx.visited.add(fixture.result());
    assertThat(inputs(fixture.parameter(), ctx).cacheable()).isFalse();
    ctx.visited.clear();
    assertThat(inputs(fixture.parameter(), ctx).cacheable()).isTrue();
    ctx.refining = true;
    assertThat(inputs(fixture.parameter(), ctx).cacheable()).isFalse();
    ctx.refining = false;
    assertThat(inputs(fixture.parameter(), ctx).cacheable()).isTrue();
  }

  @Test
  void nestedRequestsBindCallbacksToTheirOwnContext() throws Exception {
    var fixture = fixture();
    var outer = new InferenceContext(fixture.document(), ExecutionContext.SERVER);
    var first = inputs(fixture.parameter(), outer);
    var nested = outer.nested(fixture.document(), ExecutionContext.SERVER);
    nested.sawMissing = true;
    var second = inputs(fixture.parameter(), nested);
    assertThat(second.session()).isSameAs(first.session());
    assertThat(second.sawMissing().getAsBoolean()).isTrue();
    assertThat(first.sawMissing().getAsBoolean()).isFalse();
    var client = outer.nested(fixture.document(), ExecutionContext.THIN_CLIENT);
    var third = inputs(fixture.parameter(), client);
    assertThat(third.executionContext()).isEqualTo(ExecutionContext.THIN_CLIENT);
    assertThat(third.session()).isNotSameAs(first.session());
    var refining = outer.forRefinement();
    refining.refining = true;
    var fourth = inputs(fixture.parameter(), refining);
    assertThat(fourth.cacheable()).isFalse();
    assertThat(fourth.session()).isNotSameAs(first.session());
    assertThat(fourth.sawMissing().getAsBoolean()).isFalse();
  }

  @Test
  void anIncompleteDeclarationIsRetriedUntilItsSourceIsReady() throws Exception {
    var fixture = fixture();
    var ctx = new InferenceContext(fixture.document(), ExecutionContext.SERVER);
    var number = TypeSet.of(new TypeRef(TypeKind.PRIMITIVE, "Число"));
    var provider = mock(VariableTypeSource.class);
    when(provider.typesOf(fixture.parameter())).thenReturn(TypeSet.EMPTY, number);
    when(provider.isIncomplete(fixture.parameter())).thenReturn(true, false);
    ExpressionTypeInferencer target = AopTestUtils.getUltimateTargetObject(inferencer);
    var original = ReflectionTestUtils.getField(target, "variableTypeSources");
    ReflectionTestUtils.setField(target, "variableTypeSources", List.of(provider));
    try {
      assertThat(inputs(fixture.parameter(), ctx).declaredFact().apply(fixture.parameter())).isEqualTo(TypeSet.EMPTY);
      var retried = inputs(fixture.result(), ctx).declaredFact();
      assertThat(retried.apply(fixture.parameter())).isEqualTo(number);
      assertThat(retried.apply(fixture.parameter())).isEqualTo(number);
      assertThat(ctx.sawMissing).isTrue();
      verify(provider, times(2)).typesOf(fixture.parameter());
      verify(provider, times(2)).isIncomplete(fixture.parameter());
    } finally {
      ReflectionTestUtils.setField(target, "variableTypeSources", original);
    }
  }

  private FlowInputs inputs(VariableSymbol variable, InferenceContext ctx) throws Exception {
    ExpressionTypeInferencer target = AopTestUtils.getUltimateTargetObject(inferencer);
    return (FlowInputs) factory().invoke(target, variable, ctx);
  }

  private static Method factory() throws NoSuchMethodException {
    var factory = ExpressionTypeInferencer.class.getDeclaredMethod("flowInputs", VariableSymbol.class, InferenceContext.class);
    factory.setAccessible(true);
    return factory;
  }

  private Fixture fixture() {
    var document = TestUtils.getDocumentContext("""
      Procedure Check(P)
        Result = P;
      EndProcedure
      """, context);
    var variables = document.getSymbolTree().getVariables();
    var parameter = variables.stream().filter(variable -> variable.getName().equals("P")).findFirst().orElseThrow();
    var result = variables.stream().filter(variable -> variable.getName().equals("Result")).findFirst().orElseThrow();
    return new Fixture(document, parameter, result);
  }

  private record Fixture(DocumentContext document, VariableSymbol parameter, VariableSymbol result) { }
}
