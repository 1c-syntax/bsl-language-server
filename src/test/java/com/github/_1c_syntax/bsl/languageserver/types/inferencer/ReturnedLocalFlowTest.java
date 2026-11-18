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
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.eclipse.lsp4j.Position;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

class ReturnedLocalFlowTest extends AbstractServerContextAwareTest {
  @Autowired
  private ExpressionTypeInferencer inferencer;
  @Autowired
  private TypeService typeService;

  @BeforeEach
  void setUp() {
    initServerContext();
  }

  @Test
  void anIndependentReturnedStructureDoesNotEvaluateUnrelatedLocals() {
    var document = TestUtils.getDocumentContext("""
      Function Independent()
        Result = New Structure;
        Result.Insert("Status");
        Unused = Recursive();
        Return Result;
      EndFunction
      Function Recursive()
        Node = New Structure;
        Node.Insert("Next", Recursive());
        Return Node;
      EndFunction
      """, context);
    ExpressionTypeInferencer target = AopTestUtils.getUltimateTargetObject(inferencer);
    var original = (ReturnTypeFromBodyInference) ReflectionTestUtils.getField(target, "returnTypeFromBodyInference");
    var counted = spy(original);
    var unrelatedCalls = new AtomicInteger();
    doAnswer(call -> {
      if (((MethodSymbol) call.getArgument(0)).getName().equals("Recursive")) {
        unrelatedCalls.incrementAndGet();
      }
      return call.callRealMethod();
    }).when(counted).of(any(), any(), any(), any());
    ReflectionTestUtils.setField(target, "returnTypeFromBodyInference", counted);
    try {
      var result = inferencer.computeReturnTypes(document.getSymbolTree().getMethodSymbol("Independent").orElseThrow());
      assertThat(result.types().refs()).extracting(TypeRef::qualifiedName).containsExactly("Структура");
      assertThat(result.types().localFields().values().stream()
        .anyMatch(fields -> fields.keySet().stream().anyMatch(name -> name.equalsIgnoreCase("Status")))).isTrue();
      assertThat(unrelatedCalls.get()).isZero();
    } finally {
      ReflectionTestUtils.setField(target, "returnTypeFromBodyInference", original);
    }
  }

  @Test
  void aFieldDependingOnAnotherVariableKeepsTheFullCalculation() {
    var document = TestUtils.getDocumentContext("""
      Function Build()
        Result = New Structure;
        Value = "ready";
        Result.Insert("Value", Value);
        Return Result;
      EndFunction
      """, context);
    var result = inferencer.computeReturnTypes(document.getSymbolTree().getMethodSymbol("Build").orElseThrow());

    assertThat(result.types().localFields().values().stream().flatMap(fields -> fields.entrySet().stream())
      .filter(field -> field.getKey().equalsIgnoreCase("Value"))
      .flatMap(field -> field.getValue().types().refs().stream()))
      .extracting(TypeRef::qualifiedName).contains("Строка");
  }

  @Test
  void aReturnBeforeAnInsertionDoesNotSeeTheLaterField() {
    var document = TestUtils.getDocumentContext("""
      Function Before()
        Result = New Structure;
        Return Result;
        Result.Insert("Late");
      EndFunction
      Function After()
        Result = New Structure;
        Result.Insert("Early");
        Return Result;
      EndFunction
      """, context);
    var before = inferencer.computeReturnTypes(document.getSymbolTree().getMethodSymbol("Before").orElseThrow());
    var after = inferencer.computeReturnTypes(document.getSymbolTree().getMethodSymbol("After").orElseThrow());

    assertThat(before.types().localFields().values().stream()
      .flatMap(fields -> fields.keySet().stream())).noneMatch(name -> name.equalsIgnoreCase("Late"));
    assertThat(after.types().localFields().values().stream()
      .flatMap(fields -> fields.keySet().stream())).anyMatch(name -> name.equalsIgnoreCase("Early"));
  }

  @Test
  void aStructureReadThroughAnOutputParameterKeepsItsValueFields() {
    var document = TestUtils.getDocumentContext("""
      Function Build()
        Result = New Structure;
        Box = New Structure("Payload", New Structure("Value", "ready"));
        Box.Property("Payload", Result);
        Return Result;
      EndFunction
      """, context);
    var result = inferencer.computeReturnTypes(document.getSymbolTree().getMethodSymbol("Build").orElseThrow());

    assertThat(result.types().localFields().values().stream().flatMap(fields -> fields.entrySet().stream())
      .filter(field -> field.getKey().equalsIgnoreCase("Value"))
      .flatMap(field -> field.getValue().types().refs().stream()))
      .extracting(TypeRef::qualifiedName).contains("Строка");
  }

  @Test
  void insertedFieldsKeepTheirCompilationContext() {
    var source = """
      Function Build()
        Result = New Structure;
      #If Server Then
        Result.Insert("ServerField");
      #Else
        Result.Insert("ClientField");
      #EndIf
        Return Result;
      EndFunction
      Function Caller()
        Value = Build();
        Return Value;
      EndFunction
      """;
    var document = TestUtils.getDocumentContext(source, context);
    var callLine = (int) source.substring(0, source.lastIndexOf("Build()")).lines().count() - 1;
    var position = new Position(callLine, "  Value = ".length() + 1);
    var server = typeService.expressionTypesAt(document, position, ExecutionContext.SERVER);
    var client = typeService.expressionTypesAt(document, position, ExecutionContext.THIN_CLIENT);

    assertThat(server.localFields().values().stream().flatMap(fields -> fields.keySet().stream()))
      .anyMatch(name -> name.equalsIgnoreCase("ServerField"))
      .noneMatch(name -> name.equalsIgnoreCase("ClientField"));
    assertThat(client.localFields().values().stream().flatMap(fields -> fields.keySet().stream()))
      .anyMatch(name -> name.equalsIgnoreCase("ClientField"))
      .noneMatch(name -> name.equalsIgnoreCase("ServerField"));
  }
}
