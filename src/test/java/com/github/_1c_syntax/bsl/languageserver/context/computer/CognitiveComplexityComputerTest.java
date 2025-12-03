/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CognitiveComplexityComputerTest {

  @Autowired
  private ObjectProvider<CognitiveComplexityComputer> computerObjectProvider;

  @Test
  void compute() {
    // given
    DocumentContext documentContext
      = TestUtils.getDocumentContextFromFile("./src/test/resources/context/computer/CognitiveComplexityComputerTest.bsl");

    // when
    Computer<ComplexityData> cognitiveComplexityComputer = computerObjectProvider.getObject(documentContext);
    ComplexityData data = cognitiveComplexityComputer.compute();
    final Map<MethodSymbol, Integer> methodsComplexity = data.methodsComplexity();

    //then
    MethodSymbol example1 = documentContext.getSymbolTree().getMethods().get(0);
    Integer example1Complexity = methodsComplexity.get(example1);
    assertThat(example1Complexity).isEqualTo(19);

    MethodSymbol example2 = documentContext.getSymbolTree().getMethods().get(1);
    Integer example2Complexity = methodsComplexity.get(example2);
    assertThat(example2Complexity).isEqualTo(33);

    MethodSymbol example3 = documentContext.getSymbolTree().getMethods().get(2);
    Integer example3Complexity = methodsComplexity.get(example3);
    assertThat(example3Complexity).isEqualTo(20);

    MethodSymbol example4 = documentContext.getSymbolTree().getMethods().get(3);
    Integer example4Complexity = methodsComplexity.get(example4);
    assertThat(example4Complexity).isEqualTo(15);
  }
}