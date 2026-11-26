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
package com.github._1c_syntax.bsl.languageserver.semantictokens.snapshots;

import org.eclipse.lsp4j.SemanticTokensLegend;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StyledTokensDecoderTest {

  private static final SemanticTokensLegend LEGEND = new SemanticTokensLegend(
    List.of("keyword", "variable"),
    List.of("definition", "readonly", "static")
  );

  @Test
  void decodesRelativePositionsToAbsolute() {
    // given
    var data = List.of(
      1, 2, 3, 0, 0,
      0, 5, 1, 1, 0,
      2, 4, 6, 0, 0
    );

    // when
    var tokens = StyledTokensDecoder.decode(data, LEGEND);

    // then
    assertThat(tokens).containsExactly(
      new StyledToken(1, 2, 3, "keyword", Set.of()),
      new StyledToken(1, 7, 1, "variable", Set.of()),
      new StyledToken(3, 4, 6, "keyword", Set.of())
    );
  }

  @Test
  void resolvesModifiersFromBitmask() {
    // given
    var data = List.of(0, 0, 1, 1, 0b101);

    // when
    var tokens = StyledTokensDecoder.decode(data, LEGEND);

    // then
    assertThat(tokens).containsExactly(
      new StyledToken(0, 0, 1, "variable", Set.of("definition", "static"))
    );
  }
}
