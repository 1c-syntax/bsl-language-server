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
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.mdo.CommonModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.spy;

class CompilationProfileReuseTest extends AbstractServerContextAwareTest {
  @Autowired
  private ExpressionTypeInferencer inferencer;

  @Test
  void nextRootQueryReadsChangedModuleMetadata() {
    var root = Path.of("src/test/resources/metadata/commonModuleCalls");
    initServerContext(root, false);
    var original = TestUtils.getDocumentContext(
      root.resolve("CommonModules/КлиентСерверныйМодуль/Ext/Module.bsl").toUri(), """
      Функция Проверка()
      #Если Сервер Тогда
        Возврат 1;
      #Иначе
        Возврат "";
      #КонецЕсли
      КонецФункции
      """, context);
    var document = spy(original);
    var method = spy(original.getSymbolTree().getMethodSymbol("Проверка").orElseThrow());
    doReturn(document).when(method).getOwner();
    doReturn(Optional.of(CommonModule.builder().name("Тест").server(true).build()))
      .when(document).getMdObject();
    assertThat(inferencer.computeReturnTypes(method).types().refs())
      .extracting(TypeRef::qualifiedName).containsExactly("Число");

    doReturn(Optional.of(CommonModule.builder().name("Тест").clientManagedApplication(true).build()))
      .when(document).getMdObject();
    assertThat(inferencer.computeReturnTypes(method).types().refs())
      .extracting(TypeRef::qualifiedName).containsExactly("Строка");
  }

  @Test
  void bodyCompilationContextsAreResolvedOncePerRootQuery(TestReporter reporter) {
    var root = Path.of("src/test/resources/metadata/commonModuleCalls");
    initServerContext(root, false);
    var document = TestUtils.getDocumentContext(
      root.resolve("CommonModules/КлиентСерверныйМодуль/Ext/Module.bsl").toUri(), """
      Функция Проверка(Условие)
      #Если Сервер Тогда
        Если Условие Тогда
          Возврат Поставщик();
        КонецЕсли;
        Возврат Обертка();
      #Иначе
        Возврат 1;
      #КонецЕсли
      КонецФункции
      Функция Поставщик()
        Локальное = 1;
        Возврат Локальное;
      КонецФункции
      Функция Обертка()
        Возврат Поставщик();
      КонецФункции
      """, context);
    var method = document.getSymbolTree().getMethodSymbol("Проверка").orElseThrow();
    ExpressionTypeInferencer target = AopTestUtils.getUltimateTargetObject(inferencer);
    var original = (CompilationContextResolver) ReflectionTestUtils.getField(target, "compilationContextResolver");
    CompilationContextResolver unwrapped = AopTestUtils.getUltimateTargetObject(original);
    var counted = spy(unwrapped);
    var returns = (ReturnTypeFromBodyInference) ReflectionTestUtils.getField(target, "returnTypeFromBodyInference");
    var originalReturnResolver = ReflectionTestUtils.getField(returns, "compilationContextResolver");
    ReflectionTestUtils.setField(target, "compilationContextResolver", counted);
    ReflectionTestUtils.setField(returns, "compilationContextResolver", counted);
    try {
      assertThat(inferencer.computeReturnTypes(method).types().refs()).extracting(TypeRef::qualifiedName).containsExactly("Число");
      var calls = mockingDetails(counted).getInvocations().stream()
        .filter(call -> call.getMethod().getName().equals("possibleContexts"))
        .filter(call -> call.getArgument(1) instanceof BSLParser.CodeBlockContext).toList();
      var bodies = calls.stream().map(call -> call.getArgument(1)).distinct().count();
      reporter.publishEntry("bodyContextQueries", Integer.toString(calls.size()));
      reporter.publishEntry("distinctBodies", Long.toString(bodies));
      assertThat(bodies).isEqualTo(3);
      assertThat(calls).as("body contexts are shared by all modes and repeated local calls").hasSize((int) bodies);
    } finally {
      ReflectionTestUtils.setField(target, "compilationContextResolver", original);
      ReflectionTestUtils.setField(returns, "compilationContextResolver", originalReturnResolver);
    }
  }
}