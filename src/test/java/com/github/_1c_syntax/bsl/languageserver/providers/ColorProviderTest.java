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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.Color;
import org.eclipse.lsp4j.ColorPresentationParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ColorProviderTest {

  @Autowired
  private ColorProvider provider;

  private DocumentContext documentContext;

  @BeforeEach
  void init() {
    documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/providers/color.bsl");
  }

  @Test
  void testDocumentColor() {
    // when
    var colorInformation = provider.getDocumentColor(documentContext);

    // then
    assertThat(colorInformation)
      .hasSize(2)
    ;
  }

  @Test
  void testColorPresentationCommonColor() {
    // given
    var params = new ColorPresentationParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setColor(new Color(0, 0, 0, 1));
    params.setRange(Ranges.create());

    // when
    var colorPresentation = provider.getColorPresentation(documentContext, params);

    // then
    assertThat(colorPresentation)
      .hasSize(2)
    ;
  }

  @Test
  void testColorPresentationCustomColor() {
    // given
    var params = new ColorPresentationParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setColor(new Color(0, 0.5, 1, 1));
    params.setRange(Ranges.create());

    // when
    var colorPresentation = provider.getColorPresentation(documentContext, params);

    // then
    assertThat(colorPresentation)
      .hasSize(1)
    ;
  }


}