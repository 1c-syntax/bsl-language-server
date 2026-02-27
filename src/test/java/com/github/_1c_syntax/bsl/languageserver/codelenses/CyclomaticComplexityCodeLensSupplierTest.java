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
package com.github._1c_syntax.bsl.languageserver.codelenses;

import com.github._1c_syntax.bsl.languageserver.codelenses.AbstractMethodComplexityCodeLensSupplier.ComplexityCodeLensData;
import com.github._1c_syntax.bsl.languageserver.commands.complexity.ToggleComplexityInlayHintsCommandArguments;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class CyclomaticComplexityCodeLensSupplierTest {

  private final static String FILE_PATH = "./src/test/resources/codelenses/CyclomaticComplexityCodeLensSupplier.bsl";

  @Autowired
  private CyclomaticComplexityCodeLensSupplier supplier;
  @Autowired
  private LanguageServerConfiguration configuration;

  @Test
  void testGetCodeLens() {

    // given
    var documentContext = TestUtils.getDocumentContextFromFile(FILE_PATH);

    // when
    List<CodeLens> codeLenses = supplier.getCodeLenses(documentContext);

    // then
    assertThat(codeLenses).allMatch(codeLens -> codeLens.getCommand() == null);

    // when
    codeLenses.forEach(codeLens ->
      supplier.resolve(documentContext, codeLens, (ComplexityCodeLensData) codeLens.getData())
    );

    // then
    Map<MethodSymbol, Integer> methodsComplexity = documentContext.getCyclomaticComplexityData().methodsComplexity();

    MethodSymbol firstMethod = documentContext.getSymbolTree().getMethods().get(0);
    MethodSymbol secondMethod = documentContext.getSymbolTree().getMethods().get(1);
    int complexityFirstMethod = methodsComplexity.get(firstMethod);
    int complexitySecondMethod = methodsComplexity.get(secondMethod);

    assertThat(codeLenses)
      .hasSize(2)
      .anySatisfy(codeLens -> {
        assertThat(codeLens.getRange()).isEqualTo(firstMethod.getSubNameRange());
        assertThat(codeLens.getCommand().getTitle()).contains(String.valueOf(complexityFirstMethod));
        assertThat(codeLens.getCommand().getCommand()).isEqualTo("toggleCyclomaticComplexityInlayHints");
        assertThat(((ToggleComplexityInlayHintsCommandArguments) codeLens.getCommand().getArguments().get(0)).getMethodName()).isEqualTo(firstMethod.getName());
      })
      .anySatisfy(codeLens -> {
        assertThat(codeLens.getRange()).isEqualTo(secondMethod.getSubNameRange());
        assertThat(codeLens.getCommand().getTitle()).contains(String.valueOf(complexitySecondMethod));
        assertThat(codeLens.getCommand().getCommand()).isEqualTo("toggleCyclomaticComplexityInlayHints");
        assertThat(((ToggleComplexityInlayHintsCommandArguments) codeLens.getCommand().getArguments().get(0)).getMethodName()).isEqualTo(secondMethod.getName());
      });

  }

  @Test
  void testConfigureComplexityThreshold() {

    // given
    var documentContext = TestUtils.getDocumentContextFromFile(FILE_PATH);
    configuration.getCodeLensOptions().getParameters().put(
      supplier.getId(),
      Either.forRight(Map.of("complexityThreshold", 2))
    );

    // when
    List<CodeLens> codeLenses = supplier.getCodeLenses(documentContext);

    // then
    assertThat(codeLenses).hasSize(1);

  }
}