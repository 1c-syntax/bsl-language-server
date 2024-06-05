/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2024
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
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class MethodSymbolComputerTest {

  private static final String PATH_TO_METADATA = "src/test/resources/metadata/designer";
  private static final String PATH_TO_MODULE_FILE = "CommonModules/ПервыйОбщийМодуль/Ext/Module.bsl";
  private static final String PATH_TO_CATALOG_FILE = "Catalogs/Справочник1/Ext/ManagerModule.bsl";
  private static final String PATH_TO_CATALOG_MODULE_FILE = "Catalogs/Справочник1/Ext/ObjectModule.bsl";

  @Autowired
  private ServerContext serverContext;

  @Test
  void testMethodSymbolComputer() {

    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/context/computer/MethodSymbolComputerTest.bsl");
    List<MethodSymbol> methods = documentContext.getSymbolTree().getMethods();

    assertThat(methods.size()).isEqualTo(24);

    assertThat(methods.get(0).getName()).isEqualTo("Один");
    assertThat(methods.get(0).getDescription()).isNotPresent();
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
    assertThat(annotations.get(0).getKind()).isEqualTo(AnnotationKind.AFTER);
    assertThat(annotations.get(0).getName()).isEqualTo("После");
    assertThat(annotations.get(0).getParameters()).isEmpty();

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
    assertThat(methodSymbol.getAnnotations().get(0).getKind()).isEqualTo(AnnotationKind.CUSTOM);

    var parameters = methodSymbol.getAnnotations().get(0).getParameters();
    assertThat(parameters).hasSize(3);

    assertThat(parameters.get(0).getName()).isEqualTo("ДажеСПараметром");
    assertThat(parameters.get(0).isOptional()).isTrue();
    assertThat(parameters.get(0).getValue()).isEqualTo("Да");

    assertThat(parameters.get(1).getName()).isEqualTo("СПараметромБезЗначения");
    assertThat(parameters.get(1).isOptional()).isFalse();
    assertThat(parameters.get(1).getValue()).isEmpty();

    assertThat(parameters.get(2).getName()).isEmpty();
    assertThat(parameters.get(2).isOptional()).isTrue();
    assertThat(parameters.get(2).getValue()).isEqualTo("Значение без параметра");

    // BEFORE
    methodSymbol = methods.get(20);
    assertThat(methodSymbol.getName()).isEqualTo("Р_Перед");
    assertThat(methodSymbol.getAnnotations().get(0).getName()).isEqualTo("Перед");
    assertThat(methodSymbol.getAnnotations().get(0).getKind()).isEqualTo(AnnotationKind.BEFORE);
    assertThat(methodSymbol.getAnnotations().get(0).getParameters().get(0).getValue()).isEqualTo("Перед");

    // AFTER
    methodSymbol = methods.get(21);
    assertThat(methodSymbol.getName()).isEqualTo("Р_После");
    assertThat(methodSymbol.getAnnotations().get(0).getName()).isEqualTo("После");
    assertThat(methodSymbol.getAnnotations().get(0).getKind()).isEqualTo(AnnotationKind.AFTER);
    assertThat(methodSymbol.getAnnotations().get(0).getParameters().get(0).getValue()).isEqualTo("После");

    // AROUND
    methodSymbol = methods.get(22);
    assertThat(methodSymbol.getName()).isEqualTo("Р_Вместо");
    assertThat(methodSymbol.getAnnotations().get(0).getName()).isEqualTo("Вместо");
    assertThat(methodSymbol.getAnnotations().get(0).getKind()).isEqualTo(AnnotationKind.AROUND);
    assertThat(methodSymbol.getAnnotations().get(0).getParameters().get(0).getValue()).isEqualTo("Вместо");
  }

  @Test
  void testParameters() {

    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/context/computer/MethodSymbolComputerTest.bsl");
    List<MethodSymbol> methods = documentContext.getSymbolTree().getMethods();

    List<ParameterDefinition> parameters = methods.get(2).getParameters();
    assertThat(parameters.size()).isEqualTo(4);
    assertThat(parameters.get(0).getName()).isEqualTo("Парам");
    assertThat(parameters.get(0).isByValue()).isFalse();
    assertThat(parameters.get(0).isOptional()).isFalse();
    assertThat(parameters.get(0).getRange()).isEqualTo(Ranges.create(14, 12, 17));

    assertThat(parameters.get(1).getName()).isEqualTo("Парам2");
    assertThat(parameters.get(1).isByValue()).isTrue();
    assertThat(parameters.get(1).isOptional()).isFalse();
    assertThat(parameters.get(1).getRange()).isEqualTo(Ranges.create(14, 24, 30));

    assertThat(parameters.get(2).getName()).isEqualTo("Парам3");
    assertThat(parameters.get(2).isByValue()).isFalse();
    assertThat(parameters.get(2).isOptional()).isTrue();
    assertThat(parameters.get(2).getDefaultValue().getValue()).isEqualTo("0");
    assertThat(parameters.get(2).getRange()).isEqualTo(Ranges.create(14, 32, 38));

    assertThat(parameters.get(3).getName()).isEqualTo("Парам4");
    assertThat(parameters.get(3).isByValue()).isTrue();
    assertThat(parameters.get(3).isOptional()).isTrue();
    assertThat(parameters.get(3).getDefaultValue().getValue()).isEqualTo("0");
    assertThat(parameters.get(3).getRange()).isEqualTo(Ranges.create(14, 49, 55));

    parameters = methods.get(23).getParameters();
    assertThat(parameters.get(0).getName()).isEqualTo("Парам1");
    assertThat(parameters.get(0).getDescription()).isPresent();
    assertThat(parameters.get(1).getName()).isEqualTo("Парам2");
    assertThat(parameters.get(1).getDescription()).isEmpty();
    assertThat(parameters.get(2).getName()).isEqualTo("Парам3");
    assertThat(parameters.get(2).getDescription()).isPresent();

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
  void testOwner() throws IOException {

    var path = Absolute.path(PATH_TO_METADATA);
    serverContext.setConfigurationRoot(path);
    checkModule(serverContext, PATH_TO_MODULE_FILE, 7);
    checkModule(serverContext, PATH_TO_CATALOG_FILE, 2);
    checkModule(serverContext, PATH_TO_CATALOG_MODULE_FILE, 1);
  }

  @Test
  void testParseError() {

    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/context/computer/MethodSymbolComputerTestParseError.bsl");
    List<MethodSymbol> methods = documentContext.getSymbolTree().getMethods();

    assertThat(methods.get(0).getName()).isEqualTo("Выполнить");
    assertThat(methods.get(0).getSubNameRange()).isEqualTo(Ranges.create(0, 10, 0, 19));

  }

  private static void checkCompilerDirective_for_AtClient_AndAnnotation_After(MethodSymbol methodSymbol) {
    assertThat(methodSymbol.getCompilerDirectiveKind().orElse(null)).isEqualTo(CompilerDirectiveKind.AT_CLIENT);
    var annotations = methodSymbol.getAnnotations();
    assertThat(annotations).hasSize(1);
    assertThat(annotations.get(0).getKind()).isEqualTo(AnnotationKind.AFTER);
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
    assertThat(methods.get(0).getName()).isEqualTo("Тест");
    assertThat(methods.get(0).getOwner()).isEqualTo(documentContext);
  }
}
