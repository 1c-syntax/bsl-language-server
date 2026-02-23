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
package com.github._1c_syntax.bsl.languageserver.documenthighlight;

import com.github._1c_syntax.bsl.languageserver.semantictokens.strings.SdblTokenTypes;
import com.github._1c_syntax.bsl.parser.BSLLexer;
import com.github._1c_syntax.bsl.parser.SDBLLexer;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тесты для проверки того, что обо всех токенах лексера было известно
 */
public class AllKnownTokenTest {

  private static final Set<Integer> skippedSdblToken = Set.of(
    SDBLLexer.WHITE_SPACE,
    SDBLLexer.LPAREN,
    SDBLLexer.RPAREN,
    SDBLLexer.AMPERSAND,
    SDBLLexer.ROUTEPOINT_FIELD,
    SDBLLexer.INCORRECT_IDENTIFIER,
    SDBLLexer.IDENTIFIER,
    SDBLLexer.UNKNOWN,
    SDBLLexer.PARAMETER_IDENTIFIER,
    SDBLLexer.ACTUAL_ACTION_PERIOD_VT,
    SDBLLexer.BALANCE_VT,
    SDBLLexer.BALANCE_AND_TURNOVERS_VT,
    SDBLLexer.BOUNDARIES_VT,
    SDBLLexer.DR_CR_TURNOVERS_VT,
    SDBLLexer.EXT_DIMENSIONS_VT,
    SDBLLexer.RECORDS_WITH_EXT_DIMENSIONS_VT,
    SDBLLexer.SCHEDULE_DATA_VT,
    SDBLLexer.SLICEFIRST_VT,
    SDBLLexer.SLICELAST_VT,
    SDBLLexer.TASK_BY_PERFORMER_VT,
    SDBLLexer.TURNOVERS_VT
  );

  @Test
  void sdblTokens() {
    List<String> unknown = new ArrayList<>();
    // токены имею индекс от одного максимального
    var endToken = SDBLLexer.VOCABULARY.getMaxTokenType();
    for (int i = 1; i <= endToken; i++) {
      var type = SdblTokenTypes.getTokenTypeAndModifiers(i);
      if (type == null) {
        // проверим на исключения
        if (!skippedSdblToken.contains(i)) {
          unknown.add("Type=%s, name %s".formatted(i, SDBLLexer.VOCABULARY.getSymbolicName(i)));
        }
      }
    }

    assertThat(unknown).isEmpty();
  }

  @Test
  @Disabled("Заготовка. Надо реализовать аналог для bsl lexer")
  void bslTokens() {
    List<String> unknown = new ArrayList<>();
    // токены имею индекс от одного максимального
    var endToken = BSLLexer.VOCABULARY.getMaxTokenType();
    assertThat(unknown).isEmpty();
  }
}
