/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class HoverProviderTest {

  @Autowired
  private HoverProvider hoverProvider;

  @Test
  void getEmptyHover() {
    HoverParams params = new HoverParams();
    params.setPosition(new Position(0, 0));

    DocumentContext documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/providers/hover.bsl");
    Optional<Hover> optionalHover = hoverProvider.getHover(params, documentContext);

    assertThat(optionalHover.isPresent()).isFalse();
  }

  @Test
  void getHoverOverSubName() {
    HoverParams params = new HoverParams();
    params.setPosition(new Position(0, 20));

    DocumentContext documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/providers/hover.bsl");

    Optional<Hover> optionalHover = hoverProvider.getHover(params, documentContext);

    assertThat(optionalHover.isPresent()).isTrue();

    Hover hover = optionalHover.get();

    assertThat(hover.getContents().getRight().getValue()).isEqualTo("ИмяПроцедуры");
    assertThat(hover.getRange().getStart()).isEqualTo(new Position(0, 10));
    assertThat(hover.getRange().getEnd()).isEqualTo(new Position(0, 22));

  }

}
