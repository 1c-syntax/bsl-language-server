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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Юнит-тесты для {@link Methods} — пока только хелпер про конструктор
 * OneScript-классов; для прочих методов это утилитный класс с парсер-зависимой
 * логикой, покрытой через intergration-тесты соседних провайдеров.
 */
class MethodsTest {

  @ParameterizedTest
  @ValueSource(strings = {
    "ПриСозданииОбъекта",
    "присозданииобъекта",
    "ПРИСОЗДАНИИОБЪЕКТА",
    "OnObjectCreate",
    "onobjectcreate",
    "ONOBJECTCREATE"
  })
  void isOscriptClassConstructorNameRecognizesBothLanguagesCaseInsensitively(String name) {
    // given/when/then
    assertThat(Methods.isOscriptClassConstructorName(name))
      .as("имя %s должно распознаваться как конструктор OScript-класса", name)
      .isTrue();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {
    "ПриСоздании",
    "OnObject",
    "ВыполнитьЧтоТо",
    "DoSomething",
    "   ",
  })
  void isOscriptClassConstructorNameRejectsOtherNames(String name) {
    // given/when/then
    assertThat(Methods.isOscriptClassConstructorName(name))
      .as("имя %s не должно распознаваться как конструктор OScript-класса", name)
      .isFalse();
  }
}
