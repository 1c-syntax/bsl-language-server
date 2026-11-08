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
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.index.InferredExpressionTypeIndex;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.spy;

@CleanupContextBeforeClassAndAfterClass
class ModeIndependentInferenceTest extends AbstractServerContextAwareTest {
  private static final Path ROOT = Path.of("src/test/resources/metadata/commonModuleCalls");
  @Autowired private TypeService types;
  @Autowired private TypeRegistry registry;
  @Autowired private ExpressionTypeInferencer inferencer;
  @Autowired private VariableFlowAnalyzer flow;
  @Autowired private InferredExpressionTypeIndex expressions;

  @BeforeEach
  void initWorkspace() {
    initServerContext(ROOT);
  }

  @Test
  void localStructureFieldsAreCalculatedOnceAcrossRootModes() {
    var document = form("""
      &НаКлиентеНаСервереБезКонтекста
      Функция Построить()
        ДанныеПостроителя = Новый Структура;
        ДанныеПостроителя.Вставить("Ключ", 42);
        СнимокПроверки = ДанныеПостроителя;
        Возврат СнимокПроверки;
      КонецФункции
      """);
    ExpressionTypeInferencer target = AopTestUtils.getUltimateTargetObject(inferencer);
    var previousPopulation = ReflectionTestUtils.getField(target, "workspacePopulated");
    var previousRegistry = ReflectionTestUtils.getField(target, "typeRegistry");
    TypeRegistry actual = AopTestUtils.getUltimateTargetObject(registry);
    var counted = spy(actual);
    ReflectionTestUtils.setField(target, "typeRegistry", counted);
    ReflectionTestUtils.setField(target, "workspacePopulated", true);
    try {
      expressions.clear(document.getUri());
      flow.clear(document.getUri());
      var position = referencePosition(document);
      var server = types.expressionTypesAt(document, position, ExecutionContext.SERVER);
      assertThat(server.refs()).extracting(TypeRef::qualifiedName).containsExactly("Структура");
      assertThat(server.localFields().values().stream().flatMap(fields -> fields.keySet().stream())).contains("Ключ");
      var afterServer = constructorResolutions(counted);
      assertThat(afterServer).isPositive();
      var client = types.expressionTypesAt(document, position, ExecutionContext.THIN_CLIENT);
      assertThat(client).isEqualTo(server);
      assertThat(constructorResolutions(counted)).as("a second mode reuses complete context-independent body facts")
        .isEqualTo(afterServer);
    } finally {
      ReflectionTestUtils.setField(target, "typeRegistry", previousRegistry);
      ReflectionTestUtils.setField(target, "workspacePopulated", previousPopulation);
    }
  }

  @Test
  void parameterDeclarationsAndFieldArithmeticAreReusedAcrossRootModes() {
    var document = form("""
      &НаКлиентеНаСервереБезКонтекста
      Функция Построить(Вход)
        ДанныеПостроителя = Новый Структура;
        ДанныеПостроителя.Вставить("Ключ", Вход + 1);
        СнимокПроверки = ДанныеПостроителя;
        Возврат СнимокПроверки;
      КонецФункции
      """);
    ExpressionTypeInferencer target = AopTestUtils.getUltimateTargetObject(inferencer);
    var previousPopulation = ReflectionTestUtils.getField(target, "workspacePopulated");
    var previousRegistry = ReflectionTestUtils.getField(target, "typeRegistry");
    TypeRegistry actual = AopTestUtils.getUltimateTargetObject(registry);
    var counted = spy(actual);
    ReflectionTestUtils.setField(target, "typeRegistry", counted);
    ReflectionTestUtils.setField(target, "workspacePopulated", true);
    try {
      expressions.clear(document.getUri());
      flow.clear(document.getUri());
      var server = types.expressionTypesAt(document, referencePosition(document), ExecutionContext.SERVER);
      assertThat(server.refs()).extracting(TypeRef::qualifiedName).containsExactly("Структура");
      var fields = server.localFields().values().stream().flatMap(value -> value.entrySet().stream())
        .filter(entry -> entry.getKey().equals("Ключ")).toList();
      assertThat(fields).hasSize(1);
      assertThat(fields.getFirst().getValue().types().refs()).extracting(TypeRef::qualifiedName).containsExactly("Число");
      var first = constructorResolutions(counted);
      assertThat(first).isPositive();
      assertThat(types.expressionTypesAt(document, referencePosition(document), ExecutionContext.THIN_CLIENT)).isEqualTo(server);
      assertThat(constructorResolutions(counted)).as("parameter declarations do not force a second complete body pass")
        .isEqualTo(first);
    } finally {
      ReflectionTestUtils.setField(target, "typeRegistry", previousRegistry);
      ReflectionTestUtils.setField(target, "workspacePopulated", previousPopulation);
    }
  }

  @Test
  void aContextDependentMemberCallKeepsBodyFactsSeparate() {
    TestUtils.getDocumentContext(ROOT.resolve("CommonModules/КлиентСерверныйМодуль/Ext/Module.bsl").toUri(), """
      Функция Фабрика() Экспорт
        #Если Сервер Тогда
          Возврат 1;
        #Иначе
          Возврат "";
        #КонецЕсли
      КонецФункции
      """, context);
    var document = form("""
      &НаКлиентеНаСервереБезКонтекста
      Функция Построить(Вход)
        ДанныеПостроителя = КлиентСерверныйМодуль.Фабрика();
        СнимокПроверки = ДанныеПостроителя;
        Возврат СнимокПроверки;
      КонецФункции
      """);
    ExpressionTypeInferencer target = AopTestUtils.getUltimateTargetObject(inferencer);
    var previous = ReflectionTestUtils.getField(target, "workspacePopulated");
    ReflectionTestUtils.setField(target, "workspacePopulated", true);
    try {
      expressions.clear(document.getUri());
      flow.clear(document.getUri());
      assertThat(types.expressionTypesAt(document, referencePosition(document), ExecutionContext.SERVER).refs())
        .extracting(TypeRef::qualifiedName).containsExactly("Число");
      assertThat(types.expressionTypesAt(document, referencePosition(document), ExecutionContext.THIN_CLIENT).refs())
        .extracting(TypeRef::qualifiedName).containsExactly("Строка");
    } finally {
      ReflectionTestUtils.setField(target, "workspacePopulated", previous);
    }
  }

  @Test
  void aSourceFunctionShadowingAGlobalStillKeepsFlowModesSeparate() {
    var document = form("""
      &НаКлиентеНаСервереБезКонтекста
      Функция Строка(Вход)
        #Если Сервер Тогда
          Возврат 1;
        #Иначе
          Возврат "";
        #КонецЕсли
      КонецФункции
      &НаКлиентеНаСервереБезКонтекста
      Функция Построить(Вход)
        ДанныеПостроителя = Строка(Вход);
        СнимокПроверки = ДанныеПостроителя;
        Возврат СнимокПроверки;
      КонецФункции
      """);
    ExpressionTypeInferencer target = AopTestUtils.getUltimateTargetObject(inferencer);
    var previous = ReflectionTestUtils.getField(target, "workspacePopulated");
    ReflectionTestUtils.setField(target, "workspacePopulated", true);
    try {
      expressions.clear(document.getUri());
      flow.clear(document.getUri());
      assertThat(types.expressionTypesAt(document, referencePosition(document), ExecutionContext.SERVER).refs())
        .extracting(TypeRef::qualifiedName).containsExactly("Число");
      assertThat(types.expressionTypesAt(document, referencePosition(document), ExecutionContext.THIN_CLIENT).refs())
        .extracting(TypeRef::qualifiedName).containsExactly("Строка");
    } finally {
      ReflectionTestUtils.setField(target, "workspacePopulated", previous);
    }
  }

  @ParameterizedTest
  @CsvSource({"Строка,Строка", "Число,Число", "ЗначениеЗаполнено,Булево"})
  void globalMetadataReturnTypesDoNotForceAnotherBodyPass(String function, String expected) {
    // The lightweight test registry omits platform return types; provide the descriptor supplied by bsl-context.
    registry.ensureInitialized();
    registry.registerMemberOverride((TypeRef) ReflectionTestUtils.getField(TypeRegistry.class, "GLOBAL_CONTEXT"),
      () -> List.of(MemberDescriptor.method(function).withReturnTypes(TypeSet.of(new TypeRef(TypeKind.PRIMITIVE, expected)))),
      FileType.BSL);
    var document = form("""
      &НаКлиентеНаСервереБезКонтекста
      Функция Построить(Вход)
        ДанныеПостроителя = Новый Структура;
        ДанныеПостроителя.Вставить("Ключ", %s(Вход));
        СнимокПроверки = ДанныеПостроителя;
        Возврат СнимокПроверки;
      КонецФункции
      """.formatted(function));
    ExpressionTypeInferencer target = AopTestUtils.getUltimateTargetObject(inferencer);
    var population = ReflectionTestUtils.getField(target, "workspacePopulated");
    var previousRegistry = ReflectionTestUtils.getField(target, "typeRegistry");
    TypeRegistry actual = AopTestUtils.getUltimateTargetObject(registry);
    var counted = spy(actual);
    ReflectionTestUtils.setField(target, "workspacePopulated", true);
    ReflectionTestUtils.setField(target, "typeRegistry", counted);
    try {
      expressions.clear(document.getUri());
      flow.clear(document.getUri());
      var server = types.expressionTypesAt(document, referencePosition(document), ExecutionContext.SERVER);
      var fields = server.localFields().values().stream().flatMap(value -> value.entrySet().stream())
        .filter(entry -> entry.getKey().equals("Ключ")).toList();
      assertThat(fields).hasSize(1);
      assertThat(fields.getFirst().getValue().types().refs()).extracting(TypeRef::qualifiedName).containsExactly(expected);
      var first = constructorResolutions(counted);
      assertThat(first).isPositive();
      assertThat(types.expressionTypesAt(document, referencePosition(document), ExecutionContext.THIN_CLIENT)).isEqualTo(server);
      assertThat(constructorResolutions(counted)).isEqualTo(first);
    } finally {
      ReflectionTestUtils.setField(target, "typeRegistry", previousRegistry);
      ReflectionTestUtils.setField(target, "workspacePopulated", population);
    }
  }

  @Test
  void aContextDependentCalleeKeepsTheCallersLocalFactsSeparate() {
    var document = form("""
      &НаКлиентеНаСервереБезКонтекста
      Функция Фабрика()
        #Если Сервер Тогда
          Возврат 1;
        #Иначе
          Возврат "";
        #КонецЕсли
      КонецФункции
      &НаКлиентеНаСервереБезКонтекста
      Функция Построить()
        ДанныеПостроителя = Фабрика();
        СнимокПроверки = ДанныеПостроителя;
        Возврат СнимокПроверки;
      КонецФункции
      """);
    ExpressionTypeInferencer target = AopTestUtils.getUltimateTargetObject(inferencer);
    var previous = ReflectionTestUtils.getField(target, "workspacePopulated");
    ReflectionTestUtils.setField(target, "workspacePopulated", true);
    try {
      expressions.clear(document.getUri());
      flow.clear(document.getUri());
      assertThat(types.expressionTypesAt(document, referencePosition(document), ExecutionContext.SERVER).refs())
        .extracting(TypeRef::qualifiedName).containsExactly("Число");
      assertThat(types.expressionTypesAt(document, referencePosition(document), ExecutionContext.THIN_CLIENT).refs())
        .extracting(TypeRef::qualifiedName).containsExactly("Строка");
    } finally {
      ReflectionTestUtils.setField(target, "workspacePopulated", previous);
    }
  }

  private DocumentContext form(String source) {
    return TestUtils.getDocumentContext(ROOT.resolve("CommonForms/Тест/Ext/Form/Module.bsl").toUri(), source, context);
  }

  private static Position referencePosition(DocumentContext document) {
    var source = document.getContent();
    var offset = source.lastIndexOf("СнимокПроверки = ДанныеПостроителя") + "СнимокПроверки = ".length() + 1;
    return new Position((int) source.substring(0, offset).chars().filter(value -> value == '\n').count(),
      offset - source.lastIndexOf('\n', offset - 1) - 1);
  }

  private static long constructorResolutions(TypeRegistry counted) {
    return mockingDetails(counted).getInvocations().stream()
      .filter(call -> call.getMethod().getName().equals("resolve") && call.getArguments().length == 2
        && "Структура".equals(call.getArguments()[0])).count();
  }
}
