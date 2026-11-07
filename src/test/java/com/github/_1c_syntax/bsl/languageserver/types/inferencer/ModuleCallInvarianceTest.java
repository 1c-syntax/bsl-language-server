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
import com.github._1c_syntax.bsl.languageserver.context.DocumentState;
import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.index.InferredExpressionTypeIndex;
import com.github._1c_syntax.bsl.languageserver.types.index.MethodReturnTypeIndexer;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.spy;

@CleanupContextBeforeClassAndAfterClass
class ModuleCallInvarianceTest extends AbstractServerContextAwareTest {
  private static final Path ROOT = Path.of("src/test/resources/metadata/commonModuleCalls");
  @Autowired private TypeService types;
  @Autowired private TypeRegistry registry;
  @Autowired private ExpressionTypeInferencer inferencer;
  @Autowired private VariableFlowAnalyzer flow;
  @Autowired private InferredExpressionTypeIndex expressions;
  @Autowired private MethodReturnTypeIndexer returns;
  private ExpressionTypeInferencer target;
  private Object previousPopulation;
  private Object previousRegistry;
  private TypeRegistry counted;

  @BeforeEach
  void init() throws Exception {
    initServerContext(ROOT);
    target = AopTestUtils.getUltimateTargetObject(inferencer);
    previousPopulation = ReflectionTestUtils.getField(target, "workspacePopulated");
    previousRegistry = ReflectionTestUtils.getField(target, "typeRegistry");
    TypeRegistry actual = AopTestUtils.getUltimateTargetObject(registry);
    counted = spy(actual);
    ReflectionTestUtils.setField(target, "typeRegistry", counted);
    ReflectionTestUtils.setField(target, "workspacePopulated", true);
  }

  @AfterEach
  void restore() {
    ReflectionTestUtils.setField(target, "typeRegistry", previousRegistry);
    ReflectionTestUtils.setField(target, "workspacePopulated", previousPopulation);
  }

  @Test
  void aLocalModuleGetterDoesNotForceAnotherFlowPass() {
    var document = common("Функция Фабрика()\nВозврат СерверныйМодуль;\nКонецФункции\n" + builder("Фабрика()"));
    try (var scope = new DiagnosticDeclarationSession().open()) {
      var server = query(document, ExecutionContext.SERVER);
      assertThat(fieldTypes(server)).containsExactly("СерверныйМодуль");
      var first = constructors();
      assertThat(first).isPositive();
      assertThat(query(document, ExecutionContext.THIN_CLIENT)).isEqualTo(server);
      assertThat(constructors()).as("metadata-only local getter preserves context-independent body facts").isEqualTo(first);
    }
  }

  @Test
  void preprocessorInsideTheGetterKeepsBodyFactsSeparate() {
    var document = common("""
      Функция Фабрика()
        #Если Сервер Тогда
          Возврат СерверныйМодуль;
        #Иначе
          Возврат КлиентскийМодуль;
        #КонецЕсли
      КонецФункции
      """ + builder("Фабрика()"));
    try (var scope = new DiagnosticDeclarationSession().open()) {
      assertThat(fieldTypes(query(document, ExecutionContext.SERVER))).containsExactly("СерверныйМодуль");
      var first = constructors();
      assertThat(fieldTypes(query(document, ExecutionContext.THIN_CLIENT))).containsExactly("КлиентскийМодуль");
      assertThat(constructors()).isGreaterThan(first);
    }
  }

  @Test
  void aServerOnlyGetterCannotShareTheCallersFlowWithTheClient() {
    var document = common("""
      #Если Сервер Тогда
      Функция Фабрика()
        Возврат СерверныйМодуль;
      КонецФункции
      #КонецЕсли
      """ + builder("Фабрика()"));
    try (var scope = new DiagnosticDeclarationSession().open()) {
      assertThat(fieldTypes(query(document, ExecutionContext.SERVER))).containsExactly("СерверныйМодуль");
      var first = constructors();
      assertThat(fieldTypes(query(document, ExecutionContext.THIN_CLIENT))).doesNotContain("СерверныйМодуль");
      assertThat(constructors()).isGreaterThan(first);
    }
  }

  @Test
  void aReleasedModuleUsesItsNeutralIndexWithoutReloadingItsBody() {
    var callee = common("""
      Функция Фабрика() Экспорт
        #Если Сервер Тогда
          Возврат 1;
        #Иначе
          Возврат "";
        #КонецЕсли
      КонецФункции
      """);
    var method = callee.getSymbolTree().getMethodSymbol("Фабрика").orElseThrow();
    returns.computeIfAbsent(method, () -> inferencer.computeReturnTypes(method));
    context.closeDocument(callee);
    assertThat(context.getDocumentState(callee)).isEqualTo(DocumentState.WITHOUT_CONTENT);
    var source = "&НаКлиентеНаСервереБезКонтекста\n" + builder("КлиентСерверныйМодуль.Фабрика()");
    var document = TestUtils.getDocumentContext(ROOT.resolve("CommonForms/Тест/Ext/Form/Module.bsl").toUri(), source, context);
    try (var scope = new DiagnosticDeclarationSession().open()) {
      var server = query(document, ExecutionContext.SERVER);
      assertThat(fieldTypes(server)).containsExactlyInAnyOrder("Число", "Строка");
      var first = constructors();
      assertThat(query(document, ExecutionContext.THIN_CLIENT)).isEqualTo(server);
      assertThat(constructors()).as("an indexed foreign return is independent of the requested mode").isEqualTo(first);
      assertThat(context.getDocumentState(callee)).isEqualTo(DocumentState.WITHOUT_CONTENT);
    }
  }

  private DocumentContext common(String source) {
    return TestUtils.getDocumentContext(ROOT.resolve("CommonModules/КлиентСерверныйМодуль/Ext/Module.bsl").toUri(), source, context);
  }

  private static String builder(String value) {
    return "Функция Построить(Вход)\nДанныеПостроителя = Новый Структура;\n"
      + "ДанныеПостроителя.Вставить(\"Ключ\", " + value + ");\n"
      + "СнимокПроверки = ДанныеПостроителя;\nВозврат СнимокПроверки;\nКонецФункции\n";
  }

  private TypeSet query(DocumentContext document, ExecutionContext mode) {
    var source = document.getContent();
    var offset = source.lastIndexOf("СнимокПроверки = ДанныеПостроителя") + "СнимокПроверки = ".length() + 1;
    return types.expressionTypesAt(document,
      new Position((int) source.substring(0, offset).chars().filter(c -> c == '\n').count(),
        offset - source.lastIndexOf('\n', offset - 1) - 1), mode);
  }

  private static List<String> fieldTypes(TypeSet types) {
    return types.localFields().values().stream().flatMap(fields -> fields.entrySet().stream())
      .filter(entry -> entry.getKey().equals("Ключ")).flatMap(entry -> entry.getValue().types().refs().stream())
      .map(TypeRef::qualifiedName).toList();
  }

  private long constructors() {
    return mockingDetails(counted).getInvocations().stream().filter(call -> call.getMethod().getName().equals("resolve")
      && call.getArguments().length == 2 && "Структура".equals(call.getArguments()[0])).count();
  }
}
