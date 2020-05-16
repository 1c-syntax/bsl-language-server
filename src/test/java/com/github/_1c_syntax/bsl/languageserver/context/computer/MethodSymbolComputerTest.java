/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.Annotation;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.CompilerDirective;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.utils.Absolute;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class MethodSymbolComputerTest {

  private static final String PATH_TO_METADATA = "src/test/resources/metadata";
  private static final String PATH_TO_MODULE_FILE = "CommonModules/ПервыйОбщийМодуль/Ext/Module.bsl";
  private static final String PATH_TO_CATALOG_FILE = "Catalogs/Справочник1/Ext/ManagerModule.bsl";
  private static final String PATH_TO_CATALOG_MODULE_FILE = "Catalogs/Справочник1/Ext/ObjectModule.bsl";

  @Test
  void testMethodSymbolComputer() {

    DocumentContext documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/context/computer/MethodSymbolComputerTest.bsl");
    List<MethodSymbol> methods = documentContext.getSymbolTree().getMethods();

    assertThat(methods.size()).isEqualTo(18);

    assertThat(methods.get(0).getName()).isEqualTo("Один");
    assertThat(methods.get(0).getDescription().orElse(null)).isNull();
    assertThat(methods.get(0).getRange()).isEqualTo(Ranges.create(1, 0, 3, 14));
    assertThat(methods.get(0).getSubNameRange()).isEqualTo(Ranges.create(1, 10, 1, 14));

    assertThat(methods.get(1).getDescription()).isNotEmpty();
    assertThat(methods.get(1).getRegion().orElse(null).getName()).isEqualTo("ИмяОбласти");
    assertThat(methods.get(1).getDescription().orElse(null).getDescription()).isNotEmpty();

    var methodSymbol = methods.get(5);
    assertThat(methodSymbol.getName()).isEqualTo("Метод6");
    assertThat(methodSymbol.getCompilerDirective().orElse(null)).isEqualTo(CompilerDirective.AT_CLIENT);
    assertThat(methodSymbol.getAnnotations()).hasSize(0);

    methodSymbol = methods.get(13);
    assertThat(methodSymbol.getName()).isEqualTo("Метод14");
    assertThat(methodSymbol.getCompilerDirective().isPresent()).isEqualTo(false);
    var annotations = methodSymbol.getAnnotations();
    assertThat(annotations).hasSize(2);
    assertThat(annotations.get(0)).isEqualTo(Annotation.CUSTOM);
    assertThat(annotations.get(1)).isEqualTo(Annotation.CUSTOM);

    methodSymbol = methods.get(15);
    assertThat(methodSymbol.getName()).isEqualTo("Метод16");
    assertThat(methodSymbol.getCompilerDirective().orElse(null)).isEqualTo(CompilerDirective.AT_SERVER_NO_CONTEXT);
    assertThat(methodSymbol.getAnnotations()).hasSize(0);

    methodSymbol = methods.get(17);
    assertThat(methodSymbol.getName()).isEqualTo("Метод18");
    assertThat(methodSymbol.getCompilerDirective().orElse(null)).isEqualTo(CompilerDirective.AT_CLIENT_AT_SERVER);
    assertThat(methodSymbol.getAnnotations()).hasSize(0);
  }

  @Test
  void testParameters() {

    DocumentContext documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/context/computer/MethodSymbolComputerTest.bsl");
    List<MethodSymbol> methods = documentContext.getSymbolTree().getMethods();

    List<ParameterDefinition> parameters = methods.get(2).getParameters();
    assertThat(parameters.size()).isEqualTo(4);
    assertThat(parameters.get(0).getName()).isEqualTo("Парам");
    assertThat(parameters.get(0).isByValue()).isFalse();
    assertThat(parameters.get(0).isOptional()).isFalse();

    assertThat(parameters.get(1).getName()).isEqualTo("Парам2");
    assertThat(parameters.get(1).isByValue()).isTrue();
    assertThat(parameters.get(1).isOptional()).isFalse();

    assertThat(parameters.get(2).getName()).isEqualTo("Парам3");
    assertThat(parameters.get(2).isByValue()).isFalse();
    assertThat(parameters.get(2).isOptional()).isTrue();

    assertThat(parameters.get(3).getName()).isEqualTo("Парам4");
    assertThat(parameters.get(3).isByValue()).isTrue();
    assertThat(parameters.get(3).isOptional()).isTrue();

  }

  @Test
  void testDeprecated() {
    DocumentContext documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/context/computer/MethodSymbolComputerTest.bsl");
    List<MethodSymbol> methods = documentContext.getSymbolTree().getMethods();

    MethodSymbol methodSymbol = methods.get(2);

    assertThat(methodSymbol.isDeprecated()).isFalse();

    methodSymbol = methods.get(3);

    assertThat(methodSymbol.isDeprecated()).isTrue();
    assertThat(methodSymbol.getDescription().orElseThrow().getDeprecatedInfo()).isNotEmpty();

    methodSymbol = methods.get(4);

    assertThat(methodSymbol.isDeprecated()).isTrue();
    assertThat(methodSymbol.getDescription().orElseThrow().getDeprecatedInfo()).isNotEmpty();
  }

  @Test
  void testMdoRef() throws IOException {

    var path = Absolute.path(PATH_TO_METADATA);
    var serverContext = new ServerContext(path);
    checkModule(serverContext, PATH_TO_MODULE_FILE, "CommonModule.ПервыйОбщийМодуль");
    checkModule(serverContext, PATH_TO_CATALOG_FILE, "Catalog.Справочник1");
    checkModule(serverContext, PATH_TO_CATALOG_MODULE_FILE, "Catalog.Справочник1");
  }

  @Test
  void testParseError() {

    DocumentContext documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/context/computer/MethodSymbolComputerTestParseError.bsl");
    List<MethodSymbol> methods = documentContext.getSymbolTree().getMethods();

    assertThat(methods.get(0).getName()).isEqualTo("Выполнить");
    assertThat(methods.get(0).getSubNameRange()).isEqualTo(Ranges.create(0, 10, 0, 19));

  }

  @Test
  void testCompilerDirective() {

    String module = "&НаКлиенте\n" +
      "Процедура Метод6()\n" +
      "КонецПроцедуры";

    List<MethodSymbol> methods = getMethodSymbols(module);

    assertThat(methods).hasSize(1);
    var methodSymbol = methods.get(0);
    assertThat(methodSymbol.getName()).isEqualTo("Метод6");
    assertThat(methodSymbol.getCompilerDirective().orElse(null)).isEqualTo(CompilerDirective.AT_CLIENT);
    assertThat(methodSymbol.getAnnotations()).hasSize(0);
  }

  @Test
  void testCompilerDirectiveAtServerNoContext() {

    String module = "&НаСервереБезКонтекста\n" +
      "Процедура Метод7()\n" +
      "КонецПроцедуры";

    List<MethodSymbol> methods = getMethodSymbols(module);

    var methodSymbol = methods.get(0);
    assertThat(methodSymbol.getName()).isEqualTo("Метод7");
    assertThat(methodSymbol.getCompilerDirective().orElse(null)).isEqualTo(CompilerDirective.AT_SERVER_NO_CONTEXT);
    assertThat(methodSymbol.getAnnotations()).hasSize(0);
  }

  @Test
  void testSeveralCompilerDirective() {

    String module = "&НаКлиенте\n&НаСервере\n" +
      "Процедура Метод8()\n" +
      "КонецПроцедуры";

    List<MethodSymbol> methods = getMethodSymbols(module);

    var methodSymbol = methods.get(0);
    assertThat(methodSymbol.getName()).isEqualTo("Метод8");
    assertThat(methodSymbol.getCompilerDirective().orElse(null)).isEqualTo(CompilerDirective.AT_CLIENT);
    assertThat(methodSymbol.getAnnotations()).hasSize(0);
  }

  @Test
  void testNonCompilerDirectiveAndNonAnnotation() {

    String module = "Процедура Метод9()\n" +
      "КонецПроцедуры";

    List<MethodSymbol> methods = getMethodSymbols(module);

    var methodSymbol = methods.get(0);
    assertThat(methodSymbol.getName()).isEqualTo("Метод9");
    assertThat(methodSymbol.getCompilerDirective().isPresent()).isEqualTo(false);
    assertThat(methodSymbol.getAnnotations()).hasSize(0);
  }

  @Test
  void testAnnotation() {

    String module = "&После\n" +
      "Процедура Метод10()\n" +
      "КонецПроцедуры";

    List<MethodSymbol> methods = getMethodSymbols(module);

    assertThat(methods).hasSize(1);
    var methodSymbol = methods.get(0);
    assertThat(methodSymbol.getCompilerDirective().isPresent()).isEqualTo(false);
    var annotations = methodSymbol.getAnnotations();
    assertThat(annotations).hasSize(1);
    assertThat(annotations.get(0)).isEqualTo(Annotation.AFTER);
  }

  @Test
  void testCompilerDirectiveAndAnnotation() {

    String module = "&НаКлиенте\n&После\n" +
      "Процедура Метод11()\n" +
      "КонецПроцедуры";

    checkCompilerDirective_for_AtClient_AndAnnotation_After(module);
  }

  @Test
  void testCompilerDirectiveAndAnnotationOtherOrder() {

    String module = "&После\n&НаКлиенте\n" +
      "Процедура Метод12()\n" +
      "КонецПроцедуры";

    checkCompilerDirective_for_AtClient_AndAnnotation_After(module);
  }

  @Test
  void testCompilerDirectiveAndAnnotationForFunction() {

    String module = "&НаКлиенте\n&После\n" +
      "Функция Метод13()\n" +
      "КонецФункции";

    checkCompilerDirective_for_AtClient_AndAnnotation_After(module);
  }

  private static void checkCompilerDirective_for_AtClient_AndAnnotation_After(String module) {
    List<MethodSymbol> methods = getMethodSymbols(module);

    assertThat(methods).hasSize(1);
    var methodSymbol = methods.get(0);
    assertThat(methodSymbol.getCompilerDirective().orElse(null)).isEqualTo(CompilerDirective.AT_CLIENT);
    var annotations = methodSymbol.getAnnotations();
    assertThat(annotations).hasSize(1);
    assertThat(annotations.get(0)).isEqualTo(Annotation.AFTER);
  }

  @Test
  void testSeveralAnnotationsForFunction() {

    String module = "&Аннотация1\n" +
      "&Аннотация2\n" +
      "Процедура Метод14() Экспорт\n" +
      "КонецПроцедуры";

    List<MethodSymbol> methods = getMethodSymbols(module);

    var methodSymbol = methods.get(0);
    assertThat(methodSymbol.getCompilerDirective().isPresent()).isEqualTo(false);
    var annotations = methodSymbol.getAnnotations();
    assertThat(annotations).hasSize(2);
    assertThat(annotations.get(0)).isEqualTo(Annotation.CUSTOM);
    assertThat(annotations.get(1)).isEqualTo(Annotation.CUSTOM);
  }

  // есть определенные предпочтения при использовании &НаКлиентеНаСервереБезКонтекста в модуле упр.формы
  // при ее использовании с другой директивой будет использоваться именно она
  // например, порядок 1
  //&НаКлиентеНаСервереБезКонтекста
  //&НаСервереБезКонтекста
  //показывает Сервер в отладчике и доступен серверный объект ТаблицаЗначений
  // или порядок 2
  //&НаСервереБезКонтекста
  //&НаКлиентеНаСервереБезКонтекста
  //аналогично
  //т.е. порядок этих 2х директив не важен, все равно используется &НаКлиентеНаСервереБезКонтекста.
  // проверял на 8.3.15

  @Test
  void testSeveralDirectivesWithoutContext() {

    String module = "&НаСервереБезКонтекста\n" +
      "&НаКлиентеНаСервереБезКонтекста\n" +
      "Процедура Метод15()\n" +
      "КонецПроцедуры\n";

    List<MethodSymbol> methods = getMethodSymbols(module);

    var methodSymbol = methods.get(0);
    assertThat(methodSymbol.getCompilerDirective().orElse(null)).isEqualTo(CompilerDirective.AT_SERVER_NO_CONTEXT);
    assertThat(methodSymbol.getAnnotations()).hasSize(0);

  }

  @Test
  void testSeveralDirectivesWithoutContextReverse() {

    String module = "&НаКлиентеНаСервереБезКонтекста\n" +
      "&НаСервереБезКонтекста\n" +
      "Процедура Метод16()\n" +
      "КонецПроцедуры\n";

    List<MethodSymbol> methods = getMethodSymbols(module);

    var methodSymbol = methods.get(0);
    assertThat(methodSymbol.getCompilerDirective().orElse(null)).isEqualTo(CompilerDirective.AT_SERVER_NO_CONTEXT);
    assertThat(methodSymbol.getAnnotations()).hasSize(0);

  }

  // есть определенные предпочтения при использовании &НаКлиентеНаСервере в модуле команды
  // при ее использовании с другой директивой будет использоваться именно она
  //  проверял на 8.3.15
  //порядок
  //1
  //&НаКлиентеНаСервере
  //&НаКлиенте
  //вызывает клиент при вызове метода с клиента
  //вызывает сервер при вызове метода с сервера
  //2
  //&НаКлиенте
  //&НаКлиентеНаСервере
  //вызывает клиент при вызове метода с клиента
  //вызывает сервер при вызове метода с сервера

  @Test
  void testSeveralDirectivesWithClient() {

    String module = "&НаКлиентеНаСервере\n" +
      "&НаКлиенте\n" +
      "Процедура Метод17()\n" +
      "КонецПроцедуры\n";

    List<MethodSymbol> methods = getMethodSymbols(module);

    var methodSymbol = methods.get(0);
    assertThat(methodSymbol.getCompilerDirective().orElse(null)).isEqualTo(CompilerDirective.AT_CLIENT_AT_SERVER);
    assertThat(methodSymbol.getAnnotations()).hasSize(0);

  }

  @Test
  void testSeveralDirectivesWithClientReverse() {

    String module = "&НаКлиенте\n" +
      "&НаКлиентеНаСервере\n" +
      "Процедура Метод18()\n" +
      "КонецПроцедуры\n";

    List<MethodSymbol> methods = getMethodSymbols(module);

    var methodSymbol = methods.get(0);
    assertThat(methodSymbol.getCompilerDirective().orElse(null)).isEqualTo(CompilerDirective.AT_CLIENT_AT_SERVER);
    assertThat(methodSymbol.getAnnotations()).hasSize(0);

  }

  private static List<MethodSymbol> getMethodSymbols(String module) {
    DocumentContext documentContext = TestUtils.getDocumentContext(module);
    return documentContext.getSymbolTree().getMethods();
  }

  private void checkModule(ServerContext serverContext, String path, String mdoRef) throws IOException {
    var file = new File(PATH_TO_METADATA, path);
    var uri = Absolute.uri(file);
    var documentContext = serverContext.addDocument(uri, FileUtils.readFileToString(file, StandardCharsets.UTF_8));
    List<MethodSymbol> methods = documentContext.getSymbolTree().getMethods();
    assertThat(methods.size()).isEqualTo(1);
    assertThat(methods.get(0).getName()).isEqualTo("Тест");
    assertThat(methods.get(0).getMdoRef()).isEqualTo(mdoRef);
  }
}
