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
import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.VariableFlowAnalyzer.Facts;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReceiverReadFlowTest {
  private final ReturnedLocalFlow projections = mock(ReturnedLocalFlow.class);
  private final ReceiverReadFlow reads = new ReceiverReadFlow(mock(VariableFlowAnalyzer.class), projections);
  private final DocumentContext document = mock(DocumentContext.class);
  private final BSLParser.CodeBlockContext body = mock(BSLParser.CodeBlockContext.class);
  private final VariableSymbol parameter = mock(VariableSymbol.class);
  private final FlowLayout layout = mock(FlowLayout.class);
  private final FlowInputs inputs = mock(FlowInputs.class);
  private final ForeachLoopVertex firstLoop = mock(ForeachLoopVertex.class);
  private final ForeachLoopVertex secondLoop = mock(ForeachLoopVertex.class);
  private final ParserRuleContext firstHeader = mock(ParserRuleContext.class);
  private final ParserRuleContext secondHeader = mock(ParserRuleContext.class);
  private final AtomicReference<TypeSet> declaration =
    new AtomicReference<>(TypeSet.of(new TypeRef(TypeKind.PRIMITIVE, "Строка")));
  private final AtomicReference<Optional<Set<ExecutionContext>>> contexts =
    new AtomicReference<>(Optional.of(Set.of(ExecutionContext.SERVER)));

  @BeforeEach
  void setUp() {
    when(document.getFileType()).thenReturn(FileType.BSL);
    when(document.getUri()).thenReturn(URI.create("file:///module.bsl"));
    when(parameter.getKind()).thenReturn(VariableKind.PARAMETER);
    when(inputs.externalDeclaration()).thenReturn(ignored -> false);
    when(inputs.sawMissing()).thenReturn(() -> false);
    when(inputs.declaredFact()).thenReturn(ignored -> declaration.get());
    when(inputs.compilationContexts()).thenReturn(ignored -> contexts.get());
    when(inputs.executionContext()).thenReturn(ExecutionContext.SERVER);
    when(layout.vertexOf(firstHeader)).thenReturn(firstLoop);
    when(layout.vertexOf(secondHeader)).thenReturn(secondLoop);
  }

  @Test
  void reusesTheSameHeaderAndFallsBackForAnotherLoop() {
    var facts = mock(Facts.class);
    when(projections.readParameterBeforeLoop(eq(document), eq(layout), any(), eq(firstLoop), eq(inputs), any(), any())).thenReturn(facts);

    assertThat(read(firstHeader, () -> true)).isSameAs(facts);
    assertThat(read(firstHeader, () -> true)).isSameAs(facts);
    assertThat(read(secondHeader, () -> true)).isNull();

    verify(projections).readParameterBeforeLoop(eq(document), eq(layout), any(), eq(firstLoop), eq(inputs), any(), any());
    verify(projections, never()).readParameterBeforeLoop(eq(document), eq(layout), any(), eq(secondLoop), eq(inputs), any(), any());
  }

  @Test
  void refreshesTheDeclarationAndSeparatesCompilationModesAndDocuments() {
    var server = mock(Facts.class);
    var changed = mock(Facts.class);
    var expanded = mock(Facts.class);
    var client = mock(Facts.class);
    when(projections.readParameterBeforeLoop(eq(document), eq(layout), any(), eq(firstLoop), eq(inputs), any(), any()))
      .thenReturn(server, changed, expanded);
    when(projections.readParameterBeforeLoop(eq(document), eq(layout), any(), eq(secondLoop), eq(inputs), any(), any())).thenReturn(client);

    assertThat(read(firstHeader, () -> true)).isSameAs(server);
    declaration.set(TypeSet.of(new TypeRef(TypeKind.PRIMITIVE, "Число")));
    assertThat(read(firstHeader, () -> true)).isSameAs(changed);
    contexts.set(Optional.of(Set.of(ExecutionContext.SERVER, ExecutionContext.THIN_CLIENT)));
    assertThat(read(firstHeader, () -> true)).isSameAs(expanded);

    when(inputs.executionContext()).thenReturn(ExecutionContext.THIN_CLIENT);
    assertThat(read(secondHeader, () -> true)).isSameAs(client);

    when(inputs.executionContext()).thenReturn(ExecutionContext.SERVER);
    reads.clear(document.getUri());
    assertThat(read(secondHeader, () -> true)).isSameAs(client);
  }

  @Test
  void doesNotCacheAnIncompleteProjection() {
    var partial = mock(Facts.class);
    var complete = mock(Facts.class);
    when(projections.readParameterBeforeLoop(eq(document), eq(layout), any(), eq(firstLoop), eq(inputs), any(), any()))
      .thenReturn(partial, complete);

    assertThat(read(firstHeader, () -> false)).isSameAs(partial);
    assertThat(read(firstHeader, () -> true)).isSameAs(complete);
    assertThat(read(firstHeader, () -> true)).isSameAs(complete);

    verify(projections, times(2)).readParameterBeforeLoop(eq(document), eq(layout), any(), eq(firstLoop), eq(inputs), any(), any());
  }

  private @Nullable Facts read(ParserRuleContext header, BooleanSupplier complete) {
    return reads.projectedReadFacts(document, body, layout, inputs, complete, parameter, header);
  }
}
