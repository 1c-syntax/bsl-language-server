/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
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
package org.github._1c_syntax.bsl.languageserver.context.computer;

import org.apache.commons.io.FileUtils;
import org.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import org.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CognitiveComplexityComputerTest {

  @Test
  void compute() throws IOException {

    // given
    String fileContent = FileUtils.readFileToString(
      new File("./src/test/resources/context/computer/CognitiveComplexityComputerTest.bsl"),
      StandardCharsets.UTF_8
    );
    DocumentContext documentContext = new DocumentContext("fake-uri.bsl", fileContent);

    // when
    Computer<CognitiveComplexityComputer.Data> cognitiveComplexityComputer =
      new CognitiveComplexityComputer(documentContext);
    CognitiveComplexityComputer.Data data = cognitiveComplexityComputer.compute();

    //then
    MethodSymbol example1 = documentContext.getMethods().get(0);
    Integer example1Complexity = data.getMethodsComplexity().get(example1);
    assertThat(example1Complexity).isEqualTo(19);

    MethodSymbol example2 = documentContext.getMethods().get(1);
    Integer example2Complexity = data.getMethodsComplexity().get(example2);
    assertThat(example2Complexity).isEqualTo(33);

    MethodSymbol example3 = documentContext.getMethods().get(2);
    Integer example3Complexity = data.getMethodsComplexity().get(example3);
    assertThat(example3Complexity).isEqualTo(20);
  }
}