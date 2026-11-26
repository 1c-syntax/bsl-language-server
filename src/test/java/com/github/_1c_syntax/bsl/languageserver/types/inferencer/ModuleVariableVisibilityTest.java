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

import com.github._1c_syntax.bsl.languageserver.cfg.ControlFlowGraphIndex;
import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextPopulatedEvent;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.index.InferredExpressionTypeIndex;
import com.github._1c_syntax.bsl.languageserver.types.index.MethodReturnTypeIndexer;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.ConfigurationTypesProvider;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

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
  @Autowired
  private TypeRegistry typeRegistry;
  @Autowired
  private ConfigurationTypesProvider configurationTypes;
  @Autowired
  private ApplicationEventPublisher publisher;

  @BeforeEach
  void setUp() {
    initServerContext();
  }

  @Test
  void preparationPublishesCompleteCellsWithoutPublishingMethodReturns() {
    var document = document("""
      Var Shared;
      Procedure Fill()
        Shared = Seed();
      EndProcedure
      Function Seed()
        Return "ready";
      EndFunction
      """);
    var tree = document.getSymbolTree();
    var seed = tree.getMethodSymbol("Seed").orElseThrow();
    var shared = tree.getModuleVariableVisibleFromMethods("Shared").orElseThrow();
    ExpressionTypeInferencer target = AopTestUtils.getUltimateTargetObject(inferencer);
    var neutral = InferenceContext.neutral(document);
    neutral.contextualReturns.preparingCells = true;

    TypeSet value = ReflectionTestUtils.invokeMethod(target, "methodReturnType", seed, neutral);

    assertThat(value.refs()).extracting(TypeRef::qualifiedName).containsExactly("Строка");
    assertThat(indexer.isIndexed(seed)).isFalse();

    ReflectionTestUtils.invokeMethod(target, "prepareCells",
      new InferenceContext(document, ExecutionContext.SERVER), List.of(shared));

    assertThat(preparedCells(document).get(shared).refs()).extracting(TypeRef::qualifiedName)
      .containsExactlyInAnyOrder("Строка", "Неопределено");
    assertThat(indexer.isIndexed(seed)).isFalse();
  }

  @Test
  void preparationDoesNotPublishCellsAfterDepthCut() {
    var source = new StringBuilder("""
      Var Shared;
      Procedure Fill()
        Shared = Step0();
      EndProcedure
      """);
    for (var i = 0; i < 64; i++) {
      source.append("Function Step%d()%n".formatted(i));
      source.append(i == 63 ? "Return \"ready\";\n" : "Return Step%d();%n".formatted(i + 1));
      source.append("EndFunction\n");
    }
    var document = document(source.toString());
    var tree = document.getSymbolTree();
    var shared = tree.getModuleVariableVisibleFromMethods("Shared").orElseThrow();
    ExpressionTypeInferencer target = AopTestUtils.getUltimateTargetObject(inferencer);

    ReflectionTestUtils.invokeMethod(target, "prepareCells",
      new InferenceContext(document, ExecutionContext.SERVER), List.of(shared));

    assertThat(preparedCells(document)).doesNotContainKey(shared);
    assertThat(tree.getMethods()).noneMatch(indexer::isIndexed);
  }

  @Test
  void preparationDoesNotPublishCellsAfterCuttingARecursiveReturn() {
    var document = document("""
      Var Shared;
      Procedure Fill()
        Shared = Seed();
      EndProcedure
      Function Seed()
        Return ?(True, Seed(), "ready");
      EndFunction
      """);
    var tree = document.getSymbolTree();
    var shared = tree.getModuleVariableVisibleFromMethods("Shared").orElseThrow();
    ExpressionTypeInferencer target = AopTestUtils.getUltimateTargetObject(inferencer);

    ReflectionTestUtils.invokeMethod(target, "prepareCells",
      new InferenceContext(document, ExecutionContext.SERVER), List.of(shared));

    assertThat(preparedCells(document)).doesNotContainKey(shared);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void deferredResolutionOnlySharesCompletePreparedCells(boolean complete) {
    var configuration = Path.of("src/test/resources/metadata/commonModuleCalls");
    initServerContext(configuration, false);
    context.getConfiguration();
    configurationTypes.tryRegister();
    var consumer = configuration.resolve("CommonModules/КлиентСерверныйМодуль/Ext/Module.bsl").toUri();
    var document = TestUtils.getDocumentContext(consumer, """
      Перем Кэш;

      Процедура Заполнить()
        Кэш = Семя();
      КонецПроцедуры

      Функция Семя()
        Возврат %s;
      КонецФункции

      Функция ПрочитатьКэш()
        Возврат Кэш;
      КонецФункции

      Функция Вход1() Экспорт
      #Если Сервер Тогда
        Возврат ?(Истина, ПрочитатьКэш(), СерверныйМодуль.Маркер());
      #КонецЕсли
      КонецФункции

      Функция Вход2() Экспорт
      #Если Сервер Тогда
        Возврат ?(Истина, ПрочитатьКэш(), СерверныйМодуль.Маркер());
      #КонецЕсли
      КонецФункции
      """.formatted(complete ? "1" : "?(Истина, Семя(), 1)"), context);
    var tree = document.getSymbolTree();
    var shared = tree.getModuleVariableVisibleFromMethods("Кэш").orElseThrow();
    var entryPoints = List.of(tree.getMethodSymbol("Вход1").orElseThrow(), tree.getMethodSymbol("Вход2").orElseThrow());
    assertThat(entryPoints).noneMatch(indexer::isIndexed);

    var supplier = configuration.resolve("CommonModules/СерверныйМодуль/Ext/Module.bsl").toUri();
    TestUtils.getDocumentContext(supplier, """
      Функция Маркер() Экспорт
        Возврат "готово";
      КонецФункции
      """, context);
    analyzer.clear(document.getUri());
    var attempts = new AtomicInteger();
    ExpressionTypeInferencer target = AopTestUtils.getUltimateTargetObject(inferencer);
    VariableFlowAnalyzer flow = AopTestUtils.getUltimateTargetObject(analyzer);
    var observed = spy(flow);
    doAnswer(invocation -> {
      List<VariableSymbol> variables = invocation.getArgument(1);
      FlowInputs inputs = invocation.getArgument(2);
      if (inputs.executionContext() == ExecutionContext.SERVER && variables.contains(shared)) {
        attempts.incrementAndGet();
      }
      return invocation.callRealMethod();
    }).when(observed).ensureCellsFor(eq(document), anyList(), any(FlowInputs.class), any(BooleanSupplier.class));
    ReflectionTestUtils.setField(target, "variableFlowAnalyzer", observed);
    try {
      publisher.publishEvent(new ServerContextPopulatedEvent(context));
    } finally {
      ReflectionTestUtils.setField(target, "variableFlowAnalyzer", analyzer);
    }

    var expectedReturnTypes = complete ? List.of("Строка", "Число") : List.of("Строка");
    assertThat(entryPoints).allSatisfy(method -> assertThat(typeService.getReturnTypes(method).refs())
      .extracting(TypeRef::qualifiedName).containsAll(expectedReturnTypes));
    if (complete) {
      assertThat(attempts).hasValue(1);
      assertThat(preparedCells(document)).hasEntrySatisfying(shared, types -> assertThat(types.refs())
        .extracting(TypeRef::qualifiedName).contains("Число"));
    } else {
      assertThat(attempts.get()).isPositive();
      assertThat(preparedCells(document)).doesNotContainKey(shared);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void preparationDoesNotPublishCellsWithAnExternalTypeComment(boolean onDeclaration) {
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    var link = " // См. ОбщегоНазначения.ЗначениеВМассиве";
    var document = document("""
      Перем Общая;%s
      Процедура Заполнить()
        Общая = 1;%s
      КонецПроцедуры
      """.formatted(onDeclaration ? link : "", onDeclaration ? "" : link));
    var shared = document.getSymbolTree().getModuleVariableVisibleFromMethods("Общая").orElseThrow();
    ExpressionTypeInferencer target = AopTestUtils.getUltimateTargetObject(inferencer);

    ReflectionTestUtils.invokeMethod(target, "prepareCells",
      new InferenceContext(document, ExecutionContext.SERVER), List.of(shared));

    assertThat(preparedCells(document)).doesNotContainKey(shared);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void preparationFollowsLocalCommentLinksToTheirReturnDescription(boolean externalReturn) {
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    var returnDescription = externalReturn ? "См. ОбщегоНазначения.ЗначениеВМассиве" : "Строка";
    var document = document("""
      Перем Общая;
      Процедура Заполнить()
        Общая = 1; // См. ТипЗначения
      КонецПроцедуры
      // Возвращаемое значение:
      //  %s
      Функция ТипЗначения()
        Возврат "готово";
      КонецФункции
      """.formatted(returnDescription));
    var shared = document.getSymbolTree().getModuleVariableVisibleFromMethods("Общая").orElseThrow();
    ExpressionTypeInferencer target = AopTestUtils.getUltimateTargetObject(inferencer);

    ReflectionTestUtils.invokeMethod(target, "prepareCells",
      new InferenceContext(document, ExecutionContext.SERVER), List.of(shared));

    if (externalReturn) {
      assertThat(preparedCells(document)).doesNotContainKey(shared);
    } else {
      assertThat(preparedCells(document).get(shared).refs()).extracting(TypeRef::qualifiedName).contains("Строка");
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void preparationChecksTypeLinksOfParametersReadByTheWriter(boolean externalType) {
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    var parameterType = externalType ? "См. ОбщегоНазначения.ЗначениеВМассиве" : "Число";
    var document = document("""
      Перем Общая;
      Процедура Заполнить()
        Общая = ЗначениеДляЗаписи(1);
      КонецПроцедуры
      // Параметры:
      //  Источник - %s
      Функция ЗначениеДляЗаписи(Источник)
        Возврат Источник;
      КонецФункции
      """.formatted(parameterType));
    var shared = document.getSymbolTree().getModuleVariableVisibleFromMethods("Общая").orElseThrow();
    ExpressionTypeInferencer target = AopTestUtils.getUltimateTargetObject(inferencer);

    ReflectionTestUtils.invokeMethod(target, "prepareCells",
      new InferenceContext(document, ExecutionContext.SERVER), List.of(shared));

    if (externalType) {
      assertThat(preparedCells(document)).doesNotContainKey(shared);
    } else {
      assertThat(preparedCells(document).get(shared).refs()).extracting(TypeRef::qualifiedName).contains("Число");
    }
  }

  @Test
  void preparationChecksTheTypeCommentOfAnOwnExportedProperty() {
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    TestUtils.getDocumentContextFromFile(PATH_TO_METADATA + "/CommonModules/ОбщегоНазначения/Ext/Module.bsl");
    var document = TestUtils.getDocumentContextFromFile(PATH_TO_METADATA
      + "/Catalogs/СправочникСМенеджером/Ext/ObjectModule.bsl");
    var owner = typeRegistry.resolve("СправочникОбъект.СправочникСМенеджером").orElseThrow();
    var members = typeRegistry.getMembers(owner, document.getFileType());
    var external = members.stream().filter(member -> member.matches("ИтогРасчёта")).findFirst().orElseThrow();
    var local = members.stream().filter(member -> member.matches("Данные")).findFirst().orElseThrow();
    ExpressionTypeInferencer target = AopTestUtils.getUltimateTargetObject(inferencer);
    var isolated = new InferenceContext(document, ExecutionContext.SERVER);
    isolated.contextualReturns.preparingCells = true;

    TypeSet localTypes = ReflectionTestUtils.invokeMethod(target, "typesOfMember", local, "Данные",
      MemberKind.PROPERTY, TypeSet.EMPTY, isolated);
    assertThat(localTypes.refs()).extracting(TypeRef::qualifiedName).containsExactly("Структура");
    assertThat(isolated.sawMissing()).isFalse();

    TypeSet externalTypes = ReflectionTestUtils.invokeMethod(target, "typesOfMember", external, "ИтогРасчёта",
      MemberKind.PROPERTY, TypeSet.EMPTY, isolated);
    assertThat(externalTypes.refs()).extracting(TypeRef::qualifiedName).containsExactly("Массив");
    assertThat(isolated.sawMissing()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void preparationCanShareOnlyIndependentlySettledInputs(boolean independent) {
    var root = Path.of("src/test/resources/metadata/commonModuleCalls");
    initServerContext(root, false);
    context.getConfiguration();
    configurationTypes.tryRegister();
    var supplier = root.resolve("CommonModules/СерверныйМодуль/Ext/Module.bsl").toUri();
    TestUtils.getDocumentContext(supplier, """
      Функция Маркер() Экспорт
        Возврат "готово";
      КонецФункции
      """, context);
    var consumer = root.resolve("CommonModules/КлиентСерверныйМодуль/Ext/Module.bsl").toUri();
    var document = TestUtils.getDocumentContext(consumer, """
      Перем Источник;
      Перем Кэш;

      Процедура ЗаполнитьИсточник()
        Источник = %s;
      КонецПроцедуры

      Процедура ЗаполнитьКэш()
        Кэш = Источник;
      КонецПроцедуры
      """.formatted(independent ? "\"готово\"" : "СерверныйМодуль.Маркер()"), context);
    var tree = document.getSymbolTree();
    var source = tree.getModuleVariableVisibleFromMethods("Источник").orElseThrow();
    var cache = tree.getModuleVariableVisibleFromMethods("Кэш").orElseThrow();
    ExpressionTypeInferencer target = AopTestUtils.getUltimateTargetObject(inferencer);
    var request = new InferenceContext(document, ExecutionContext.SERVER);
    if (independent) {
      ReflectionTestUtils.invokeMethod(target, "prepareCells", request, List.of(source));
    } else {
      FlowInputs ordinary = ReflectionTestUtils.invokeMethod(target, "flowInputs", source, request);
      VariableFlowAnalyzer flow = AopTestUtils.getUltimateTargetObject(analyzer);
      flow.ensureCellsFor(document, List.of(source), ordinary, null);
    }
    assertThat(preparedCells(document)).hasEntrySatisfying(source, types -> assertThat(types.refs())
      .extracting(TypeRef::qualifiedName).contains("Строка")).doesNotContainKey(cache);

    ReflectionTestUtils.invokeMethod(target, "prepareCells",
      new InferenceContext(document, ExecutionContext.SERVER), List.of(cache));

    if (independent) {
      assertThat(preparedCells(document)).hasEntrySatisfying(cache, types -> assertThat(types.refs())
        .extracting(TypeRef::qualifiedName).contains("Строка"));
    } else {
      assertThat(preparedCells(document)).doesNotContainKey(cache);
    }
  }

  private Map<VariableSymbol, TypeSet> preparedCells(DocumentContext document) {
    VariableFlowAnalyzer target = AopTestUtils.getUltimateTargetObject(analyzer);
    Map<VariableSymbol, VariableFlowAnalyzer.SettledCell> cells =
      ReflectionTestUtils.invokeMethod(target, "cellsOf", document, ExecutionContext.SERVER);
    return cells.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().types()));
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

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void independentReceiverDoesNotAnalyzeUnrelatedModuleWrites(boolean parameter) {
    var document = document(parameter ? """
      Var Shared;
      // Параметры:
      //  Result - Массив
      Procedure Use(Result)
        Text = Result.Количество();
        Other = Result.Количество();
        Shared = Slow();
      EndProcedure
      Function Slow()
        Return "another value";
      EndFunction
      """ : """
      Var Shared;
      Procedure Use()
        Result = New Structure;
        Result.Insert("Name", "ready");
        Text = Result.Name;
        Other = Result.Name;
        Shared = Slow();
      EndProcedure
      Function Slow()
        Return "another value";
      EndFunction
      """);
    publisher.publishEvent(new ServerContextPopulatedEvent(context));

    var target = AopTestUtils.<ExpressionTypeInferencer>getUltimateTargetObject(inferencer);
    var original = (ReturnTypeFromBodyInference) ReflectionTestUtils.getField(target, "returnTypeFromBodyInference");
    var counted = spy(original);
    var flow = AopTestUtils.<VariableFlowAnalyzer>getUltimateTargetObject(analyzer);
    var originalGraphs = (ControlFlowGraphIndex) ReflectionTestUtils.getField(flow, "controlFlowGraphIndex");
    var graphs = spy(originalGraphs);
    var unrelatedCalls = new AtomicInteger();
    doAnswer(invocation -> {
      var called = (MethodSymbol) invocation.getArgument(0);
      if (called.getName().equals("Slow")) {
        unrelatedCalls.incrementAndGet();
      }
      return invocation.callRealMethod();
    }).when(counted).of(any(), any(), any(), any());
    ReflectionTestUtils.setField(target, "returnTypeFromBodyInference", counted);
    ReflectionTestUtils.setField(flow, "controlFlowGraphIndex", graphs);
    try {
      var member = typeService.memberAt(document, new Position(4, "  Text = Result.".length())).orElseThrow();

      assertThat(member.descriptor().returnTypes().refs()).extracting(TypeRef::qualifiedName)
        .containsExactly(parameter ? "Число" : "Строка");
      clearInvocations(graphs);

      var repeated = typeService.memberAt(document, new Position(5, "  Other = Result.".length())).orElseThrow();

      assertThat(repeated.descriptor().returnTypes()).isEqualTo(member.descriptor().returnTypes());
      verify(graphs, never()).graphOf(eq(document), any(BSLParser.CodeBlockContext.class), any());
      assertThat(unrelatedCalls).hasValue(0);
    } finally {
      ReflectionTestUtils.setField(flow, "controlFlowGraphIndex", originalGraphs);
      ReflectionTestUtils.setField(target, "returnTypeFromBodyInference", original);
    }
  }

  @Test
  void moduleReceiverImmediatelyAfterResetDoesNotAnalyzeOtherWriters() {
    var document = document("""
      Var Shared;
      Procedure Use()
        Shared = New Structure;
        Shared.Insert("Name", "ready");
        Shared.Insert("Size", 1);
      EndProcedure
      Procedure OtherWriter()
        Shared = Slow();
      EndProcedure
      Function Slow()
        Return "another value";
      EndFunction
      """);
    publisher.publishEvent(new ServerContextPopulatedEvent(context));

    var target = AopTestUtils.<ExpressionTypeInferencer>getUltimateTargetObject(inferencer);
    var original = (ReturnTypeFromBodyInference) ReflectionTestUtils.getField(target, "returnTypeFromBodyInference");
    var counted = spy(original);
    var unrelatedCalls = new AtomicInteger();
    doAnswer(invocation -> {
      var called = (MethodSymbol) invocation.getArgument(0);
      if (called.getName().equals("Slow")) {
        unrelatedCalls.incrementAndGet();
      }
      return invocation.callRealMethod();
    }).when(counted).of(any(), any(), any(), any());
    ReflectionTestUtils.setField(target, "returnTypeFromBodyInference", counted);
    try {
      var afterReset = typeService.receiverTypesAt(document, new Position(3, "  Shared.".length()));
      assertThat(afterReset.refs()).extracting(TypeRef::qualifiedName).containsExactly("Структура");
      assertThat(unrelatedCalls).hasValue(0);

      var afterCall = typeService.receiverTypesAt(document, new Position(4, "  Shared.".length()));
      assertThat(afterCall.refs()).extracting(TypeRef::qualifiedName).contains("Строка", "Структура");
      assertThat(unrelatedCalls.get()).isPositive();
    } finally {
      ReflectionTestUtils.setField(target, "returnTypeFromBodyInference", original);
    }
  }

  @Test
  void loopHeadersReuseParameterProjectionWithoutModuleVariables() {
    var document = document("""
      // Параметры:
      //  Data - Структура:
      //   * Items - Массив
      Procedure Use(Data)
        For Each First In Data.Items Do
        EndDo;
        For Each Second In Data.Items Do
        EndDo;
      EndProcedure
      """);
    publisher.publishEvent(new ServerContextPopulatedEvent(context));

    var flow = AopTestUtils.<VariableFlowAnalyzer>getUltimateTargetObject(analyzer);
    var reads = (ReceiverReadFlow) ReflectionTestUtils.getField(flow, "receiverReads");
    var original = (ReturnedLocalFlow) ReflectionTestUtils.getField(reads, "projections");
    var projections = spy(original);
    ReflectionTestUtils.setField(reads, "projections", projections);
    try {
      var first = typeService.receiverTypesAt(document, new Position(4, "  For Each First In Data.".length()));
      var second = typeService.receiverTypesAt(document, new Position(6, "  For Each Second In Data.".length()));

      assertThat(first.refs()).extracting(TypeRef::qualifiedName).containsExactly("Структура");
      assertThat(second).isEqualTo(first);
      verify(projections).readFacts(eq(document), any(), any(), any());
      verify(projections, never()).readParameterBeforeLoop(eq(document), any(), any(), any(), any());
    } finally {
      ReflectionTestUtils.setField(reads, "projections", original);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void parameterReceiverInLoopUsesItsPredecessorFlow(boolean backEdgeWrite) {
    var document = document("""
      Var Shared;
      // Параметры:
      //  Data - Структура:
      //   * Items - Массив
      //   * Auxiliary - Число
      Procedure Use(Data)
        Temporary = Slow();
        Touch(Data.Auxiliary);
        For Each Item In Data.Items Do
          %s
        EndDo;
        Data = New Array;
        Shared = Data.Количество();
      EndProcedure
      Procedure Touch(Value)
        Value = 1;
      EndProcedure
      Function Slow()
        Return "another value";
      EndFunction
      """.formatted(backEdgeWrite ? "Data = New Array;" : "Temporary = Item;"));
    publisher.publishEvent(new ServerContextPopulatedEvent(context));

    var target = AopTestUtils.<ExpressionTypeInferencer>getUltimateTargetObject(inferencer);
    var original = (ReturnTypeFromBodyInference) ReflectionTestUtils.getField(target, "returnTypeFromBodyInference");
    var counted = spy(original);
    var unrelatedCalls = new AtomicInteger();
    doAnswer(invocation -> {
      var method = (MethodSymbol) invocation.getArgument(0);
      if (method.getName().equals("Slow")) {
        unrelatedCalls.incrementAndGet();
      }
      return invocation.callRealMethod();
    }).when(counted).of(any(), any(), any(), any());
    ReflectionTestUtils.setField(target, "returnTypeFromBodyInference", counted);
    try {
      var receiver = typeService.receiverTypesAt(document, new Position(8, "  For Each Item In Data.".length()));
      if (backEdgeWrite) {
        assertThat(receiver.refs()).extracting(TypeRef::qualifiedName).containsExactlyInAnyOrder("Структура", "Массив");
        assertThat(unrelatedCalls.get()).isPositive();
      } else {
        assertThat(receiver.refs()).extracting(TypeRef::qualifiedName).containsExactly("Структура");
        assertThat(unrelatedCalls).hasValue(0);
      }

      var afterLoop = typeService.receiverTypesAt(document, new Position(12, "  Shared = Data.".length()));
      assertThat(afterLoop.refs()).extracting(TypeRef::qualifiedName).containsExactly("Массив");
    } finally {
      ReflectionTestUtils.setField(target, "returnTypeFromBodyInference", original);
    }
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
