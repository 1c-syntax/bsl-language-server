/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2023
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

import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
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

  private static final String PATH_TO_FILE = "./src/test/resources/providers/hover.bsl";

  @Autowired
  private HoverProvider hoverProvider;

  @Test
  void testEmptyHover() {
    // given
    HoverParams params = new HoverParams();
    params.setPosition(new Position(0, 0));

    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);

    // when
    Optional<Hover> optionalHover = hoverProvider.getHover(documentContext, params);

    // then
    assertThat(optionalHover).isNotPresent();
  }

  @Test
  void testSourceDefinedMethod() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);

    HoverParams params = new HoverParams();
    params.setPosition(new Position(3, 10));

    // when
    Optional<Hover> optionalHover = hoverProvider.getHover(documentContext, params);

    // then
    assertThat(optionalHover).isPresent();

    var hover = optionalHover.get();
    assertThat(hover.getContents().getRight().getValue()).isNotEmpty();
    assertThat(hover.getRange()).isEqualTo(Ranges.create(3, 8, 18));
  }

  @Test
  void testSourceDefinedVariable() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);

    HoverParams params = new HoverParams();
    params.setPosition(new Position(6, 15));

    // when
    Optional<Hover> optionalHover = hoverProvider.getHover(documentContext, params);

    // then
    assertThat(optionalHover).isPresent();

    var hover = optionalHover.get();
    assertThat(hover.getContents().getRight().getValue()).isNotEmpty();
    assertThat(hover.getRange()).isEqualTo(Ranges.create(6, 10, 20));
  }

}
