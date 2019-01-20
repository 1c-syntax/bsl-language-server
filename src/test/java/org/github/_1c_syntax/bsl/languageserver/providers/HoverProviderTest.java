/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
 * Alexey Sosnoviy <labotamy@yandex.ru>, Nikita Gryzlov <nixel2007@gmail.com>
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
package org.github._1c_syntax.bsl.languageserver.providers;

import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.github._1c_syntax.parser.BSLExtendedParser;
import org.github._1c_syntax.parser.BSLParser;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Optional;

import static org.assertj.core.api.Java6Assertions.assertThat;

class HoverProviderTest {

  private BSLExtendedParser parser = new BSLExtendedParser();

  @Test
  void getEmptyHover() {
    TextDocumentPositionParams params = new TextDocumentPositionParams();
    params.setPosition(new Position(0, 0));

    BSLParser.FileContext fileContext = parser.parseFile(new File("./src/test/resources/providers/hover.bsl"));
    Optional<Hover> optionalHover = HoverProvider.getHover(params, fileContext);

    assertThat(optionalHover.isPresent()).isFalse();
  }

  @Test
  void getHoverOverSubName() {
    TextDocumentPositionParams params = new TextDocumentPositionParams();
    params.setPosition(new Position(0, 20));

    BSLParser.FileContext fileContext = parser.parseFile(new File("./src/test/resources/providers/hover.bsl"));
    Optional<Hover> optionalHover = HoverProvider.getHover(params, fileContext);

    assertThat(optionalHover.isPresent()).isTrue();

    Hover hover = optionalHover.get();

    assertThat(hover.getContents().getRight().getValue()).isEqualTo("ИмяПроцедуры");
    assertThat(hover.getRange().getStart()).isEqualTo(new Position(0, 10));
    assertThat(hover.getRange().getEnd()).isEqualTo(new Position(0, 22));

  }

}
