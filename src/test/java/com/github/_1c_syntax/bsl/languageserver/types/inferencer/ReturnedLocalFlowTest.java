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
import com.github._1c_syntax.bsl.languageserver.context.DocumentContentUnavailableException;
import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.ExpressionTreeBuildingVisitor;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.ParserRuleContext;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.eclipse.lsp4j.Position;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicInteger;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;

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
  @CsvSource(value = {
    "New Structure | bsl", "New Structure() | bsl", "Новый Структура | bsl", "Новый Структура() | bsl",
    "New Structure() | os", "Новый Структура() | os"
  }, delimiter = '|')
  void anIndependentReturnedStructureDoesNotEvaluateUnrelatedLocals(String constructor, String extension) {
    var uri = URI.create("file:///ReturnedStructure." + extension);
    var document = TestUtils.getDocumentContext(uri, """
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
    assertThat(unrelatedCallsIn(document)).isZero();
  }

  @Test
  void aProjectedReturnLeavesTheOtherLocalAvailableToAPositionQuery() {
    var document = TestUtils.getDocumentContext("""
      Function Build()
        Result = New Structure;
        Result.Insert("Ready", True);
        Other = 42;
        Message(Other);
        Return Result;
      EndFunction
      """, context);
    var method = document.getSymbolTree().getMethodSymbol("Build").orElseThrow();
    var returned = inferencer.computeReturnTypes(method);

    assertThat(returned.types().refs()).extracting(TypeRef::qualifiedName).containsExactly("Структура");
    assertThat(returned.types().localFields().values().stream().flatMap(fields -> fields.keySet().stream()))
      .anyMatch(name -> name.equalsIgnoreCase("Ready"));
    assertThat(typeService.expressionTypesAt(document, new Position(4, 11), ExecutionContext.SERVER).refs())
      .extracting(TypeRef::qualifiedName).containsExactly("Число");
  }

  @Test
  void aLiteralFieldValueKeepsTheProjectionAndItsType() {
    var document = TestUtils.getDocumentContext("""
      Function Independent()
        Result = New Structure();
        Result.Insert("Status", True);
        Unused = Recursive();
        Return Result;
      EndFunction
      Function Recursive()
        Node = New Structure;
        Node.Insert("Next", Recursive());
        Return Node;
      EndFunction
      """, context);

    assertThat(unrelatedCallsIn(document)).isZero();
    var method = document.getSymbolTree().getMethodSymbol("Independent").orElseThrow();
    var result = inferencer.computeReturnTypes(method).types();
    assertThat(result.localFields().values().stream()
      .flatMap(fields -> fields.entrySet().stream())
      .filter(entry -> entry.getKey().equalsIgnoreCase("Status"))
      .flatMap(entry -> entry.getValue().types().refs().stream()))
      .extracting(TypeRef::qualifiedName).containsExactly("Булево");
  }

  @ParameterizedTest
  @CsvSource(value = {
    "Make() | Unused = Recursive(); | true",
    "Make(Seed) | Unused = Recursive(); | false",
    "Make() | Shared = Recursive(); Unused = Recursive(); | false",
    "Make() | Result.Insert(\"Other\", 1); Unused = Recursive(); | false"
  }, delimiter = '|')
  void aCallInitializerIsProjectedOnlyWithoutCallerInputsOrMutations(String initializer, String statements,
                                                                    boolean independent) {
    var document = TestUtils.getDocumentContext("""
      Var Shared;
      Function Independent()
        Seed = 1;
        Result = %s;
        %s
        Return Result;
      EndFunction
      Function Make(Value = 0)
        Local = New Structure();
        Local.Insert("Status", True);
        Return Local;
      EndFunction
      Function Recursive()
        Node = New Structure;
        Node.Insert("Next", Recursive());
        Return Node;
      EndFunction
      """.formatted(initializer, statements), context);

    if (independent) {
      assertThat(unrelatedCallsIn(document)).isZero();
    } else {
      assertThat(unrelatedCallsIn(document)).isPositive();
    }
    var method = document.getSymbolTree().getMethodSymbol("Independent").orElseThrow();
    var result = inferencer.computeReturnTypes(method).types();
    assertThat(result.localFields().values().stream()
      .flatMap(fields -> fields.entrySet().stream())
      .filter(entry -> entry.getKey().equalsIgnoreCase("Status"))
      .flatMap(entry -> entry.getValue().types().refs().stream()))
      .extracting(TypeRef::qualifiedName).containsExactly("Булево");
  }

  private int unrelatedCallsIn(DocumentContext document) {
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
      return unrelatedCalls.get();
    } finally {
      ReflectionTestUtils.setField(target, "returnTypeFromBodyInference", original);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "Result.Insert(\"Status\"); // insertion note",
    "Result.Insert(\"Status\"); Response = New Structure(\"Result\", 1); Log(Response.Result);"
  })
  void insertCommentsAndSameNamedMembersDoNotEscapeTheLocal(String statements) {
    var document = TestUtils.getDocumentContext("""
      Function Independent()
        Result = New Structure;
        %s
        Unused = Recursive();
        Return Result;
      EndFunction
      Function Recursive()
        Node = New Structure;
        Node.Insert("Next", Recursive());
        Return Node;
      EndFunction
      Procedure Log(Value)
      EndProcedure
      """.formatted(statements), context);

    assertThat(unrelatedCallsIn(document)).isZero();
  }

  @ParameterizedTest
  @ValueSource(ints = {3, 12})
  void differentReturnedLocalsShareOneProjectedBodyPass(int count) {
    var source = new StringBuilder("Function Batch(Flag)\n");
    for (var i = 0; i < count; i++) {
      source.append("  V%d = New Structure;%n".formatted(i));
    }
    for (var i = 0; i < count - 1; i++) {
      source.append("  If Flag = %d Then Return V%d; EndIf;%n".formatted(i, i));
    }
    source.append("  Return V%d;%nEndFunction%n".formatted(count - 1));
    var document = TestUtils.getDocumentContext(source.toString(), context);
    var body = VariableFlowAnalyzer.bodyAt(document, new Position(1, 3));
    var variables = java.util.stream.IntStream.range(0, count)
      .mapToObj(i -> document.getSymbolTree().getVariableSymbolInScope(body, "V" + i).orElseThrow()).toList();
    var planning = new AtomicInteger();
    var assignments = new AtomicInteger();
    var session = new VariableFlowAnalyzer.FlowSession();
    var inputs = new VariableFlowAnalyzer.FlowInputs(session, used -> {}, false, ExecutionContext.SERVER,
      used -> {
        planning.incrementAndGet();
        return Optional.empty();
      }, () -> false, used -> variables, variable -> false, variable -> TypeSet.EMPTY,
      variable -> List.of(new Position(variables.indexOf(variable) + 1, 2)), variable -> List.of(),
      (variable, statement, position) -> {
        assignments.incrementAndGet();
        return TypeSet.of(new TypeRef(TypeKind.PLATFORM, "Структура"));
      }, (variable, position, incoming) -> incoming, mock(VariableFlowAnalyzer.GuardNarrowing.class));
    var returns = Trees.findAllRuleNodes(body, BSLParser.RULE_returnStatement);

    for (var node : returns) {
      var use = ((BSLParser.ReturnStatementContext) node).expression();
      var variable = document.getSymbolTree().getVariableSymbolInScope(body, use.getText()).orElseThrow();
      assertThat(analyzer.independentReturnTypeAt(document, use, variable, inputs, () -> true).refs())
        .extracting(TypeRef::qualifiedName).containsExactly("Структура");
    }

    assertThat(planning.get()).as("one compiled body pass serves every returned local").isEqualTo(1);
    assertThat(assignments.get()).isEqualTo(count);
  }

  @Test
  void batchingDifferentLocalsStillSkipsUnrelatedRecursion() {
    var document = TestUtils.getDocumentContext("""
      Function Independent(Choice)
        Unused = Recursive();
        First = New Structure;
        First.Insert("Status");
        Second = New Structure;
        Second.Insert("Status");
        Third = New Structure;
        Third.Insert("Status");
        If Choice = 1 Then Return First; EndIf;
        If Choice = 2 Then Return Second; EndIf;
        Return Third;
      EndFunction
      Function Recursive()
        Node = New Structure;
        Node.Insert("Next", Recursive());
        Return Node;
      EndFunction
      """, context);

    assertThat(unrelatedCallsIn(document)).isZero();
  }

  @Test
  void aBatchDoesNotReadDeclarationsOfAnUnavailableCompilationBranch() {
    var document = TestUtils.getDocumentContext("""
      Function Build()
      #If Server Then
        ServerResult = New Structure;
        Return ServerResult;
      #Else
        ClientResult = New Structure;
        Return ClientResult;
      #EndIf
      EndFunction
      """, context);
    var body = VariableFlowAnalyzer.bodyAt(document, new Position(2, 3));
    var server = document.getSymbolTree().getVariableSymbolInScope(body, "ServerResult").orElseThrow();
    var client = document.getSymbolTree().getVariableSymbolInScope(body, "ClientResult").orElseThrow();
    var use = ((BSLParser.ReturnStatementContext) Trees.findAllRuleNodes(body, BSLParser.RULE_returnStatement)
      .iterator().next()).expression();
    var structure = TypeSet.of(new TypeRef(TypeKind.PLATFORM, "Структура"));
    var inputs = new VariableFlowAnalyzer.FlowInputs(new VariableFlowAnalyzer.FlowSession(), used -> {}, false,
      ExecutionContext.SERVER, used -> Optional.of(Set.of(ExecutionContext.SERVER)), () -> false,
      used -> List.of(server, client), variable -> false, variable -> {
        assertThat(variable).as("inactive declarations are not needed for the selected return").isSameAs(server);
        return TypeSet.EMPTY;
      }, variable -> List.of(new Position(variable == server ? 2 : 5, 2)), variable -> List.of(),
      (variable, statement, position) -> structure, (variable, position, incoming) -> incoming,
      mock(VariableFlowAnalyzer.GuardNarrowing.class));

    assertThat(analyzer.independentReturnTypeAt(document, use, server, inputs, () -> true)).isEqualTo(structure);
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
  void aDynamicStructureKeyDoesNotRequireItsRecursiveValueToInferTheReturn() {
    var document = TestUtils.getDocumentContext("""
      Function Build(Key)
        Result = New Structure;
        Value = Build(Key);
        Result.Insert(Key, Value);
        Return Result;
      EndFunction
      """, context);
    var method = document.getSymbolTree().getMethodSymbol("Build").orElseThrow();
    var inference = new InferenceContext(document, ExecutionContext.SERVER);
    ExpressionTypeInferencer target = AopTestUtils.getUltimateTargetObject(inferencer);

    TypeSet types = ReflectionTestUtils.invokeMethod(target, "methodReturnType", method, inference);

    assertThat(types.refs()).extracting(TypeRef::qualifiedName).containsExactly("Структура");
    assertThat(types.localFields()).isEmpty();
    assertThat(inference.cycleCut).isFalse();
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

    assertThat(analyzer.independentReturnTypeAt(document, first, variable, server, () -> true).refs())
      .extracting(TypeRef::qualifiedName).containsExactly("String");
    var afterFirst = assignments.get();
    assertThat(afterFirst).isPositive();
    assertThat(analyzer.independentReturnTypeAt(document, last, variable, server, () -> true).refs())
      .extracting(TypeRef::qualifiedName).containsExactly("String");
    assertThat(assignments.get()).as("later returns read facts computed by the first return").isEqualTo(afterFirst);

    var client = projectedInputs(session, variable, ExecutionContext.THIN_CLIENT, assignments);
    assertThat(analyzer.independentReturnTypeAt(document, last, variable, client, () -> true).refs())
      .extracting(TypeRef::qualifiedName).containsExactly("Number");
    assertThat(assignments.get()).isGreaterThan(afterFirst);
    var afterClient = assignments.get();
    var another = projectedInputs(new VariableFlowAnalyzer.FlowSession(), variable, ExecutionContext.SERVER, assignments);
    assertThat(analyzer.independentReturnTypeAt(document, first, variable, another, () -> true).refs())
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
  void stableRejectionsDoNotRescanEachReturnAndRemainSeparateByContext() {
    var document = TestUtils.getDocumentContext("""
      Function Build(Flag)
        Result = New Structure;
        Alias(Result);
        If Flag Then
          Return Result;
        EndIf;
        Return Result;
      EndFunction
      """, context);
    var body = VariableFlowAnalyzer.bodyAt(document, new Position(1, 3));
    var variable = document.getSymbolTree().getVariableSymbolInScope(body, "Result").orElseThrow();
    var first = (ParserRuleContext) Trees.findTerminalNodeContainsPosition(document.getAst(), new Position(4, 12))
      .orElseThrow().getParent();
    var last = (ParserRuleContext) Trees.findTerminalNodeContainsPosition(document.getAst(), new Position(6, 10))
      .orElseThrow().getParent();
    var checks = new AtomicInteger();
    var narrowing = mock(VariableFlowAnalyzer.GuardNarrowing.class);
    doAnswer(call -> {
      checks.incrementAndGet();
      return Set.of();
    }).when(narrowing).variablesOf(any());
    var session = new VariableFlowAnalyzer.FlowSession();
    var server = rejectionInputs(session, variable, ExecutionContext.SERVER, narrowing);
    assertThat(analyzer.independentReturnTypeAt(document, first, variable, server, () -> true)).isNull();
    var afterFirst = checks.get();
    assertThat(afterFirst).isPositive();

    assertThat(analyzer.independentReturnTypeAt(document, last, variable, server, () -> true)).isNull();
    assertThat(checks.get()).isEqualTo(afterFirst);
    var client = rejectionInputs(session, variable, ExecutionContext.THIN_CLIENT, narrowing);
    assertThat(analyzer.independentReturnTypeAt(document, last, variable, client, () -> true)).isNull();
    assertThat(checks.get()).isGreaterThan(afterFirst);
  }

  private static VariableFlowAnalyzer.FlowInputs rejectionInputs(VariableFlowAnalyzer.FlowSession session,
      VariableSymbol variable, ExecutionContext mode, VariableFlowAnalyzer.GuardNarrowing narrowing) {
    return new VariableFlowAnalyzer.FlowInputs(session, body -> {}, true, mode, body -> Optional.empty(), () -> false,
      body -> List.of(variable), candidate -> false, candidate -> TypeSet.EMPTY,
      candidate -> List.of(new Position(1, 2)), candidate -> List.of(),
      (candidate, statement, position) -> TypeSet.EMPTY, (candidate, position, incoming) -> incoming, narrowing);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void aFailedProjectionDoesNotDiscardIndependentLiteralReturns(boolean contentReleased) {
    var document = TestUtils.getDocumentContext("""
      Function Build(Flag)
        If Flag Then
          Return 1;
        EndIf;
        Result = New Structure;
        Return Result;
      EndFunction
      """, context);
    ExpressionTypeInferencer target = AopTestUtils.getUltimateTargetObject(inferencer);
    var original = (VariableFlowAnalyzer) ReflectionTestUtils.getField(target, "variableFlowAnalyzer");
    VariableFlowAnalyzer real = AopTestUtils.getUltimateTargetObject(original);
    var failing = spy(real);
    var failure = contentReleased ? new DocumentContentUnavailableException(document.getUri())
      : new IllegalArgumentException("incomplete expression in the edited document");
    doThrow(failure).when(failing)
      .independentReturnTypeAt(any(), any(), any(), any(), any());
    ReflectionTestUtils.setField(target, "variableFlowAnalyzer", failing);
    try {
      var result = inferencer.computeReturnTypes(document.getSymbolTree().getMethodSymbol("Build").orElseThrow());
      assertThat(result.types().refs()).extracting(TypeRef::qualifiedName).contains("Число");
      assertThat(result.types().refs()).extracting(TypeRef::qualifiedName).contains("Структура");
      assertThat(result.incomplete()).isTrue();
    } finally {
      ReflectionTestUtils.setField(target, "variableFlowAnalyzer", original);
    }
  }

  @Test
  void aDepthCutProjectionDoesNotPoisonAShallowerLookupInTheSameSession() {
    var document = TestUtils.getDocumentContext("""
      Function Build()
        Result = New Structure;
        Return Result;
      EndFunction
      """, context);
    var method = document.getSymbolTree().getMethodSymbol("Build").orElseThrow();
    var statement = (BSLParser.ReturnStatementContext) Trees.findAllRuleNodes(document.getAst(),
      BSLParser.RULE_returnStatement).iterator().next();
    var expression = ExpressionTreeBuildingVisitor.buildExpressionTree(statement.expression());
    var inference = new InferenceContext(document, ExecutionContext.SERVER);
    inference.visited.add(method);
    ExpressionTypeInferencer target = AopTestUtils.getUltimateTargetObject(inferencer);
    inference.depth = (Integer) ReflectionTestUtils.getField(target, "MAX_DEPTH") - 1;

    TypeSet deep = ReflectionTestUtils.invokeMethod(target, "inferInternal", expression, inference, false, true);
    assertThat(deep.isEmpty()).isTrue();
    assertThat(inference.depthCut).isTrue();
    inference.depth = 0;
    inference.depthCut = false;
    TypeSet shallow = ReflectionTestUtils.invokeMethod(target, "inferInternal", expression, inference, false, true);

    assertThat(shallow.refs()).extracting(TypeRef::qualifiedName).containsExactly("Структура");
    assertThat(inference.depthCut).isFalse();
  }

  @Test
  void aDeepReturnedLocalCanUseAlreadyComputedFactsFromTheSameMode() {
    var document = TestUtils.getDocumentContext("""
      Function Build()
        Result = New Structure;
        Result.Insert("Status");
        Return Result;
      EndFunction
      """, context);
    var method = document.getSymbolTree().getMethodSymbol("Build").orElseThrow();
    var statement = (BSLParser.ReturnStatementContext) Trees.findAllRuleNodes(document.getAst(),
      BSLParser.RULE_returnStatement).iterator().next();
    var expression = ExpressionTreeBuildingVisitor.buildExpressionTree(statement.expression());
    ExpressionTypeInferencer target = AopTestUtils.getUltimateTargetObject(inferencer);
    var shallow = new InferenceContext(document, ExecutionContext.SERVER);
    shallow.visited.add(method);
    TypeSet warm = ReflectionTestUtils.invokeMethod(target, "inferInternal", expression, shallow, false, false);
    assertThat(warm.refs()).extracting(TypeRef::qualifiedName).containsExactly("Структура");

    var deep = new InferenceContext(document, ExecutionContext.SERVER);
    deep.visited.add(method);
    deep.depth = (Integer) ReflectionTestUtils.getField(target, "MAX_DEPTH") - 1;
    TypeSet result = ReflectionTestUtils.invokeMethod(target, "inferInternal", expression, deep, false, true);
    assertThat(result).isEqualTo(warm);
    assertThat(deep.depthCut).isFalse();
  }

  @ParameterizedTest
  @CsvSource(value = {
    "Result = 1; | false", "Alias(Result); | false",
    "If Result <> Undefined And Other <> Undefined Then Result.Insert(\"Status\"); EndIf; | false",
    "Result = 1; | true", "Alias(Result); | true",
    "If Result <> Undefined And Other <> Undefined Then Result.Insert(\"Status\"); EndIf; | true"
  }, delimiter = '|')
  void inactiveCodeDoesNotInvalidateProjection(String branchCode, boolean oneScript) {
    var uri = URI.create(oneScript ? "file:///BranchStructure.os" : "file:///BranchStructure.bsl");
    var document = TestUtils.getDocumentContext(uri, """
      Function Build()
        Result = New Structure;
        Other = 1;
      #If %s Then
        %s
      #EndIf
        Unused = Recursive();
        Return Result;
      EndFunction
      Function Recursive()
        Return 1;
      EndFunction
      Procedure Alias(Value)
      EndProcedure
      """.formatted(oneScript ? "Linux" : "Client", branchCode), context);
    var method = document.getSymbolTree().getMethodSymbol("Build").orElseThrow();
    var statement = (BSLParser.ReturnStatementContext) Trees.findAllRuleNodes(document.getAst(),
      BSLParser.RULE_returnStatement).iterator().next();
    var expression = ExpressionTreeBuildingVisitor.buildExpressionTree(statement.expression());
    ExpressionTypeInferencer target = AopTestUtils.getUltimateTargetObject(inferencer);
    var original = (ReturnTypeFromBodyInference) ReflectionTestUtils.getField(target, "returnTypeFromBodyInference");
    var counted = spy(original);
    var calls = new AtomicInteger();
    doAnswer(call -> {
      calls.incrementAndGet();
      return call.callRealMethod();
    }).when(counted).of(any(), any(), any(), any());
    ReflectionTestUtils.setField(target, "returnTypeFromBodyInference", counted);
    try {
      var server = new InferenceContext(document,
        oneScript ? ExecutionContext.OSCRIPT_WINDOWS : ExecutionContext.SERVER);
      server.visited.add(method);
      TypeSet serverTypes = ReflectionTestUtils.invokeMethod(target, "inferInternal", expression, server, false, true);
      assertThat(serverTypes.refs()).extracting(TypeRef::qualifiedName).containsExactly("Структура");
      assertThat(calls.get()).isZero();

      var client = new InferenceContext(document,
        oneScript ? ExecutionContext.OSCRIPT_LINUX : ExecutionContext.THIN_CLIENT);
      client.visited.add(method);
      TypeSet clientTypes = ReflectionTestUtils.invokeMethod(target, "inferInternal", expression, client, false, true);
      assertThat(clientTypes.refs()).extracting(TypeRef::qualifiedName)
        .containsExactly(branchCode.equals("Result = 1;") ? "Число" : "Структура");
      assertThat(calls.get()).isPositive();
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
