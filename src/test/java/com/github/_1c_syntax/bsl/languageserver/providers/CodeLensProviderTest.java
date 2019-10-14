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
package com.github._1c_syntax.bsl.languageserver.providers;

import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.CodeLens;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CodeLensProviderTest {

  @Test
  void testGetCodeLens() throws IOException {

    // given
    String fileContent = FileUtils.readFileToString(
      new File("./src/test/resources/providers/codeLens.bsl"),
      StandardCharsets.UTF_8
    );
    DocumentContext documentContext = new DocumentContext("fake-uri.bsl", fileContent);

    CodeLensProvider codeLensProvider = new CodeLensProvider(LanguageServerConfiguration.create());

    // when
    List<CodeLens> codeLenses = codeLensProvider.getCodeLens(documentContext);

    // then
    Map<MethodSymbol, Integer> methodsComplexity = documentContext.getCognitiveComplexityData().getMethodsComplexity();

    MethodSymbol firstMethod = documentContext.getMethods().get(0);
    MethodSymbol secondMethod = documentContext.getMethods().get(1);
    int cognitiveComplexityFirstMethod = methodsComplexity.get(firstMethod);
    int cognitiveComplexitySecondMethod = methodsComplexity.get(secondMethod);

    assertThat(codeLenses).hasSize(2);
    assertThat(codeLenses)
      .anyMatch(codeLens -> codeLens.getRange().equals(firstMethod.getSubNameRange()))
      .anyMatch(codeLens -> codeLens.getCommand().getTitle().contains(String.valueOf(cognitiveComplexityFirstMethod)))
      .anyMatch(codeLens -> codeLens.getRange().equals(secondMethod.getSubNameRange()))
      .anyMatch(codeLens -> codeLens.getCommand().getTitle().contains(String.valueOf(cognitiveComplexitySecondMethod)))
    ;

  }

}