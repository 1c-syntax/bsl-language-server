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
package com.github._1c_syntax.bsl.languageserver.context.symbol.annotations;

import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Разбор параметров аннотаций: позиционные/именованные значения, числовые
 * (без кавычек) литералы и вложенные аннотации.
 */
@SpringBootTest
class AnnotationsTest {

  @Test
  void parsesNumericAndNestedAnnotationParameters() {
    // given
    var code = """
      &Числовая(1)
      &Вложенная(Поле = &Внутренняя)
      Процедура Тест() Экспорт
      КонецПроцедуры
      """;

    // when
    var method = TestUtils.getDocumentContext(code).getSymbolTree().getMethods().getFirst();
    var annotations = method.getAnnotations();

    // then
    var numeric = annotations.stream().filter(a -> "Числовая".equals(a.getName())).findFirst().orElseThrow();
    assertThat(numeric.getParameters()).hasSize(1);
    // числовой литерал не оборачивается в кавычки
    assertThat(numeric.getParameters().getFirst().value().getLeft()).isEqualTo("1");

    var nested = annotations.stream().filter(a -> "Вложенная".equals(a.getName())).findFirst().orElseThrow();
    assertThat(nested.getParameters()).hasSize(1);
    var nestedValue = nested.getParameters().getFirst().value();
    assertThat(nestedValue.isRight()).isTrue();
    assertThat(nestedValue.getRight().getName()).isEqualTo("Внутренняя");
  }
}
