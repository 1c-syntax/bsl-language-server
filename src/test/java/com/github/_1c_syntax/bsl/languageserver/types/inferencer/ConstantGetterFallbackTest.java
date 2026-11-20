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
import com.github._1c_syntax.bsl.languageserver.types.index.MethodReturnTypeIndexer;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "app.platform-context.enabled=false")
class ConstantGetterFallbackTest extends AbstractServerContextAwareTest {
  @Autowired
  private ExpressionTypeInferencer inferencer;

  @ParameterizedTest
  @ValueSource(strings = {"Константы.Threshold.Получить()", "Constants.Threshold.Get()"})
  void readsConstantTypeDuringPopulationWithoutPlatformContext(String getter, @TempDir Path root) throws IOException {
    var result = readConstantType(root, getter, "<v8:Type>xs:decimal</v8:Type>");

    assertThat(result.types().refs()).extracting(TypeRef::qualifiedName).containsExactly("Число");
    assertThat(result.incomplete()).isFalse();
  }

  @Test
  void readsAllDeclaredTypesOfCompositeConstant(@TempDir Path root) throws IOException {
    var result = readConstantType(root, "Константы.Threshold.Получить()", """
      <v8:Type>xs:decimal</v8:Type>
      <v8:Type>xs:string</v8:Type>
      """);

    assertThat(result.types().refs()).extracting(TypeRef::qualifiedName).containsExactlyInAnyOrder("Число", "Строка");
    assertThat(result.incomplete()).isFalse();
  }

  private MethodReturnTypeIndexer.ComputedReturnTypes readConstantType(
    Path root, String getter, String declaredTypes
  ) throws IOException {
    Files.writeString(root.resolve("Configuration.xml"), """
      <MetaDataObject xmlns="http://v8.1c.ru/8.3/MDClasses" version="2.11">
        <Configuration uuid="11111111-1111-4111-8111-111111111111">
          <Properties><Name>ConstantFallback</Name><ScriptVariant>Russian</ScriptVariant></Properties>
          <ChildObjects><Constant>Threshold</Constant></ChildObjects>
        </Configuration>
      </MetaDataObject>
      """);
    var constants = Files.createDirectory(root.resolve("Constants"));
    Files.writeString(constants.resolve("Threshold.xml"), """
      <MetaDataObject xmlns="http://v8.1c.ru/8.3/MDClasses"
                      xmlns:v8="http://v8.1c.ru/8.1/data/core"
                      xmlns:xs="http://www.w3.org/2001/XMLSchema" version="2.11">
        <Constant uuid="22222222-2222-4222-8222-222222222222">
          <Properties><Name>Threshold</Name><Type>%s</Type></Properties>
        </Constant>
      </MetaDataObject>
      """.formatted(declaredTypes));
    initServerContext(root, false);
    var document = TestUtils.getDocumentContext("""
      Function ReadValue()
        Return %s;
      EndFunction
      """.formatted(getter), context);
    var method = document.getSymbolTree().getMethodSymbol("ReadValue").orElseThrow();

    return inferencer.computeReturnTypes(method);
  }
}
