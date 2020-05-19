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

    assertThat(methods.size()).isEqualTo(5);

    assertThat(methods.get(0).getName()).isEqualTo("Один");
    assertThat(methods.get(0).getDescription().orElse(null)).isNull();
    assertThat(methods.get(0).getRange()).isEqualTo(Ranges.create(1, 0, 3, 14));
    assertThat(methods.get(0).getSubNameRange()).isEqualTo(Ranges.create(1, 10, 1, 14));

    assertThat(methods.get(1).getDescription()).isNotEmpty();
    assertThat(methods.get(1).getRegion().orElse(null).getName()).isEqualTo("ИмяОбласти");
    assertThat(methods.get(1).getDescription().orElse(null).getDescription()).isNotEmpty();

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
    checkModule(serverContext, PATH_TO_MODULE_FILE, "CommonModule.ПервыйОбщийМодуль", 5);
    checkModule(serverContext, PATH_TO_CATALOG_FILE, "Catalog.Справочник1", 1);
    checkModule(serverContext, PATH_TO_CATALOG_MODULE_FILE, "Catalog.Справочник1", 1);
  }

  @Test
  void testParseError() {

    DocumentContext documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/context/computer/MethodSymbolComputerTestParseError.bsl");
    List<MethodSymbol> methods = documentContext.getSymbolTree().getMethods();

    assertThat(methods.get(0).getName()).isEqualTo("Выполнить");
    assertThat(methods.get(0).getSubNameRange()).isEqualTo(Ranges.create(0, 10, 0, 19));

  }

  private void checkModule(
    ServerContext serverContext,
    String path,
    String mdoRef,
    int methodsCount
  ) throws IOException {
    var file = new File(PATH_TO_METADATA, path);
    var uri = Absolute.uri(file);
    var documentContext = serverContext.addDocument(uri, FileUtils.readFileToString(file, StandardCharsets.UTF_8));
    List<MethodSymbol> methods = documentContext.getSymbolTree().getMethods();
    assertThat(methods.size()).isEqualTo(methodsCount);
    assertThat(methods.get(0).getName()).isEqualTo("Тест");
    assertThat(methods.get(0).getMdoRef()).isEqualTo(mdoRef);
  }
}
