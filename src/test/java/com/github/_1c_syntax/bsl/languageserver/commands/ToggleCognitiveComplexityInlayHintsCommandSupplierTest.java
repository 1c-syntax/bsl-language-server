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
package com.github._1c_syntax.bsl.languageserver.commands;

import com.github._1c_syntax.bsl.languageserver.commands.complexity.ToggleComplexityInlayHintsCommandArguments;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.inlayhints.CognitiveComplexityInlayHintSupplier;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class ToggleCognitiveComplexityInlayHintsCommandSupplierTest {
  private final static String FILE_PATH = "./src/test/resources/commands/ToggleCognitiveComplexityInlayHintsCommandSupplier.bsl";

  @MockBean
  private CognitiveComplexityInlayHintSupplier complexityInlayHintSupplier;

  @Autowired
  private ToggleCognitiveComplexityInlayHintsCommandSupplier supplier;

  @Test
  void testExecute() {

    // given
    var documentContext = TestUtils.getDocumentContextFromFile(FILE_PATH);
    MethodSymbol firstMethod = documentContext.getSymbolTree().getMethods().get(0);

    var methodName = firstMethod.getName();

    var arguments = new ToggleComplexityInlayHintsCommandArguments(
      documentContext.getUri(),
      supplier.getId(),
      methodName
    );

    // when
    Optional<Object> result = supplier.execute(arguments);

    // then
    assertThat(result).isEmpty();
    verify(complexityInlayHintSupplier, times(1)).toggleHints(documentContext.getUri(), methodName);

  }

}
