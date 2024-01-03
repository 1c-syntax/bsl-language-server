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
package com.github._1c_syntax.bsl.languageserver.color;

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.Color;
import org.eclipse.lsp4j.ColorPresentation;
import org.eclipse.lsp4j.ColorPresentationParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextEdit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThatColorPresentations;

@SpringBootTest
@CleanupContextBeforeClassAndAfterClass
class ConstructorColorPresentationSupplierTest {

  @Autowired
  private ConstructorColorPresentationSupplier supplier;

  @Autowired
  private LanguageServerConfiguration configuration;

  private DocumentContext documentContext;

  @BeforeEach
  void init() {
    documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/color/ConstructorColorPresentationSupplier.os");
  }

  @Test
  void getColorPresentationRu() {
    // given
    configuration.setLanguage(Language.RU);
    var params = new ColorPresentationParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setColor(new Color(0, 0.5, 1, 1));
    params.setRange(Ranges.create(0, 1, 2));

    // when
    List<ColorPresentation> colorPresentation = supplier.getColorPresentation(documentContext, params);

    // then
    assertThatColorPresentations(colorPresentation)
      .hasSize(1)
      .hasLabelAndTextEdit("Через конструктор \"Цвет\"", new TextEdit(params.getRange(), "Новый Цвет(0, 127, 255)"))
    ;
  }

  @Test
  void getColorPresentationEn() {
    // given
    configuration.setLanguage(Language.EN);
    var params = new ColorPresentationParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setColor(new Color(0, 0.5, 1, 1));
    params.setRange(Ranges.create(0, 1, 2));

    // when
    List<ColorPresentation> colorPresentation = supplier.getColorPresentation(documentContext, params);

    // then
    assertThatColorPresentations(colorPresentation)
      .hasSize(1)
      .hasLabelAndTextEdit("Via \"Color\" constructor", new TextEdit(params.getRange(), "New Color(0, 127, 255)"))
    ;
  }
}