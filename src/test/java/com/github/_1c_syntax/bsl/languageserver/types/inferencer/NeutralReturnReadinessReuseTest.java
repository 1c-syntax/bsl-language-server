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
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.types.index.SymbolTypeIndex;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

@CleanupContextBeforeClassAndAfterClass
@TestPropertySource(properties = "app.platform-context.enabled=false")
class NeutralReturnReadinessReuseTest extends AbstractServerContextAwareTest {
  @Autowired
  private ExpressionTypeInferencer inferencer;

  @ParameterizedTest
  @ValueSource(ints = {0, 8})
  void anIncompleteNeutralReturnIsReusedOnlyWithinItsRequest(int branches) {
    initServerContext();
    var source = new StringBuilder("""
      Function Root()
        If Flag Then
          Return Leaf();
        EndIf;
        If OtherFlag Then
          Return Leaf();
        EndIf;
        Return Ready();
      EndFunction
      Function Leaf()
        Return Pending();
      EndFunction
      Function Pending()
        Return "ready";
      EndFunction
      Function Ready()
        Return "ready";
      EndFunction
      """.replace("Return Leaf();", "Return " + (branches == 0 ? "Leaf" : "Branch" + branches) + "();"));
    for (var i = 1; i <= branches; i++) {
      var previous = i == 1 ? "Leaf" : "Branch" + (i - 1);
      source.append("""
        Function Branch%d()
          If Flag Then
            Return %s();
          EndIf;
          Return %s();
        EndFunction
        """.formatted(i, previous, previous));
    }
    var document = TestUtils.getDocumentContext(source.toString(), context);
    ExpressionTypeInferencer target = AopTestUtils.getUltimateTargetObject(inferencer);
    var originalBodies = (ReturnTypeFromBodyInference) ReflectionTestUtils.getField(target, "returnTypeFromBodyInference");
    var originalTypes = (SymbolTypeIndex) ReflectionTestUtils.getField(target, "symbolTypeIndex");
    SymbolTypeIndex realTypes = AopTestUtils.getUltimateTargetObject(originalTypes);
    var types = spy(realTypes);
    doAnswer(call -> ((MethodSymbol) call.getArgument(0)).getName().equals("Pending"))
      .when(types).returnAwaitsUnparsedModule(any());
    var bodies = spy(originalBodies);
    var counts = new HashMap<String, Integer>();
    doAnswer(call -> {
      counts.merge(((MethodSymbol) call.getArgument(0)).getName(), 1, Integer::sum);
      return call.callRealMethod();
    }).when(bodies).of(any(), any(), any(), any());
    ReflectionTestUtils.setField(target, "returnTypeFromBodyInference", bodies);
    ReflectionTestUtils.setField(target, "symbolTypeIndex", types);
    try {
      var root = document.getSymbolTree().getMethodSymbol("Root").orElseThrow();
      var first = inferencer.computeReturnTypes(root);
      assertThat(first.types().refs()).extracting(TypeRef::qualifiedName).containsExactly("Строка");
      assertThat(first.incomplete()).isTrue();
      assertThat(counts).as("repeated calls share the incomplete result and readiness flags").containsEntry("Leaf", 1);
      for (var i = 1; i <= branches; i++) {
        assertThat(counts).as("branch %s body calculations in one request", i).containsEntry("Branch" + i, 1);
      }

      var second = inferencer.computeReturnTypes(root);
      assertThat(second.incomplete()).isTrue();
      assertThat(second.types()).isEqualTo(first.types());
      assertThat(counts).as("the next request retries the incomplete body").containsEntry("Leaf", 2);
      assertThat(counts).as("a complete body remains globally reusable").containsEntry("Pending", 1);
      assertThat(counts).as("a sibling's missing input does not contaminate a complete body").containsEntry("Ready", 1);
      for (var i = 1; i <= branches; i++) {
        assertThat(counts).as("branch %s is retried in the next request", i).containsEntry("Branch" + i, 2);
      }
    } finally {
      ReflectionTestUtils.setField(target, "returnTypeFromBodyInference", originalBodies);
      ReflectionTestUtils.setField(target, "symbolTypeIndex", originalTypes);
    }
  }
}
