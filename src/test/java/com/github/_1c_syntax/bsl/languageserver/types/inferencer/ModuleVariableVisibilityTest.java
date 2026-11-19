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
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.index.InferredExpressionTypeIndex;
import com.github._1c_syntax.bsl.languageserver.types.index.MethodReturnTypeIndexer;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

@CleanupContextBeforeClassAndAfterClass
@TestPropertySource(properties = "app.platform-context.enabled=false")
class ModuleVariableVisibilityTest extends AbstractServerContextAwareTest {
  @Autowired
  private ExpressionTypeInferencer inferencer;
  @Autowired
  private VariableFlowAnalyzer analyzer;
  @Autowired
  private MethodReturnTypeIndexer indexer;
  @Autowired
  private InferredExpressionTypeIndex expressions;
  @Autowired
  private TypeService typeService;

  @BeforeEach
  void setUp() {
    initServerContext();
  }

  @Test
  void computingOneCellDoesNotEvaluateAnUnrelatedWriteInTheSameProcedure() {
    var document = document("""
      Var Needed;
      Var Other;
      Procedure WriteState()
        Value = "ready";
        Prepared = Value;
        Needed = Prepared;
        Unused = Recursive();
        Other = Recursive();
      EndProcedure
      Function Recursive()
        Result = New Structure;
        Result.Insert("Next", Recursive());
        Return Result;
      EndFunction
      Function Independent()
        Result = Needed;
        Return Result;
      EndFunction
      """);
    var method = document.getSymbolTree().getMethodSymbol("Independent").orElseThrow();
    ExpressionTypeInferencer target = AopTestUtils.getUltimateTargetObject(inferencer);
    var original = (ReturnTypeFromBodyInference) ReflectionTestUtils.getField(target, "returnTypeFromBodyInference");
    var counted = spy(original);
    var unrelatedCalls = new AtomicInteger();
    doAnswer(invocation -> {
      var called = (MethodSymbol) invocation.getArgument(0);
      if (called.getName().equals("Recursive")) {
        unrelatedCalls.incrementAndGet();
      }
      return invocation.callRealMethod();
    }).when(counted).of(any(), any(), any(), any());
    ReflectionTestUtils.setField(target, "returnTypeFromBodyInference", counted);
    try {
      assertThat(inferencer.computeReturnTypes(method).types().refs())
        .extracting(TypeRef::qualifiedName).contains("Строка");
      assertThat(unrelatedCalls.get()).as("the other module write and unused local are not inputs of Needed").isZero();
    } finally {
      ReflectionTestUtils.setField(target, "returnTypeFromBodyInference", original);
    }
  }

  @Test
  void aReadCellKeepsItsValueAtTheAssignmentRatherThanItsWholeScopeUnion() {
    var document = document("""
      Var Needed;
      Var Other;
      Procedure WriteState()
        Other = "ready";
        Needed = Other;
      EndProcedure
      Procedure WriteNumber()
        Other = 1;
      EndProcedure
      Function Read()
        Result = Needed;
        Return Result;
      EndFunction
      """);

    assertThat(inferencer.computeReturnTypes(document.getSymbolTree().getMethodSymbol("Read").orElseThrow())
      .types().refs()).extracting(TypeRef::qualifiedName).contains("Строка").doesNotContain("Число");
  }

  @Test
  void moduleInitializationDoesNotReadEveryVisibleCell() {
    var document = document("""
      Var Needed;
      Var Other;
      Procedure WriteOther()
        Other = Recursive();
      EndProcedure
      Function Recursive()
        Result = New Structure;
        Result.Insert("Next", Recursive());
        Return Result;
      EndFunction
      Function Independent()
        Result = Needed;
        Return Result;
      EndFunction
      Needed = "ready";
      """);

    assertThat(recursiveBodiesForIndependent(document)).isZero();
  }

  @Test
  void aReverseCellChainReachesItsFixedPointBeforePublication() {
    var source = new StringBuilder();
    for (var i = 0; i < 20; i++) {
      source.append("Var V%02d;%n".formatted(i));
    }
    for (var i = 0; i < 20; i++) {
      source.append("Procedure Write%02d()%n".formatted(i));
      source.append(i == 19 ? "V19 = \"ready\";\n" : "V%02d = V%02d;%n".formatted(i, i + 1));
      source.append("EndProcedure\n");
    }
    source.append("Function Read() Result = V00; Return Result; EndFunction\n");
    var document = document(source.toString());
    var result = inferencer.computeReturnTypes(document.getSymbolTree().getMethodSymbol("Read").orElseThrow());

    assertThat(result.types().refs()).extracting(TypeRef::qualifiedName).contains("Строка");
    assertThat(result.incomplete()).isFalse();
  }

  @Test
  void anExpandingCellIsNotReportedAsACompleteReturnType() {
    var document = document("""
      Var Shared;
      Procedure Grow()
        Shared = New Structure("Next", Shared);
      EndProcedure
      Function Read()
        Result = Shared;
        Return Result;
      EndFunction
      """);
    var result = inferencer.computeReturnTypes(document.getSymbolTree().getMethodSymbol("Read").orElseThrow());

    assertThat(result.incomplete()).isTrue();
    assertThat(result.incompleteMethodReturns()).as("a capped cell is not a missing method-return cycle").isNull();
    var repeated = inferencer.computeReturnTypes(document.getSymbolTree().getMethodSymbol("Read").orElseThrow());
    assertThat(repeated.incomplete()).as("the previous partial cells must not become a settled cache hit").isTrue();
  }

  @Test
  void recursiveReturnKeepsNestedFieldTypesWhenReadThroughAModuleCell() {
    var document = document("""
      Var Shared;
      Procedure Write()
        Shared = Outer();
      EndProcedure
      Function Read()
        Result = Shared;
        Return Result;
      EndFunction
      Function Outer()
        Result = New Structure("Name", "ready");
        Result.Insert("Next", Branch());
        Return Result;
      EndFunction
      Function Branch()
        Existing = Shared;
        If Flag Then
          Return Outer();
        EndIf;
        Return Existing = Undefined;
      EndFunction
      """);
    var method = document.getSymbolTree().getMethodSymbol("Read").orElseThrow();

    var result = inferencer.computeReturnTypes(method);

    assertThat(result.types().getFieldTypes("Name").refs()).extracting(TypeRef::qualifiedName)
      .containsExactly("Строка");
    assertThat(result.types().getFieldTypes("Next").refs()).extracting(TypeRef::qualifiedName)
      .contains("Структура");
  }

  @ParameterizedTest
  @ValueSource(strings = {"\"ready\"", "\"Unrelated\"", "Receiver.Unrelated", "Receiver.Child.Unrelated",
    "Receiver[\"Unrelated\"]", "Unrelated()"})
  void localResultDoesNotAnalyzeUnrelatedModuleState(String expression) {
    var document = document("""
      Var Unrelated;
      Procedure Initialize()
        Unrelated = Recursive();
      EndProcedure
      Function Recursive()
        Result = New Structure;
        Result.Insert("Next", Recursive());
        Return Result;
      EndFunction
      Function Unrelated()
        Return "ready";
      EndFunction
      Function Independent()
        // Unrelated is a name in a comment, not a variable read.
        Receiver = New Structure("Unrelated, Child", "ready", New Structure("Unrelated", "ready"));
        Result = %s;
        Return Result;
      EndFunction
      """.formatted(expression));

    assertThat(recursiveBodiesForIndependent(document)).isZero();
  }

  @Test
  void localDeclarationShadowsTheModuleVariable() {
    var document = document("""
      Var Unrelated;
      Procedure Initialize()
        Unrelated = Recursive();
      EndProcedure
      Function Recursive()
        Return 1;
      EndFunction
      Function Independent()
        Var Unrelated;
        Unrelated = "ready";
        Return Unrelated;
      EndFunction
      """);

    assertThat(recursiveBodiesForIndependent(document)).isZero();
  }

  @Test
  void variableUsedInsideAnIndexExpressionKeepsItsWriters() {
    var source = """
      Var Unrelated;
      Procedure Initialize()
        Unrelated = Recursive();
      EndProcedure
      Function Recursive()
        Return 0;
      EndFunction
      Function Independent()
        Receiver = New Array;
        Receiver.Add("ready");
        Result = Receiver[Unrelated];
        Return Result;
      EndFunction
      """;
    var document = document(source);
    var line = source.substring(0, source.indexOf("Result = Receiver[")).lines().count() - 1;

    assertThat(typeService.expressionTypesAt(document,
      new Position((int) line, "  Result = Receiver[".length() + 1)).refs())
      .extracting(TypeRef::qualifiedName).contains("Число");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void cellWriterDistinguishesMemberNamesFromIndexVariables(boolean indexAccess) {
    var receiver = indexAccess
      ? "Receiver = New Array; Receiver.Add(\"ready\");"
      : "Receiver = New Structure(\"Unrelated\", \"ready\");";
    var access = indexAccess ? "Receiver[Unrelated]" : "Receiver.Unrelated";
    var document = document("""
      Var Needed;
      Procedure WriteNeeded()
        Var Unrelated;
        Needed = "ready";
        Unrelated = Recursive();
        %s
        Needed = %s;
      EndProcedure
      Function Recursive()
        Return 0;
      EndFunction
      Function Independent()
        Value = Needed;
        Return Value;
      EndFunction
      """.formatted(receiver, access));

    assertThat(recursiveBodiesForIndependent(document, "Строка", "Неопределено") > 0)
      .as("whether the access reads the variable Unrelated")
      .isEqualTo(indexAccess);
  }

  private int recursiveBodiesForIndependent(DocumentContext document) {
    return recursiveBodiesForIndependent(document, "Строка");
  }

  private int recursiveBodiesForIndependent(DocumentContext document, String... expectedTypes) {
    var method = document.getSymbolTree().getMethodSymbol("Independent").orElseThrow();
    ExpressionTypeInferencer target = AopTestUtils.getUltimateTargetObject(inferencer);
    var original = (ReturnTypeFromBodyInference) ReflectionTestUtils.getField(target, "returnTypeFromBodyInference");
    var counted = spy(original);
    var unrelatedCalls = new AtomicInteger();
    doAnswer(invocation -> {
      var called = (MethodSymbol) invocation.getArgument(0);
      if (called.getName().equals("Recursive")) {
        unrelatedCalls.incrementAndGet();
      }
      return invocation.callRealMethod();
    }).when(counted).of(any(), any(), any(), any());
    ReflectionTestUtils.setField(target, "returnTypeFromBodyInference", counted);
    try {
      assertThat(inferencer.computeReturnTypes(method).types().refs())
        .extracting(TypeRef::qualifiedName).containsExactlyInAnyOrder(expectedTypes);
      return unrelatedCalls.get();
    } finally {
      ReflectionTestUtils.setField(target, "returnTypeFromBodyInference", original);
    }
  }

  @Test
  void forCounterDefinesTheModuleVariable() {
    var document = document("""
      Var Counter;
      Procedure Write()
        For Counter = 1 To 3 Do
        EndDo;
      EndProcedure
      Function Read()
        Result = Counter;
        Return Result;
      EndFunction
      """);

    assertThat(inferencer.computeReturnTypes(document.getSymbolTree().getMethodSymbol("Read").orElseThrow())
      .types().refs()).extracting(TypeRef::qualifiedName).contains("Число");
  }

  @ParameterizedTest
  @ValueSource(strings = {"For Unrelated = 1 To 3 Do", "For Each Unrelated In New Array Do"})
  void loopBindingDoesNotReadItsOtherWriters(String loop) {
    var document = document(loopWriterSource(loop));

    assertThat(recursiveBodiesForIndependent(document, "Строка", "Неопределено")).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"For Unrelated = Unrelated To 3 Do",
    "For Each Unrelated In Unrelated Do", "For Counter = 1 To Unrelated Do",
    "For Counter = 1 To 3 Do\n Log(Unrelated);"})
  void loopInputStillReadsItsWriters(String loop) {
    var document = document(loopWriterSource(loop));

    assertThat(recursiveBodiesForIndependent(document, "Строка", "Неопределено")).isPositive();
  }

  private static String loopWriterSource(String loop) {
    return """
      Var Needed;
      Var Unrelated;
      Procedure WriteOther()
        Unrelated = Recursive();
      EndProcedure
      Function Recursive()
        Return 1;
      EndFunction
      Procedure WriteNeeded()
        Needed = "ready";
        %s
          Needed = "ready";
        EndDo;
      EndProcedure
      Function Independent()
        Result = Needed;
        Return Result;
      EndFunction
      """.formatted(loop);
  }

  @Test
  void foreachBindingDefinesTheModuleVariable() {
    var document = document("""
      Var Item;
      Procedure Write()
        For Each Item In New Structure("Key", 1) Do
        EndDo;
      EndProcedure
      Function Read()
        Result = Item;
        Return Result;
      EndFunction
      """);

    assertThat(inferencer.computeReturnTypes(document.getSymbolTree().getMethodSymbol("Read").orElseThrow())
      .types().refs()).extracting(TypeRef::qualifiedName).contains("КлючИЗначение");
  }

  @Test
  void nonMutatingCallStatementKeepsTheModuleReceiver() {
    var source = """
      Var Shared;
      Procedure Write()
        Shared = New Structure("Key", 1);
      EndProcedure
      Procedure Read()
        Shared.Count();
      EndProcedure
      """;
    var document = document(source);
    var line = source.substring(0, source.indexOf("Shared.Count")).lines().count() - 1;
    var shared = document.getSymbolTree().getModuleVariableVisibleFromMethods("Shared").orElseThrow();

    assertThat(typeService.typesOfVariableAt(shared, new Position((int) line, 3)).refs())
      .extracting(TypeRef::qualifiedName).contains("Структура");
  }

  @Test
  void provisionalReturnTracksModuleCellGrowth() {
    var document = document("""
      Var ZResult;
      Var ASeed;
      Procedure Initialize()
        ZResult = GetSeed();
      EndProcedure
      Function GetSeed()
        Return ASeed;
      EndFunction
      Function Read()
        Return ZResult;
      EndFunction
      ASeed = "ready";
      """);
    var method = document.getSymbolTree().getMethodSymbol("Read").orElseThrow();

    var result = inferencer.computeReturnTypes(method);

    assertThat(result.types().refs()).extracting(TypeRef::qualifiedName).contains("Строка");
    assertThat(result.incomplete()).isFalse();
    assertThat(inferencer.computeReturnTypes(method).types()).isEqualTo(result.types());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void newlyDiscoveredInputsDoNotLeaveTemporaryArithmeticTypes(boolean numericWriter) {
    var document = document("""
      Var ZResult;
      Var ASeed;
      Procedure Initialize()
        ZResult = GetSeed();
      EndProcedure
      Function GetSeed()
        Return ASeed + "!";
      EndFunction
      Function Read()
        Return ZResult;
      EndFunction
      %s
      ASeed = "ready";
      """.formatted(numericWriter ? "Procedure Numeric() ZResult = 42; EndProcedure" : ""));

    var result = inferencer.computeReturnTypes(document.getSymbolTree().getMethodSymbol("Read").orElseThrow());

    assertThat(result.types().refs()).extracting(TypeRef::qualifiedName).contains("Строка");
    assertThat(result.types().refs().stream().anyMatch(type -> type.qualifiedName().equals("Число")))
      .as("only a real numeric writer contributes Number").isEqualTo(numericWriter);
    assertThat(result.incomplete()).isFalse();
  }

  @Test
  void moduleVariableReadsAreCaseInsensitive() {
    var document = document("""
      Var Shared;
      Procedure Initialize()
        Shared = StringValue();
      EndProcedure
      Function StringValue()
        Return "ready";
      EndFunction
      Function Read()
        Result = sHaReD;
        Return Result;
      EndFunction
      """);

    assertThat(inferencer.computeReturnTypes(document.getSymbolTree().getMethodSymbol("Read").orElseThrow())
      .types().refs()).extracting(TypeRef::qualifiedName).contains("Строка");
  }

  @Test
  void moduleVariableMutationsStillContributeToTheResult() {
    var document = document("""
      Var Shared;
      Procedure Initialize()
        Shared = New Structure;
      EndProcedure
      Function Read()
        Shared.Insert("Answer", 42);
        Result = Shared.Answer;
        Return Result;
      EndFunction
      """);

    assertThat(inferencer.computeReturnTypes(document.getSymbolTree().getMethodSymbol("Read").orElseThrow())
      .types().refs()).extracting(TypeRef::qualifiedName).containsExactly("Число");
  }

  @Test
  void unusedModuleStateRemainsAvailableAtItsDeclaration() {
    var document = document("""
      Var Shared;
      Procedure WriteNumber()
        Shared = 1;
      EndProcedure
      Function Independent()
        Result = "ready";
        Return Result;
      EndFunction
      Shared = False;
      """);
    assertThat(inferencer.computeReturnTypes(document.getSymbolTree().getMethodSymbol("Independent").orElseThrow())
      .types().refs()).extracting(TypeRef::qualifiedName).containsExactly("Строка");
    var shared = document.getSymbolTree().getVariablesOfScope(document.getSymbolTree().getModule()).stream()
      .filter(variable -> variable.getName().equals("Shared")).findFirst().orElseThrow();

    assertThat(typeService.typesOfVariableAt(shared, shared.getSelectionRange().getStart()).refs())
      .extracting(TypeRef::qualifiedName).contains("Булево", "Число");
  }

  @Test
  void aNestedReturnReadingAnUnpublishedCellStaysProvisional() {
    var document = document("""
      Var Shared;
      Function ReadCell()
        Return Shared;
      EndFunction
      Function Independent()
        Shared = 20;
        Result = "ready";
        Return Result;
      EndFunction
      Shared = 10;
      """);
    var tree = document.getSymbolTree();
    var shared = tree.getVariablesOfScope(tree.getModule()).getFirst();
    var reader = tree.getMethodSymbol("ReadCell").orElseThrow();
    var independent = tree.getMethodSymbol("Independent").orElseThrow();
    var caller = InferenceContext.neutral(document);
    caller.noteMissingData();

    ExpressionTypeInferencer target = AopTestUtils.getUltimateTargetObject(inferencer);
    ReflectionTestUtils.invokeMethod(target, "variableTypeAt", shared, shared.getSelectionRange().getStart(),
      false, caller);
    assertThat(caller.flowSession.cellsIncomplete()).as("the solver finished, but its inputs were incomplete").isFalse();

    ReflectionTestUtils.invokeMethod(target, "methodReturnType", reader, caller);
    ReflectionTestUtils.invokeMethod(target, "methodReturnType", independent, caller);

    assertThat(indexer.isIndexed(reader)).as("a nested reader must not publish the approximation as final").isFalse();
    assertThat(indexer.isIndexed(independent)).as("writing a cell does not require its previous value").isTrue();

    ReflectionTestUtils.invokeMethod(target, "methodReturnType", reader, InferenceContext.neutral(document));
    assertThat(indexer.isIndexed(reader)).as("a later complete cell calculation can publish the return").isTrue();
  }

  private DocumentContext document(String source) {
    var document = TestUtils.getDocumentContext(source, context);
    analyzer.clear(document.getUri());
    indexer.clear(document.getUri());
    expressions.clear(document.getUri());
    return document;
  }
}
