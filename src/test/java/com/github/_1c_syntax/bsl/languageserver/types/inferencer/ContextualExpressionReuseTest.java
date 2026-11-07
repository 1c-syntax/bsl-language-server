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
import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.types.DereferenceLocator;
import com.github._1c_syntax.bsl.languageserver.types.index.InferredExpressionTypeIndex;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.BslExpression;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.spy;

class ContextualExpressionReuseTest extends AbstractServerContextAwareTest {
  @Autowired
  private ExpressionTypeInferencer inferencer;
  @Autowired
  private InferredExpressionTypeIndex index;

  @Test
  void doesNotPublishBeforePopulation() {
    assertNotCached("Функция Фабрика() Возврат Новый Структура; КонецФункции", false);
  }

  @Test
  void doesNotPublishARecursiveCut() {
    assertNotCached("""
      Функция Фабрика(Условие = Ложь)
        Если Условие Тогда
          Возврат Новый Структура;
        Иначе
          Возврат Фабрика(Истина);
        КонецЕсли;
      КонецФункции
      """, true);
  }

  @Test
  void doesNotPublishADepthCut() {
    var source = new StringBuilder("Функция Фабрика() Возврат Шаг0(); КонецФункции\n");
    for (var i = 0; i < 100; i++) {
      source.append("Функция Шаг").append(i).append("() Возврат Шаг").append(i + 1)
        .append("(); КонецФункции\n");
    }
    source.append("Функция Шаг100() Возврат Новый Структура; КонецФункции\n");
    assertNotCached(source.toString(), true);
  }

  @Test
  void recursiveAssignmentDoesNotPublishItsPartialFlowFacts() {
    var document = assertNotCached("""
      Функция Фабрика()
        Локальная = Фабрика();
        Возврат Локальная;
      КонецФункции
      """, true);
    ExpressionTypeInferencer target = AopTestUtils.getUltimateTargetObject(inferencer);
    var flow = (VariableFlowAnalyzer) ReflectionTestUtils.getField(target, "variableFlowAnalyzer");
    VariableFlowAnalyzer flowTarget = AopTestUtils.getUltimateTargetObject(flow);
    var byUri = (java.util.Map<?, ?>) ReflectionTestUtils.getField(flowTarget, "contextualFactsByUri");
    var modes = (java.util.Map<?, ?>) byUri.get(document.getUri());
    if (modes != null) {
      assertThat(modes.values()).allSatisfy(byBody -> assertThat((java.util.Map<?, ?>) byBody).isEmpty());
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void retriesAfterAFlowFailureInsteadOfRememberingItsEmptyFallback(boolean missingPlacement) {
    var document = document("""
      Функция Фабрика()
        Локальная = Новый Массив;
        Возврат Локальная;
      КонецФункции
      """);
    var expression = receiver(document);
    ExpressionTypeInferencer target = AopTestUtils.getUltimateTargetObject(inferencer);
    var original = (VariableFlowAnalyzer) ReflectionTestUtils.getField(target, "variableFlowAnalyzer");
    VariableFlowAnalyzer flowTarget = AopTestUtils.getUltimateTargetObject(original);
    var counted = spy(flowTarget);
    var failure = missingPlacement ? doReturn((Object) null) : doThrow(new IllegalStateException("transient flow failure"));
    failure.doCallRealMethod().when(counted)
      .typeAtRoot(any(DocumentContext.class), any(ParserRuleContext.class), any(VariableSymbol.class),
        any(VariableFlowAnalyzer.FlowInputs.class), any(java.util.function.BooleanSupplier.class));
    var populated = ReflectionTestUtils.getField(target, "workspacePopulated");
    ReflectionTestUtils.setField(target, "variableFlowAnalyzer", counted);
    ReflectionTestUtils.setField(target, "workspacePopulated", true);
    try {
      inferencer.infer(expression, document, ExecutionContext.SERVER);
      assertThat(index.contextualQuery(document.getUri(), expression.getRepresentingAst(),
        ExecutionContext.SERVER).cached()).as("a swallowed runtime failure is not a completed inference").isNull();
      assertThat(inferencer.infer(expression, document, ExecutionContext.SERVER).refs())
        .extracting(TypeRef::qualifiedName).containsExactly("Массив");
    } finally {
      ReflectionTestUtils.setField(target, "variableFlowAnalyzer", original);
      ReflectionTestUtils.setField(target, "workspacePopulated", populated);
    }
  }

  @Test
  void aFailedFunctionBodyIsReportedAsIncomplete() {
    var document = document("Функция Фабрика() Возврат Новый Структура; КонецФункции");
    var method = document.getSymbolTree().getMethodSymbol("Фабрика").orElseThrow();
    ExpressionTypeInferencer target = AopTestUtils.getUltimateTargetObject(inferencer);
    var original = (ReturnTypeFromBodyInference) ReflectionTestUtils.getField(target, "returnTypeFromBodyInference");
    var counted = spy(original);
    doThrow(new IllegalStateException("transient body failure")).when(counted).of(any(), any(), any(), any());
    ReflectionTestUtils.setField(target, "returnTypeFromBodyInference", counted);
    try {
      assertThat(inferencer.computeReturnTypes(method).incomplete()).isTrue();
    } finally {
      ReflectionTestUtils.setField(target, "returnTypeFromBodyInference", original);
    }
  }

  private DocumentContext assertNotCached(String declarations, boolean populated) {
    var document = document(declarations);
    var expression = receiver(document);
    ExpressionTypeInferencer target = AopTestUtils.getUltimateTargetObject(inferencer);
    var original = ReflectionTestUtils.getField(target, "workspacePopulated");
    ReflectionTestUtils.setField(target, "workspacePopulated", populated);
    try {
      inferencer.infer(expression, document, ExecutionContext.SERVER);
      assertThat(index.contextualQuery(document.getUri(), expression.getRepresentingAst(),
        ExecutionContext.SERVER).cached()).isNull();
    } finally {
      ReflectionTestUtils.setField(target, "workspacePopulated", original);
    }
    return document;
  }

  private DocumentContext document(String declarations) {
    var root = Path.of("src/test/resources/metadata/commonModuleCalls");
    initServerContext(root, false);
    return TestUtils.getDocumentContext(
      root.resolve("CommonModules/КлиентСерверныйМодуль/Ext/Module.bsl").toUri(), declarations + """

      Процедура Проверка()
        Результат = Фабрика().Количество();
      КонецПроцедуры
      """, context);
  }

  private static BslExpression receiver(DocumentContext document) {
    var call = Trees.findAllRuleNodes(document.getAst(), BSLParser.RULE_methodCall).stream()
      .map(BSLParser.MethodCallContext.class::cast).findFirst().orElseThrow();
    return DereferenceLocator.locate(call.methodName().IDENTIFIER()).getLeft();
  }

  @Test
  void reusesCompletedRootInTheSameModeAndKeepsOtherModesIndependent() {
    var document = document("""
      Функция Фабрика()
      #Если Сервер Тогда
        Возврат Новый Структура;
      #Иначе
        Возврат Новый Массив;
      #КонецЕсли
      КонецФункции
      """);
    var expression = receiver(document);
    ExpressionTypeInferencer target = AopTestUtils.getUltimateTargetObject(inferencer);
    var original = (ReturnTypeFromBodyInference) ReflectionTestUtils.getField(target, "returnTypeFromBodyInference");
    var counted = spy(original);
    var populated = ReflectionTestUtils.getField(target, "workspacePopulated");
    ReflectionTestUtils.setField(target, "returnTypeFromBodyInference", counted);
    ReflectionTestUtils.setField(target, "workspacePopulated", true);
    try {
      var server = inferencer.infer(expression, document, ExecutionContext.SERVER);
      assertThat(server.refs()).extracting(TypeRef::qualifiedName).containsExactly("Структура");
      var firstCalls = mockingDetails(counted).getInvocations().size();
      assertThat(firstCalls).isPositive();
      assertThat(inferencer.infer(expression, document, ExecutionContext.SERVER)).isSameAs(server);
      assertThat(mockingDetails(counted).getInvocations()).hasSize(firstCalls);
      assertThat(inferencer.infer(expression, document, ExecutionContext.THIN_CLIENT).refs())
        .extracting(TypeRef::qualifiedName).containsExactly("Массив");
      index.clear(document.getUri());
      assertThat(inferencer.infer(expression, document, ExecutionContext.SERVER).refs())
        .extracting(TypeRef::qualifiedName).containsExactly("Структура");
      assertThat(mockingDetails(counted).getInvocations().size()).isGreaterThan(firstCalls);
    } finally {
      ReflectionTestUtils.setField(target, "returnTypeFromBodyInference", original);
      ReflectionTestUtils.setField(target, "workspacePopulated", populated);
    }
  }
}
