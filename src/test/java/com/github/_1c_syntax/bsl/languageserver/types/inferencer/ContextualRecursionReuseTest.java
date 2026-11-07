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
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.spy;

class ContextualRecursionReuseTest extends AbstractServerContextAwareTest {
  @Autowired
  private ExpressionTypeInferencer inferencer;

  @Test
  void aRootOutsideTheCalleesCycleDoesNotRefineItsOwnValue(TestReporter reporter) {
    var root = Path.of("src/test/resources/metadata/commonModuleCalls");
    initServerContext(root, false);
    var document = TestUtils.getDocumentContext(
      root.resolve("CommonModules/КлиентСерверныйМодуль/Ext/Module.bsl").toUri(), """
      Функция Корень(Условие)
      #Если Сервер Тогда
        Возврат РекурсивныйПоставщик(Условие);
      #Иначе
        Возврат 1;
      #КонецЕсли
      КонецФункции
      Функция РекурсивныйПоставщик(Условие)
        Если Условие Тогда
          Возврат РекурсивныйПоставщик(Условие);
        Иначе
          Возврат 1;
        КонецЕсли;
      КонецФункции
      """, context);
    var method = document.getSymbolTree().getMethodSymbol("Корень").orElseThrow();
    ExpressionTypeInferencer target = AopTestUtils.getUltimateTargetObject(inferencer);
    var original = (ReturnTypeFromBodyInference) ReflectionTestUtils.getField(target, "returnTypeFromBodyInference");
    var counted = spy(original);
    ReflectionTestUtils.setField(target, "returnTypeFromBodyInference", counted);
    try {
      assertThat(inferencer.computeReturnTypes(method).types().refs())
        .extracting(TypeRef::qualifiedName).containsExactly("Число");
      var rootEvaluations = mockingDetails(counted).getInvocations().stream()
        .filter(call -> call.getMethod().getName().equals("of") && call.getMethod().getParameterCount() == 4)
        .filter(call -> call.getArgument(0) == method).count();
      reporter.publishEntry("rootEvaluations", Long.toString(rootEvaluations));
      assertThat(rootEvaluations).as("the root approximation cannot close a cycle that never calls the root").isEqualTo(1);
    } finally {
      ReflectionTestUtils.setField(target, "returnTypeFromBodyInference", original);
    }
  }

  @ParameterizedTest
  @CsvSource({"4, false, false", "6, false, false", "8, false, false", "4, true, false", "6, true, false",
    "4, false, true", "6, false, true", "4, true, true", "6, true, true"})
  void differentPathsToTheSameRecursiveComponentDoNotExpandExponentially(int depth, boolean viaLocalVariable,
                                                                         boolean extraRightHop,
                                                                         TestReporter reporter) {
    var root = Path.of("src/test/resources/metadata/commonModuleCalls");
    initServerContext(root, false);
    var source = new StringBuilder("""
      Функция Корень(Условие)
      #Если Сервер Тогда
        Возврат Уровень0(Условие);
      #Иначе
        Возврат 1;
      #КонецЕсли
      КонецФункции
      """);
    for (var i = 0; i < depth; i++) {
      source.append("""
        Функция Уровень%d(Условие)
          Если Условие Тогда
            Возврат Корень(Условие);
          ИначеЕсли Условие Тогда
            Возврат Левая%d(Условие);
          Иначе
            Возврат Правая%d(Условие);
          КонецЕсли;
        КонецФункции
        Функция Левая%d(Условие)
          Возврат Уровень%d(Условие);
        КонецФункции
        Функция Правая%d(Условие)
          Возврат %s%d(Условие);
        КонецФункции
        """.formatted(i, i, i, i, i + 1, i, extraRightHop ? "Промежуточная" : "Уровень",
          extraRightHop ? i : i + 1));
      if (extraRightHop) {
        source.append("Функция Промежуточная%d(Условие) Возврат Уровень%d(Условие); КонецФункции\n"
          .formatted(i, i + 1));
      }
    }
    source.append("Функция Уровень%d(Условие) Возврат 1; КонецФункции".formatted(depth));
    if (viaLocalVariable) {
      var text = source.toString().replaceAll("Возврат (Уровень|Промежуточная)(\\d+)\\(Условие\\);",
        "Локальное = $1$2(Условие); Возврат Локальное;");
      source = new StringBuilder(text);
    }
    var document = TestUtils.getDocumentContext(
      root.resolve("CommonModules/КлиентСерверныйМодуль/Ext/Module.bsl").toUri(), source.toString(), context);
    var method = document.getSymbolTree().getMethodSymbol("Корень").orElseThrow();
    ExpressionTypeInferencer target = AopTestUtils.getUltimateTargetObject(inferencer);
    var original = (ReturnTypeFromBodyInference) ReflectionTestUtils.getField(target, "returnTypeFromBodyInference");
    var counted = spy(original);
    ReflectionTestUtils.setField(target, "returnTypeFromBodyInference", counted);
    try {
      var result = inferencer.computeReturnTypes(method);
      assertThat(result.types().refs()).extracting(TypeRef::qualifiedName).containsExactly("Число");
      var bodyEvaluations = mockingDetails(counted).getInvocations().stream()
        .filter(call -> call.getMethod().getName().equals("of") && call.getMethod().getParameterCount() == 4).count();
      reporter.publishEntry("diamondBodyEvaluations", Long.toString(bodyEvaluations));
      assertThat(bodyEvaluations).as("method bodies evaluated through different paths to a recursive component")
        .isLessThan(40L * (depth + 1));
    } finally {
      ReflectionTestUtils.setField(target, "returnTypeFromBodyInference", original);
    }
  }

  @Test
  void repeatedPathsThroughARecursiveComponentDoNotExpandExponentially(TestReporter reporter) {
    var root = Path.of("src/test/resources/metadata/commonModuleCalls");
    initServerContext(root, false);
    var source = new StringBuilder("""
      Функция Корень(Условие) Экспорт
      #Если Сервер Тогда
        Возврат Уровень0(Условие);
      #Иначе
        Возврат 1;
      #КонецЕсли
      КонецФункции
      """);
    var depth = 8;
    for (var i = 0; i < depth; i++) {
      source.append("""
        Функция Уровень%d(Условие)
          Если Условие Тогда
            Возврат Корень(Условие);
          ИначеЕсли Условие Тогда
            Возврат Уровень%d(Условие);
          Иначе
            Возврат Уровень%d(Условие);
          КонецЕсли;
        КонецФункции
        """.formatted(i, i + 1, i + 1));
    }
    source.append("Функция Уровень%d(Условие) Возврат 1; КонецФункции".formatted(depth));
    var document = TestUtils.getDocumentContext(
      root.resolve("CommonModules/КлиентСерверныйМодуль/Ext/Module.bsl").toUri(), source.toString(), context);
    var method = document.getSymbolTree().getMethodSymbol("Корень").orElseThrow();
    ExpressionTypeInferencer target = AopTestUtils.getUltimateTargetObject(inferencer);
    var original = (ReturnTypeFromBodyInference) ReflectionTestUtils.getField(target, "returnTypeFromBodyInference");
    var counted = spy(original);
    ReflectionTestUtils.setField(target, "returnTypeFromBodyInference", counted);
    try {
      clearInvocations(counted);
      var result = inferencer.computeReturnTypes(method);
      assertThat(result.types().refs()).extracting(TypeRef::qualifiedName).containsExactly("Число");
      var bodyEvaluations = mockingDetails(counted).getInvocations().stream()
        .filter(call -> call.getMethod().getName().equals("of") && call.getMethod().getParameterCount() == 4).count();
      reporter.publishEntry("bodyEvaluations", Long.toString(bodyEvaluations));
      assertThat(bodyEvaluations).as("method bodies evaluated for a shared recursive call graph")
        .isLessThan(24L * (depth + 2));
    } finally {
      ReflectionTestUtils.setField(target, "returnTypeFromBodyInference", original);
    }
  }
}
