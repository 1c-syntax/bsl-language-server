/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2022
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

import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.Color;
import org.eclipse.lsp4j.ColorInformation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThatColorInformations;

@SpringBootTest
class WebColorInformationSupplierTest {

  @Autowired
  private WebColorInformationSupplier supplier;

  @Test
  void getColorInformation() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/color/WebColorInformationSupplier.bsl");

    // when
    List<ColorInformation> colorInformation = supplier.getColorInformation(documentContext);

    // then
    assertThatColorInformations(colorInformation)
      .hasSize(2)
      .hasColorAndRange(new Color(0, 0, 0, 1), Ranges.create(0, 4, 19))
      .hasColorAndRange(new Color(0.4980392156862745, 1, 0.8313725490196079, 1), Ranges.create(1, 4, 22))
    ;
  }

}