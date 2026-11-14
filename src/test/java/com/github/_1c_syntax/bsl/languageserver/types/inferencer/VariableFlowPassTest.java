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
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.VariableFlowAnalyzer.FlowInputs;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.VariableFlowAnalyzer.FlowSession;
import com.github._1c_syntax.bsl.languageserver.types.model.LazyTypeSet;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.sun.management.ThreadMXBean;
import org.antlr.v4.runtime.CommonToken;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class VariableFlowPassTest {
  private final VariableFlowAnalyzer analyzer = mock(VariableFlowAnalyzer.class);
  private final DocumentContext document = mock(DocumentContext.class);
  private final ControlFlowGraph graph = emptyGraph();
  private final FlowLayout layout = emptyLayout(graph);

  @Test
  void preservesDeclaredAndSharedFactsIncludingLazyAndEmptyDecorations() {
    var number = TypeSet.of(new TypeRef(TypeKind.PRIMITIVE, "Number"));
    var structure = new TypeRef(TypeKind.PLATFORM, "Structure");
    var array = new TypeRef(TypeKind.PLATFORM, "Array");
    var forced = new AtomicInteger();
    var values = List.of(TypeSet.EMPTY, new TypeSet(Set.of()), number,
      TypeSet.of(structure).withField(structure, "Count", number),
      TypeSet.of(array).withLazyElement(array, new LazyTypeSet("numbers", () -> {
        forced.incrementAndGet();
        return number;
      })),
      new TypeSet(Set.of(), Map.of(array, number), Map.of(), Map.of(), Map.of(), Map.of()),
      TypeSet.of(TypeRef.UNDEFINED));
    var variables = variables(values.size());
    var shared = variables.getLast();
    var reads = new ArrayList<VariableSymbol>();
    var declared = new IdentityHashMap<VariableSymbol, TypeSet>();
    for (var i = 0; i < variables.size() - 1; i++) {
      declared.put(variables.get(i), values.get(i));
    }
    var inputs = inputs(variables, variable -> variable == shared, variable -> {
      reads.add(variable);
      return declared.get(variable);
    });
    doAnswer(call -> {
      reads.add(shared);
      return values.getLast();
    }).when(analyzer).cellOf(same(document), same(shared), same(inputs));
    var pass = pass(variables, inputs);

    var entry = pass.computeFacts().leaving();
    assertThat(entry.types()).containsExactlyElementsOf(values);
    assertThat(entry.get(1)).isSameAs(TypeSet.EMPTY);
    assertThat(entry.get(5)).isSameAs(values.get(5));
    assertThat(pass.computeFacts().leaving()).isSameAs(entry);
    assertThat(reads).containsExactlyElementsOf(variables);
    assertThat(forced).hasValue(0);
  }

  @Test
  void retriesAllEntryFactsAfterCallbackFailure() {
    var variables = variables(3);
    var reads = new ArrayList<VariableSymbol>();
    var failures = new AtomicInteger();
    var number = TypeSet.of(new TypeRef(TypeKind.PRIMITIVE, "Number"));
    var pass = pass(variables, inputs(variables, variable -> false, variable -> {
      reads.add(variable);
      if (variable == variables.get(1) && failures.getAndIncrement() == 0) {
        throw new IllegalStateException("Entry fact unavailable");
      }
      return number;
    }));

    assertThatThrownBy(pass::computeFacts).isInstanceOf(IllegalStateException.class)
      .hasMessage("Entry fact unavailable");
    assertThat(pass.computeFacts().leaving().types()).containsExactly(number, number, number);
    assertThat(reads).containsExactly(variables.get(0), variables.get(1),
      variables.get(0), variables.get(1), variables.get(2));
  }

  @Test
  void initializingManyDeclaredVariablesAllocatesLinearSpace() {
    var management = ManagementFactory.getThreadMXBean();
    assumeTrue(management instanceof ThreadMXBean);
    var allocations = (ThreadMXBean) management;
    assumeTrue(allocations.isThreadAllocatedMemorySupported() && allocations.isThreadAllocatedMemoryEnabled());
    var variables = variables(1024);
    var number = TypeSet.of(new TypeRef(TypeKind.PRIMITIVE, "Number"));
    var inputs = inputs(variables, variable -> false, variable -> number);
    for (var warmup = 0; warmup < 5; warmup++) {
      pass(variables, inputs).computeFacts();
    }
    var pass = pass(variables, inputs);
    var thread = Thread.currentThread().threadId();

    var before = allocations.getThreadAllocatedBytes(thread);
    var entry = pass.computeFacts().leaving();
    var allocated = allocations.getThreadAllocatedBytes(thread) - before;

    assertThat(entry.types()).hasSize(variables.size()).containsOnly(number);
    // A generous per-variable budget includes graph traversal and the facts container.
    // Repeated array copies require several MiB for this body, independently of timing.
    assertThat(allocated).isBetween(0L, variables.size() * 128L);
  }

  private VariableFlowPass pass(List<VariableSymbol> variables, FlowInputs inputs) {
    var indexes = new IdentityHashMap<VariableSymbol, Integer>();
    for (var i = 0; i < variables.size(); i++) {
      indexes.put(variables.get(i), i);
    }
    return new VariableFlowPass(analyzer, document, graph, layout, inputs, null,
      new FlowVariables(variables, indexes, Map.of()));
  }

  private static List<VariableSymbol> variables(int count) {
    return IntStream.range(0, count).mapToObj(index -> mock(VariableSymbol.class)).toList();
  }

  private static ControlFlowGraph emptyGraph() {
    var graph = new ControlFlowGraph();
    graph.setEntryPoint(graph.getExitPoint());
    return graph;
  }

  private static FlowLayout emptyLayout(ControlFlowGraph graph) {
    var body = new BSLParser.CodeBlockContext(null, 0);
    var token = new CommonToken(0, "");
    token.setLine(1);
    body.start = token;
    body.stop = token;
    return FlowLayout.of(graph, body);
  }

  private static FlowInputs inputs(List<VariableSymbol> variables, Predicate<VariableSymbol> shared,
                                   Function<VariableSymbol, TypeSet> declared) {
    return new FlowInputs(new FlowSession(), body -> {}, true, null, body -> Optional.empty(), () -> false,
      body -> variables, shared, declared, variable -> List.of(), variable -> List.of(),
      (variable, statement, position) -> TypeSet.EMPTY, (variable, position, incoming) -> incoming,
      mock(VariableFlowAnalyzer.GuardNarrowing.class));
  }
}
