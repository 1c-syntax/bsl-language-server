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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.types.index.SymbolTypeIndex.ReturnTypeInputs;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class InferenceContextMissingInputsTest {
  @Test
  void missingMethodReturnsAreMergedWithoutLosingTheirCause() {
    var document = mock(DocumentContext.class);
    var parent = new InferenceContext(document, ExecutionContext.SERVER);
    var child = parent.nested(document, ExecutionContext.SERVER);
    var first = mock(MethodSymbol.class);
    var second = mock(MethodSymbol.class);
    parent.noteMissingMethod(emptyInput(first));
    child.noteMissingMethod(emptyInput(second));

    parent.inheritMissingInputs(child);

    assertThat(parent.sawMissing()).isTrue();
    assertThat(parent.incompleteMethodReturns()).extracting(ReturnTypeInputs::method).containsExactlyInAnyOrder(first, second);
    child.noteMissingData();
    parent.inheritMissingInputs(child);
    assertThat(parent.incompleteMethodReturns()).isNull();
  }

  @Test
  void observingDifferentSourcesForOneMethodDoesNotAllowCycleCompletion() {
    var document = mock(DocumentContext.class);
    var parent = new InferenceContext(document, ExecutionContext.SERVER);
    var child = parent.nested(document, ExecutionContext.SERVER);
    var method = mock(MethodSymbol.class);
    parent.noteMissingMethod(emptyInput(method));
    child.noteMissingMethod(new ReturnTypeInputs(method, TypeSet.EMPTY, TypeSet.of(TypeRef.UNDEFINED)));

    parent.inheritMissingInputs(child);

    assertThat(parent.sawMissing()).isTrue();
    assertThat(parent.incompleteMethodReturns()).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"failed", "depth", "body"})
  void intrinsicCutsCannotBeFinalizedAsAMethodReturnCycle(String reason) {
    var inference = new InferenceContext(mock(DocumentContext.class), ExecutionContext.SERVER);
    inference.noteMissingMethod(emptyInput(mock(MethodSymbol.class)));
    switch (reason) {
      case "failed" -> inference.failed = true;
      case "depth" -> inference.depthCut = true;
      case "body" -> inference.bodyInFlowCut = true;
      default -> throw new IllegalArgumentException(reason);
    }

    assertThat(inference.incompleteMethodReturns()).isNull();
  }

  @Test
  void aFreshRefinementDoesNotInheritPreviousMissingInputs() {
    var inference = new InferenceContext(mock(DocumentContext.class), ExecutionContext.SERVER);
    inference.noteMissingMethod(emptyInput(mock(MethodSymbol.class)));
    inference.noteMissingData();

    var fresh = inference.forRefinement();

    assertThat(fresh.sawMissing()).isFalse();
    assertThat(fresh.incompleteMethodReturns()).isEmpty();
    assertThat(inference.incompleteMethodReturns()).isNull();
  }

  private static ReturnTypeInputs emptyInput(MethodSymbol method) {
    return new ReturnTypeInputs(method, TypeSet.EMPTY, TypeSet.EMPTY);
  }
}
