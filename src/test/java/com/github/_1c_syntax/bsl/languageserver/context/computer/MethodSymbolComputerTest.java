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
package com.github._1c_syntax.bsl.languageserver.context.computer;

import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.AnnotationKind;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.CompilerDirectiveKind;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.utils.Absolute;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.io.File;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class MethodSymbolComputerTest {

  private static final String PATH_TO_METADATA = "src/test/resources/metadata/designer";
  private static final String PATH_TO_MODULE_FILE = "CommonModules/ПервыйОбщийМодуль/Ext/Module.bsl";
  private static final String PATH_TO_CATALOG_FILE = "Catalogs/Справочник1/Ext/ManagerModule.bsl";
  private static final String PATH_TO_CATALOG_MODULE_FILE = "Catalogs/Справочник1/Ext/ObjectModule.bsl";

  @Autowired
  private ServerContextProvider serverContextProvider;

  @Test
  void testMethodSymbolComputer() {

    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/context/computer/MethodSymbolComputerTest.bsl");
    List<MethodSymbol> methods = documentContext.getSymbolTree().getMethods();

    assertThat(methods.size()).isEqualTo(25);

    assertThat(methods.getFirst().getName()).isEqualTo("Один");
    assertThat(methods.getFirst().getDescription()).isNotPresent();
    assertThat(methods.get(0).getRange()).isEqualTo(Ranges.create(1, 0, 3, 14));
    assertThat(methods.get(0).getSubNameRange()).isEqualTo(Ranges.create(1, 10, 1, 14));

    assertThat(methods.get(1).getDescription()).isNotEmpty();
    assertThat(methods.get(1).getRegion().orElse(null).getName()).isEqualTo("ИмяОбласти");
    assertThat(methods.get(1).getDescription().orElse(null).getDescription()).isNotEmpty();

    var methodSymbol = methods.get(5);
    assertThat(methodSymbol.getName()).isEqualTo("Метод6");
    assertThat(methodSymbol.getCompilerDirectiveKind().orElse(null)).isEqualTo(CompilerDirectiveKind.AT_CLIENT);
    assertThat(methodSymbol.getAnnotations()).isEmpty();

    methodSymbol = methods.get(6);
    assertThat(methodSymbol.getName()).isEqualTo("Метод7");
    assertThat(methodSymbol.getCompilerDirectiveKind().orElse(null)).isEqualTo(CompilerDirectiveKind.AT_SERVER_NO_CONTEXT);
    assertThat(methodSymbol.getAnnotations()).isEmpty();

    methodSymbol = methods.get(7);
    assertThat(methodSymbol.getName()).isEqualTo("Метод8");
    assertThat(methodSymbol.getCompilerDirectiveKind().orElse(null)).isEqualTo(CompilerDirectiveKind.AT_CLIENT);
    assertThat(methodSymbol.getAnnotations()).isEmpty();

    methodSymbol = methods.get(8);
    assertThat(methodSymbol.getName()).isEqualTo("Метод9");
    assertThat(methodSymbol.getCompilerDirectiveKind()).isEmpty();
    assertThat(methodSymbol.getAnnotations()).isEmpty();

    methodSymbol = methods.get(9);
    assertThat(methodSymbol.getName()).isEqualTo("Метод10");
    assertThat(methodSymbol.getCompilerDirectiveKind()).isEmpty();
    var annotations = methodSymbol.getAnnotations();
    assertThat(annotations).hasSize(1);
    assertThat(annotations.getFirst().getKind()).isEqualTo(AnnotationKind.AFTER);
    assertThat(annotations.getFirst().getName()).isEqualTo("После");
    assertThat(annotations.getFirst().getParameters()).isEmpty();

    methodSymbol = methods.get(10);
    assertThat(methodSymbol.getName()).isEqualTo("Метод11");
    checkCompilerDirective_for_AtClient_AndAnnotation_After(methodSymbol);

    methodSymbol = methods.get(11);
    assertThat(methodSymbol.getName()).isEqualTo("Метод12");
    checkCompilerDirective_for_AtClient_AndAnnotation_After(methodSymbol);

    methodSymbol = methods.get(12);
    assertThat(methodSymbol.getName()).isEqualTo("Метод13");
    checkCompilerDirective_for_AtClient_AndAnnotation_After(methodSymbol);

    methodSymbol = methods.get(13);
    assertThat(methodSymbol.getName()).isEqualTo("Метод14");
    assertThat(methodSymbol.getCompilerDirectiveKind()).isEmpty();
    annotations = methodSymbol.getAnnotations();
    assertThat(annotations).hasSize(2);
    assertThat(annotations.get(0).getKind()).isEqualTo(AnnotationKind.CUSTOM);
    assertThat(annotations.get(1).getKind()).isEqualTo(AnnotationKind.CUSTOM);

    methodSymbol = methods.get(14);
    assertThat(methodSymbol.getName()).isEqualTo("Метод15");
    assertThat(methodSymbol.getCompilerDirectiveKind().orElse(null)).isEqualTo(CompilerDirectiveKind.AT_SERVER_NO_CONTEXT);
    assertThat(methodSymbol.getAnnotations()).isEmpty();

    methodSymbol = methods.get(15);
    assertThat(methodSymbol.getName()).isEqualTo("Метод16");
    assertThat(methodSymbol.getCompilerDirectiveKind().orElse(null)).isEqualTo(CompilerDirectiveKind.AT_SERVER_NO_CONTEXT);
    assertThat(methodSymbol.getAnnotations()).isEmpty();

    methodSymbol = methods.get(16);
    assertThat(methodSymbol.getName()).isEqualTo("Метод17");
    assertThat(methodSymbol.getCompilerDirectiveKind().orElse(null)).isEqualTo(CompilerDirectiveKind.AT_CLIENT_AT_SERVER);
    assertThat(methodSymbol.getAnnotations()).isEmpty();

    methodSymbol = methods.get(17);
    assertThat(methodSymbol.getName()).isEqualTo("Метод18");
    assertThat(methodSymbol.getCompilerDirectiveKind().orElse(null)).isEqualTo(CompilerDirectiveKind.AT_CLIENT_AT_SERVER);
    assertThat(methodSymbol.getAnnotations()).isEmpty();

    methodSymbol = methods.get(18);
    assertThat(methodSymbol.getName()).isEqualTo("Метод19");
    assertThat(methodSymbol.getCompilerDirectiveKind().orElse(null)).isEqualTo(CompilerDirectiveKind.AT_CLIENT_AT_SERVER);
    assertThat(methodSymbol.getAnnotations()).isEmpty();

  }

  @Test
  void testAnnotation() {

    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/context/computer/MethodSymbolComputerTest.bsl");
    List<MethodSymbol> methods = documentContext.getSymbolTree().getMethods();

    // CUSTOM
    MethodSymbol methodSymbol = methods.get(19);
    assertThat(methodSymbol.getName()).isEqualTo("Метод20");
    assertThat(methodSymbol.getCompilerDirectiveKind()).isEmpty();
    assertThat(methodSymbol.getAnnotations()).hasSize(1);
    assertThat(methodSymbol.getAnnotations().getFirst().getKind()).isEqualTo(AnnotationKind.CUSTOM);

    var parameters = methodSymbol.getAnnotations().getFirst().getParameters();
    assertThat(parameters).hasSize(3);

    assertThat(parameters.getFirst().name()).isEqualTo("ДажеСПараметром");
    assertThat(parameters.get(0).optional()).isTrue();
    assertThat(parameters.get(0).value().getLeft()).isEqualTo("Да");

    assertThat(parameters.get(1).name()).isEqualTo("СПараметромБезЗначения");
    assertThat(parameters.get(1).optional()).isFalse();
    assertThat(parameters.get(1).value().getLeft()).isEmpty();

    assertThat(parameters.get(2).name()).isEmpty();
    assertThat(parameters.get(2).optional()).isTrue();
    assertThat(parameters.get(2).value().getLeft()).isEqualTo("Значение без параметра");

    // BEFORE
    methodSymbol = methods.get(20);
    assertThat(methodSymbol.getName()).isEqualTo("Р_Перед");
    assertThat(methodSymbol.getAnnotations().getFirst().getName()).isEqualTo("Перед");
    assertThat(methodSymbol.getAnnotations().getFirst().getKind()).isEqualTo(AnnotationKind.BEFORE);
    assertThat(methodSymbol.getAnnotations().getFirst().getParameters().getFirst().value().getLeft()).isEqualTo("Перед");

    // AFTER
    methodSymbol = methods.get(21);
    assertThat(methodSymbol.getName()).isEqualTo("Р_После");
    assertThat(methodSymbol.getAnnotations().getFirst().getName()).isEqualTo("После");
    assertThat(methodSymbol.getAnnotations().getFirst().getKind()).isEqualTo(AnnotationKind.AFTER);
    assertThat(methodSymbol.getAnnotations().getFirst().getParameters().getFirst().value().getLeft()).isEqualTo("После");

    // AROUND
    methodSymbol = methods.get(22);
    assertThat(methodSymbol.getName()).isEqualTo("Р_Вместо");
    assertThat(methodSymbol.getAnnotations().getFirst().getName()).isEqualTo("Вместо");
    assertThat(methodSymbol.getAnnotations().getFirst().getKind()).isEqualTo(AnnotationKind.AROUND);
    assertThat(methodSymbol.getAnnotations().getFirst().getParameters().getFirst().value().getLeft()).isEqualTo("Вместо");
  }

  @Test
  void testParameters() {

    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/context/computer/MethodSymbolComputerTest.bsl");
    List<MethodSymbol> methods = documentContext.getSymbolTree().getMethods();

    List<ParameterDefinition> parameters = methods.get(2).getParameters();
    assertThat(parameters.size()).isEqualTo(4);
    assertThat(parameters.getFirst().getName()).isEqualTo("Парам");
    assertThat(parameters.getFirst().isByValue()).isFalse();
    assertThat(parameters.get(0).isOptional()).isFalse();
    assertThat(parameters.get(0).getRange()).isEqualTo(Ranges.create(14, 12, 17));

    assertThat(parameters.get(1).getName()).isEqualTo("Парам2");
    assertThat(parameters.get(1).isByValue()).isTrue();
    assertThat(parameters.get(1).isOptional()).isFalse();
    assertThat(parameters.get(1).getRange()).isEqualTo(Ranges.create(14, 24, 30));

    assertThat(parameters.get(2).getName()).isEqualTo("Парам3");
    assertThat(parameters.get(2).isByValue()).isFalse();
    assertThat(parameters.get(2).isOptional()).isTrue();
    assertThat(parameters.get(2).getDefaultValue().value()).isEqualTo("0");
    assertThat(parameters.get(2).getRange()).isEqualTo(Ranges.create(14, 32, 38));

    assertThat(parameters.get(3).getName()).isEqualTo("Парам4");
    assertThat(parameters.get(3).isByValue()).isTrue();
    assertThat(parameters.get(3).isOptional()).isTrue();
    assertThat(parameters.get(3).getDefaultValue().value()).isEqualTo("0");
    assertThat(parameters.get(3).getRange()).isEqualTo(Ranges.create(14, 49, 55));

    parameters = methods.get(23).getParameters();
    assertThat(parameters.get(0).getName()).isEqualTo("Парам1");
    assertThat(parameters.get(0).getDescription()).isPresent();
    assertThat(parameters.get(1).getName()).isEqualTo("Парам2");
    assertThat(parameters.get(1).getDescription()).isEmpty();
    assertThat(parameters.get(2).getName()).isEqualTo("Парам3");
    assertThat(parameters.get(2).getDescription()).isPresent();

    parameters = methods.get(24).getParameters();
    assertThat(parameters.get(0).getName()).isEqualTo("Парам1");
    assertThat(parameters.getFirst().getAnnotations()).hasSize(1);
    assertThat(parameters.getFirst().getAnnotations().getFirst().getName()).isEqualTo("Повторяемый");
    assertThat(parameters.get(0).getAnnotations().getFirst().getKind()).isEqualTo(AnnotationKind.CUSTOM);
    assertThat(parameters.get(0).getAnnotations().getFirst().getParameters()).isEmpty();
    assertThat(parameters.get(1).getName()).isEqualTo("Парам2");
    assertThat(parameters.get(1).getAnnotations()).hasSize(1);
    assertThat(parameters.get(1).getAnnotations().getFirst().getName()).isEqualTo("ДругаяАннотация");
    assertThat(parameters.get(1).getAnnotations().getFirst().getKind()).isEqualTo(AnnotationKind.CUSTOM);
    assertThat(parameters.get(1).getAnnotations().getFirst().getParameters()).hasSize(1);
    assertThat(parameters.get(1).getAnnotations().getFirst().getParameters().getFirst().name()).isEmpty();
    assertThat(parameters.get(1).getAnnotations().getFirst().getParameters().getFirst().value().getLeft()).isEqualTo("СПараметром");
    assertThat(parameters.get(2).getName()).isEqualTo("Парам3");
    assertThat(parameters.get(2).getAnnotations()).isEmpty();

  }

  @Test
  void testDeprecated() {
    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/context/computer/MethodSymbolComputerTest.bsl");
    List<MethodSymbol> methods = documentContext.getSymbolTree().getMethods();

    MethodSymbol methodSymbol = methods.get(2);

    assertThat(methodSymbol.isDeprecated()).isFalse();

    methodSymbol = methods.get(3);

    assertThat(methodSymbol.isDeprecated()).isTrue();
    assertThat(methodSymbol.getDescription().orElseThrow().getDeprecationInfo()).isEmpty();

    methodSymbol = methods.get(4);

    assertThat(methodSymbol.isDeprecated()).isTrue();
    assertThat(methodSymbol.getDescription().orElseThrow().getDeprecationInfo()).isNotEmpty();
  }

  @Test
  @DirtiesContext
  void testOwner() {

    var path = Absolute.path(PATH_TO_METADATA);

    // Create workspace for the path
    serverContextProvider.clear();
    var serverContext = serverContextProvider.addWorkspace(path.toUri());
    serverContext.setConfigurationRoot(path);

    checkModule(serverContext, PATH_TO_MODULE_FILE, 7);
    checkModule(serverContext, PATH_TO_CATALOG_FILE, 2);
    checkModule(serverContext, PATH_TO_CATALOG_MODULE_FILE, 1);
  }

  @Test
  void testParseError() {

    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/context/computer/MethodSymbolComputerTestParseError.bsl");
    List<MethodSymbol> methods = documentContext.getSymbolTree().getMethods();

    assertThat(methods.getFirst().getName()).isEqualTo("Выполнить");
    assertThat(methods.getFirst().getSubNameRange()).isEqualTo(Ranges.create(0, 10, 0, 19));

  }

  @Test
  void testAsyncFunctionDescription() {
    // given
    var source = """
      // Описание асинхронной функции.
      //
      // Параметры:
      //   Желудь - Произвольный - первый параметр
      //   ОпределениеЖелудя - Произвольный - второй параметр
      //
      // Возвращаемое значение:
      //   Произвольный
      Асинх Функция ОбработатьЖелудь(Желудь, ОпределениеЖелудя) Экспорт
          Возврат Желудь;
      КонецФункции
      """;

    // when
    var documentContext = TestUtils.getDocumentContext(source);
    var methods = documentContext.getSymbolTree().getMethods();

    // then
    assertThat(methods).hasSize(1);
    var method = methods.getFirst();
    assertThat(method.getName()).isEqualTo("ОбработатьЖелудь");
    assertThat(method.getDescription()).isPresent();
    assertThat(method.getDescription().orElseThrow().getDescription())
      .contains("Описание асинхронной функции");

    var parameters = method.getParameters();
    assertThat(parameters).hasSize(2);
    assertThat(parameters.get(0).getName()).isEqualTo("Желудь");
    assertThat(parameters.get(0).getDescription()).isPresent();
    assertThat(parameters.get(1).getName()).isEqualTo("ОпределениеЖелудя");
    assertThat(parameters.get(1).getDescription()).isPresent();
  }

  @Test
  void testAsyncProcedureDescription() {
    // given
    var source = """
      // Описание асинхронной процедуры.
      //
      // Параметры:
      //   Парам1 - Произвольный - описание
      Асинх Процедура ВыполнитьЧтоТо(Парам1) Экспорт
      КонецПроцедуры
      """;

    // when
    var documentContext = TestUtils.getDocumentContext(source);
    var methods = documentContext.getSymbolTree().getMethods();

    // then
    assertThat(methods).hasSize(1);
    var method = methods.getFirst();
    assertThat(method.getName()).isEqualTo("ВыполнитьЧтоТо");
    assertThat(method.getDescription()).isPresent();
    assertThat(method.getParameters()).hasSize(1);
    assertThat(method.getParameters().getFirst().getDescription()).isPresent();
  }

  @Test
  void testAsyncMethodWithAnnotation() {
    // given
    var source = """
      // Описание async + аннотация.
      //
      // Параметры:
      //   Парам1 - Произвольный - описание
      &После
      Асинх Процедура ОбработатьСобытие(Парам1) Экспорт
      КонецПроцедуры
      """;

    // when
    var documentContext = TestUtils.getDocumentContext(source);
    var methods = documentContext.getSymbolTree().getMethods();

    // then
    assertThat(methods).hasSize(1);
    var method = methods.getFirst();
    assertThat(method.getName()).isEqualTo("ОбработатьСобытие");
    assertThat(method.getDescription()).isPresent();
    assertThat(method.getParameters().getFirst().getDescription()).isPresent();
    assertThat(method.getAnnotations()).hasSize(1);
    assertThat(method.getAnnotations().getFirst().getKind()).isEqualTo(AnnotationKind.AFTER);
    // у аннотации приоритет над Асинх: range начинается с AMPERSAND аннотации, а не с Асинх
    assertThat(method.getRange()).isEqualTo(Ranges.create(4, 0, 6, 14));
  }

  @Test
  void testAsyncMethodWithCompilerDirective() {
    // given
    var source = """
      // Описание async + директива компиляции.
      //
      // Параметры:
      //   Парам1 - Произвольный - описание
      &НаКлиенте
      Асинх Процедура ВыполнитьНаКлиенте(Парам1) Экспорт
      КонецПроцедуры
      """;

    // when
    var documentContext = TestUtils.getDocumentContext(source);
    var methods = documentContext.getSymbolTree().getMethods();

    // then
    assertThat(methods).hasSize(1);
    var method = methods.getFirst();
    assertThat(method.getName()).isEqualTo("ВыполнитьНаКлиенте");
    assertThat(method.getDescription()).isPresent();
    assertThat(method.getParameters().getFirst().getDescription()).isPresent();
    assertThat(method.getCompilerDirectiveKind().orElse(null)).isEqualTo(CompilerDirectiveKind.AT_CLIENT);
    // директивы компиляции исторически не входят в range; ASYNC задаёт начало range
    assertThat(method.getRange()).isEqualTo(Ranges.create(5, 0, 6, 14));
  }

  private static void checkCompilerDirective_for_AtClient_AndAnnotation_After(MethodSymbol methodSymbol) {
    assertThat(methodSymbol.getCompilerDirectiveKind().orElse(null)).isEqualTo(CompilerDirectiveKind.AT_CLIENT);
    var annotations = methodSymbol.getAnnotations();
    assertThat(annotations).hasSize(1);
    assertThat(annotations.getFirst().getKind()).isEqualTo(AnnotationKind.AFTER);
  }

  private void checkModule(
    ServerContext serverContext,
    String path,
    int methodsCount
  ) {
    var file = new File(PATH_TO_METADATA, path);
    var uri = Absolute.uri(file);
    var documentContext = serverContext.addDocument(uri);
    serverContext.rebuildDocument(documentContext);
    List<MethodSymbol> methods = documentContext.getSymbolTree().getMethods();
    assertThat(methods.size()).isEqualTo(methodsCount);
    assertThat(methods.getFirst().getName()).isEqualTo("Тест");
    assertThat(methods.getFirst().getOwner()).isEqualTo(documentContext);
  }
}
