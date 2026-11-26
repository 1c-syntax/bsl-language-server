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
package com.github._1c_syntax.bsl.languageserver.utils;

import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.TerminalNodeImpl;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PositionsTest {

  @Test
  void createFromCoordinates() {
    assertThat(Positions.create(3, 7)).isEqualTo(new Position(3, 7));
  }

  @Test
  void createFromToken() {
    var token = token();

    // ANTLR line 1-based -> LSP line 0-based; колонка без изменений.
    assertThat(Positions.create(token)).isEqualTo(new Position(4, 6));
  }

  @Test
  void createFromTokenMatchesRangeStart() {
    var token = token();

    // Парити с прежним Ranges.create(terminal).getStart().
    assertThat(Positions.create(token)).isEqualTo(Ranges.create(token).getStart());
  }

  @Test
  void createFromTerminalNode() {
    TerminalNode terminal = new TerminalNodeImpl(token());

    assertThat(Positions.create(terminal)).isEqualTo(new Position(4, 6));
    assertThat(Positions.create(terminal)).isEqualTo(Ranges.create(terminal).getStart());
  }

  @Test
  void createFromNullTerminalNodeIsEmpty() {
    assertThat(Positions.create((TerminalNode) null)).isEqualTo(new Position(0, 0));
  }

  @Test
  void createFromRuleContext() {
    var ctx = new ParserRuleContext(null, -1);
    ctx.start = token();
    ctx.stop = token();

    assertThat(Positions.create(ctx)).isEqualTo(new Position(4, 6));
    assertThat(Positions.create(ctx)).isEqualTo(Ranges.create(ctx).getStart());
  }

  @Test
  void createEndFromToken() {
    var token = token();

    // Парити с прежним Ranges.create(token).getEnd(): колонка + длина текста.
    assertThat(Positions.createEnd(token)).isEqualTo(new Position(4, 6 + "Идентификатор".length()));
    assertThat(Positions.createEnd(token)).isEqualTo(Ranges.create(token).getEnd());
  }

  @Test
  void createEndFromTerminalNode() {
    TerminalNode terminal = new TerminalNodeImpl(token());

    assertThat(Positions.createEnd(terminal)).isEqualTo(Ranges.create(terminal).getEnd());
  }

  @Test
  void createEndFromNullTerminalNodeIsEmpty() {
    assertThat(Positions.createEnd((TerminalNode) null)).isEqualTo(new Position(0, 0));
  }

  private static CommonToken token() {
    var token = new CommonToken(1, "Идентификатор");
    token.setLine(5);
    token.setCharPositionInLine(6);
    return token;
  }
}
