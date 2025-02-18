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
package com.github._1c_syntax.bsl.languageserver.hover;

import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DescriptionFormatterTest {

  @Autowired
  private DescriptionFormatter descriptionFormatter;

  @Test
  void whenParameterOfMethodHasAnnotations_thenAnnotationIsAddedToParameterSignatureDescription() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/hover/DescriptionFormatter.bsl");
    var methodSymbol = documentContext.getSymbolTree().getMethodSymbol("МетодСАннотациямиПараметров").orElseThrow();

    // when
    var description = descriptionFormatter.getParametersSignatureDescription(methodSymbol);

    // then
    assertThat(description).isEqualTo("&Повторяемый Парам1, Парам2");
  }

}