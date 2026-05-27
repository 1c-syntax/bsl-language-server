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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

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
    var nestedParam = nested.getParameters().getFirst();
    assertThat(nestedParam.name()).isEqualTo("Поле");
    assertThat(nestedParam.value().isRight()).isTrue();
    assertThat(nestedParam.value().getRight().getName()).isEqualTo("Внутренняя");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("stringLiteralValues")
  void resolvesStringLiteralParameterValue(String description, String code, String expected) {
    // when
    var method = TestUtils.getDocumentContext(code).getSymbolTree().getMethods().getFirst();
    var value = method.getAnnotations().getFirst().getParameters().getFirst().value();

    // then
    assertThat(value.getLeft()).isEqualTo(expected);
  }

  private static Stream<Arguments> stringLiteralValues() {
    return Stream.of(
      // экранированные кавычки ("" -> ")
      arguments("экранированные кавычки", """
        &Строковая("С ""кавычками"" внутри")
        Процедура Тест() Экспорт
        КонецПроцедуры
        """, "С \"кавычками\" внутри"),
      // пустая строка: кавычки сняты, значение пустое (а не "\"\"")
      arguments("пустая строка", """
        &Пустая("")
        Процедура Тест() Экспорт
        КонецПроцедуры
        """, ""),
      // многострочная: маркеры | сняты, переводы строк сохранены
      arguments("многострочная", """
        &Многострочная("строка1
        |строка2
        |строка3")
        Процедура Тест() Экспорт
        КонецПроцедуры
        """, "строка1\nстрока2\nстрока3"),
      // многострочная с отступами перед | и удвоённой кавычкой внутри
      arguments("многострочная с отступами и кавычками", """
        &Многострочная("первая
                |""вторая""\")
        Процедура Тест() Экспорт
        КонецПроцедуры
        """, "первая\n\"вторая\"")
    );
  }
}
