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
package com.github._1c_syntax.bsl.languageserver.context.computer;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class QueryComputerTest {

  @Test
  void compute() {
    // given
    DocumentContext documentContext
      = TestUtils.getDocumentContextFromFile("./src/test/resources/context/computer/QueryComputerTest.bsl");

    // when
    var queries = documentContext.getQueries();

    //then
    assertThat(queries).hasSize(10);
    assertThat(queries.get(0).getTokens().get(1).getLine()).isEqualTo(3);
    assertThat(queries.get(1).getTokens().get(1).getLine()).isEqualTo(5);
    assertThat(queries.get(2).getTokens().get(1).getLine()).isEqualTo(12);
    assertThat(queries.get(3).getTokens().get(1).getLine()).isEqualTo(14);
    assertThat(queries.get(4).getTokens().get(2).getLine()).isEqualTo(21);
    assertThat(queries.get(5).getTokens().get(2).getLine()).isEqualTo(23);
    assertThat(queries.get(6).getTokens().get(1).getLine()).isEqualTo(30);
    assertThat(queries.get(7).getTokens().get(15).getLine()).isEqualTo(39);
    assertThat(queries.get(8).getTokens().get(31).getLine()).isEqualTo(50);
    assertThat(queries.get(9).getTokens().get(31).getLine()).isEqualTo(62);
  }
}