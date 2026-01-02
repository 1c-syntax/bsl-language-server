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
class ConstructorColorInformationSupplierTest {

  @Autowired
  private ConstructorColorInformationSupplier supplier;

  @Test
  void getColorInformation() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/color/ConstructorColorInformationSupplier.bsl");

    // when
    List<ColorInformation> colorInformation = supplier.getColorInformation(documentContext);

    // then
    assertThatColorInformations(colorInformation)
      .hasSize(5)
      .hasColorAndRange(new Color(0, 0, 0, 1), Ranges.create(0, 4, 14))
      .hasColorAndRange(new Color(0.00392156862745098, 0.00784313725490196, 0.011764705882352941, 1), Ranges.create(1, 4, 23))
      .hasColorAndRange(new Color(0, 0, 0, 1), Ranges.create(2, 4, 35))
      .hasColorAndRange(new Color(0.00392156862745098, 0.00784313725490196, 0.011764705882352941, 1), Ranges.create(3, 4, 26))
      .hasColorAndRange(new Color(0, 0, 0, 1), Ranges.create(4, 4, 17))
    ;
  }

}
