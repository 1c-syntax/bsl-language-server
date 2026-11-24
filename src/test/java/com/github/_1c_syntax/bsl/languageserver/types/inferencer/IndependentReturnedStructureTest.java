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
import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class IndependentReturnedStructureTest extends AbstractServerContextAwareTest {

  @Autowired
  private VariableFlowAnalyzer analyzer;

  @Test
  void selectsIndependentReturnsAndExcludesLocalPassedToAnotherCall() {
    initServerContext();
    var document = TestUtils.getDocumentContext("""
      Function Build(Flag)
        First = New Structure;
        Second = New Structure;
        Escaping = New Structure;
        Alias(Escaping);
        If Flag Then
          Return First;
        EndIf;
        If Flag = 1 Then
          Return Escaping;
        EndIf;
        Return Second;
      EndFunction
      """, context);
    var body = VariableFlowAnalyzer.bodyAt(document, new Position(1, 2));
    var symbols = document.getSymbolTree();
    var first = symbols.getVariableSymbolInScope(body, "First").orElseThrow();
    var second = symbols.getVariableSymbolInScope(body, "Second").orElseThrow();
    var escaping = symbols.getVariableSymbolInScope(body, "Escaping").orElseThrow();
    var definitions = Map.of(
      first, List.of(new Position(1, 2)),
      second, List.of(new Position(2, 2)),
      escaping, List.of(new Position(3, 2))
    );
    var inputs = new VariableFlowAnalyzer.FlowInputs(
      new VariableFlowAnalyzer.FlowSession(), code -> {}, false, ExecutionContext.SERVER,
      code -> Optional.empty(), () -> false, code -> List.of(first, second, escaping),
      variable -> false, variable -> TypeSet.EMPTY, definitions::get, variable -> List.of(),
      (variable, statement, position) -> TypeSet.EMPTY, (variable, position, incoming) -> incoming,
      mock(VariableFlowAnalyzer.GuardNarrowing.class)
    );
    var layout = analyzer.layoutOf(document, body);
    var variables = analyzer.variablesOf(document, body, layout, inputs);
    var vertices = analyzer.flowGraph(document, body, inputs).graph().vertexSet();

    assertThat(IndependentReturnedStructure.supports(document, body, first, variables, vertices, inputs,
      node -> true)).isTrue();
    assertThat(IndependentReturnedStructure.supports(document, body, escaping, variables, vertices, inputs,
      node -> true)).isFalse();
    assertThat(IndependentReturnedStructure.returnedLocals(document, body, variables, vertices, inputs, first,
      node -> true)).containsExactlyInAnyOrder(first, second);
  }
}
