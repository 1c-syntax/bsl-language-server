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
package com.github._1c_syntax.bsl.languageserver.context.computer;

import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Висячий комментарий принадлежит своему символу и описанием соседнего не становится,
 * даже когда объявления стоят на соседних строках.
 */
@SpringBootTest
class TrailingCommentOwnershipTest {

  @Test
  void trailingCommentDoesNotDescribeNextVariable() {
    // given
    var documentContext = TestUtils.getDocumentContext("""
      Перем СВисячим; // Строка -
      Перем БезКомментария;
      """);

    // when
    var variables = documentContext.getSymbolTree().getVariables();

    // then
    assertThat(variables).extracting(variable -> variable.getName()).containsExactly("СВисячим", "БезКомментария");
    assertThat(variables.get(0).getDescription())
      .as("свой висячий комментарий у переменной есть")
      .isPresent();
    assertThat(variables.get(1).getDescription())
      .as("у соседней переменной комментария нет — чужой висячий её не описывает")
      .isEmpty();
  }

  @Test
  void trailingCommentDoesNotDescribeNextMethod() {
    // given
    var documentContext = TestUtils.getDocumentContext("""
      // Описание первой.
      Процедура Первая() Экспорт
      КонецПроцедуры // хвостовой комментарий первой
      Процедура Вторая() Экспорт
      КонецПроцедуры
      """);

    // when
    var methods = documentContext.getSymbolTree().getMethods();

    // then
    assertThat(methods).extracting(method -> method.getName()).containsExactly("Первая", "Вторая");
    assertThat(methods.get(0).getDescription())
      .as("собственное описание метода сохраняется")
      .isPresent()
      .hasValueSatisfying(description ->
        assertThat(description.getDescription()).contains("Описание первой."));
    assertThat(methods.get(1).getDescription())
      .as("комментарий после «КонецПроцедуры» описывает не следующий метод")
      .isEmpty();
  }
}
