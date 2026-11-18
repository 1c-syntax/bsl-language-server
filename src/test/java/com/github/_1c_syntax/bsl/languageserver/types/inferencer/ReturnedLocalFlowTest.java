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
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import org.antlr.v4.runtime.ParserRuleContext;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.eclipse.lsp4j.Position;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.mock;

class ReturnedLocalFlowTest extends AbstractServerContextAwareTest {
  @Autowired
  private ExpressionTypeInferencer inferencer;
  @Autowired
  private TypeService typeService;
  @Autowired
  private VariableFlowAnalyzer analyzer;

  @BeforeEach
  void setUp() {
    initServerContext();
  }

  @ParameterizedTest
  @ValueSource(strings = {"New Structure", "New Structure()", "Новый Структура", "Новый Структура()"})
  void anIndependentReturnedStructureDoesNotEvaluateUnrelatedLocals(String constructor) {
    var document = TestUtils.getDocumentContext("""
      Function Independent()
        Result = %s;
        Result.Insert("Status");
        Unused = Recursive();
        Return Result;
      EndFunction
      Function Recursive()
        Node = New Structure;
        Node.Insert("Next", Recursive());
        Return Node;
      EndFunction
      """.formatted(constructor), context);
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
  void projectedFactsAreReusedAcrossReturnsButNotAcrossModesOrSessions() {
    var document = TestUtils.getDocumentContext("""
      Function Build(Flag)
        Result = New Structure;
        If Flag Then
          Return Result;
        EndIf;
        Result.Insert("Late");
        Return Result;
      EndFunction
      """, context);
    var body = VariableFlowAnalyzer.bodyAt(document, new Position(1, 3));
    var variable = document.getSymbolTree().getVariableSymbolInScope(body, "Result").orElseThrow();
    var first = (ParserRuleContext) Trees.findTerminalNodeContainsPosition(document.getAst(), new Position(3, 12))
      .orElseThrow().getParent();
    var last = (ParserRuleContext) Trees.findTerminalNodeContainsPosition(document.getAst(), new Position(6, 10))
      .orElseThrow().getParent();
    var assignments = new AtomicInteger();
    var session = new VariableFlowAnalyzer.FlowSession();
    var server = projectedInputs(session, variable, ExecutionContext.SERVER, assignments);

    assertThat(analyzer.independentReturnTypeAt(document, first, variable, server).refs())
      .extracting(TypeRef::qualifiedName).containsExactly("String");
    var afterFirst = assignments.get();
    assertThat(afterFirst).isPositive();
    assertThat(analyzer.independentReturnTypeAt(document, last, variable, server).refs())
      .extracting(TypeRef::qualifiedName).containsExactly("String");
    assertThat(assignments.get()).as("later returns read facts computed by the first return").isEqualTo(afterFirst);

    var client = projectedInputs(session, variable, ExecutionContext.THIN_CLIENT, assignments);
    assertThat(analyzer.independentReturnTypeAt(document, last, variable, client).refs())
      .extracting(TypeRef::qualifiedName).containsExactly("Number");
    assertThat(assignments.get()).isGreaterThan(afterFirst);
    var afterClient = assignments.get();
    var another = projectedInputs(new VariableFlowAnalyzer.FlowSession(), variable, ExecutionContext.SERVER, assignments);
    assertThat(analyzer.independentReturnTypeAt(document, first, variable, another).refs())
      .extracting(TypeRef::qualifiedName).containsExactly("String");
    assertThat(assignments.get()).isGreaterThan(afterClient);
  }

  private static VariableFlowAnalyzer.FlowInputs projectedInputs(VariableFlowAnalyzer.FlowSession session,
      VariableSymbol variable, ExecutionContext mode, AtomicInteger assignments) {
    return new VariableFlowAnalyzer.FlowInputs(session, body -> {}, false, mode, body -> Optional.empty(), () -> false,
      body -> List.of(variable), candidate -> false, candidate -> TypeSet.EMPTY,
      candidate -> List.of(new Position(1, 2)), candidate -> List.of(new Position(5, 2)),
      (candidate, statement, position) -> {
        assignments.incrementAndGet();
        return TypeSet.of(new TypeRef(TypeKind.PRIMITIVE, mode == ExecutionContext.SERVER ? "String" : "Number"));
      }, (candidate, position, incoming) -> incoming, mock(VariableFlowAnalyzer.GuardNarrowing.class));
  }

  @ParameterizedTest
  @ValueSource(strings = {"NewStructure", "НовыйСтруктура"})
  void anIdentifierSpelledLikeAConstructorKeepsItsAssignedType(String name) {
    var document = TestUtils.getDocumentContext("""
      Function Build()
        %s = "ready";
        Result = %s;
        Return Result;
      EndFunction
      """.formatted(name, name), context);

    assertThat(inferencer.computeReturnTypes(document.getSymbolTree().getMethodSymbol("Build").orElseThrow())
      .types().refs()).extracting(TypeRef::qualifiedName).containsExactly("Строка");
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "Result = New Structure; // Структура",
    "Result = New Structure; Result = New Structure;",
    "Result = New Structure; Key = \"Status\"; Result.Insert(Key);",
    "Result = New Structure; Alias(Result);",
    "Result = New Structure; If TypeOf(Result) = Type(\"Structure\") Then "
      + "Result.Insert(\"Checked\"); EndIf;"
  })
  void dependentConstructionsKeepTheFullFlow(String construction) {
    var document = TestUtils.getDocumentContext("""
      Function Build()
        %s
        Unused = Hidden();
        Return Result;
      EndFunction
      Function Hidden()
        Return 1;
      EndFunction
      Procedure Alias(Value)
      EndProcedure
      """.formatted(construction), context);
    ExpressionTypeInferencer target = AopTestUtils.getUltimateTargetObject(inferencer);
    var original = (ReturnTypeFromBodyInference) ReflectionTestUtils.getField(target, "returnTypeFromBodyInference");
    var counted = spy(original);
    var hiddenCalls = new AtomicInteger();
    doAnswer(call -> {
      if (((MethodSymbol) call.getArgument(0)).getName().equals("Hidden")) {
        hiddenCalls.incrementAndGet();
      }
      return call.callRealMethod();
    }).when(counted).of(any(), any(), any(), any());
    ReflectionTestUtils.setField(target, "returnTypeFromBodyInference", counted);
    try {
      var result = inferencer.computeReturnTypes(document.getSymbolTree().getMethodSymbol("Build").orElseThrow());
      assertThat(result.types().refs()).extracting(TypeRef::qualifiedName).contains("Структура");
      assertThat(hiddenCalls.get()).as("a dependent result uses the full body flow").isPositive();
    } finally {
      ReflectionTestUtils.setField(target, "returnTypeFromBodyInference", original);
    }
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

    assertThat(before.types().refs()).extracting(TypeRef::qualifiedName).containsExactly("Структура");
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
