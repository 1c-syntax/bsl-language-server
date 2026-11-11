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
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver.MethodContext;
import com.github._1c_syntax.bsl.languageserver.types.index.InferredExpressionTypeIndex;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.VariableFlowAnalyzer.FlowInputs;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.antlr.v4.runtime.ParserRuleContext;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

@CleanupContextBeforeClassAndAfterClass
class ContextIterationOrderTest extends AbstractServerContextAwareTest {
  @Autowired
  private TypeService typeService;
  @Autowired
  private ExpressionTypeInferencer inferencer;
  @Autowired
  private InferredExpressionTypeIndex expressions;

  @Test
  void neutralQueryUsesStableModeOrderForDifferentSetOrders() {
    var document = TestUtils.getDocumentContext("""
      Procedure Check()
        X = 1;
        Result = X;
      EndProcedure
      """, context);
    ExpressionTypeInferencer target = AopTestUtils.getUltimateTargetObject(inferencer);
    var originalResolver = (CompilationContextResolver) ReflectionTestUtils.getField(target, "compilationContextResolver");
    var originalFlow = (VariableFlowAnalyzer) ReflectionTestUtils.getField(target, "variableFlowAnalyzer");
    CompilationContextResolver resolverTarget = AopTestUtils.getUltimateTargetObject(originalResolver);
    VariableFlowAnalyzer flowTarget = AopTestUtils.getUltimateTargetObject(originalFlow);
    var resolver = spy(resolverTarget);
    var flow = spy(flowTarget);
    var seen = new ArrayList<ExecutionContext>();
    doAnswer(call -> {
      VariableSymbol variable = call.getArgument(2);
      FlowInputs inputs = call.getArgument(3);
      if (variable.getName().equals("X")) {
        seen.add(inputs.executionContext());
      }
      return call.callRealMethod();
    }).when(flow).typeAtRoot(same(document), any(ParserRuleContext.class), any(VariableSymbol.class), any(FlowInputs.class),
      any(java.util.function.BooleanSupplier.class));
    ReflectionTestUtils.setField(target, "compilationContextResolver", resolver);
    ReflectionTestUtils.setField(target, "variableFlowAnalyzer", flow);
    try {
      for (var order : List.of(List.of(ExecutionContext.THIN_CLIENT, ExecutionContext.SERVER),
        List.of(ExecutionContext.SERVER, ExecutionContext.THIN_CLIENT))) {
        seen.clear();
        flow.clear(document.getUri());
        expressions.clear(document.getUri());
        doReturn(Optional.of(new MethodContext(Optional.empty(), new LinkedHashSet<>(order))))
          .when(resolver).contextAt(same(document), any(Position.class), any());
        assertThat(typeService.expressionTypesAt(document, new Position(2, "  Result = ".length())).refs())
          .extracting(TypeRef::qualifiedName).containsExactly("Число");
        assertThat(seen.stream().distinct().toList()).containsExactly(ExecutionContext.SERVER, ExecutionContext.THIN_CLIENT);
      }
    } finally {
      ReflectionTestUtils.setField(target, "compilationContextResolver", originalResolver);
      ReflectionTestUtils.setField(target, "variableFlowAnalyzer", originalFlow);
    }
  }
}
