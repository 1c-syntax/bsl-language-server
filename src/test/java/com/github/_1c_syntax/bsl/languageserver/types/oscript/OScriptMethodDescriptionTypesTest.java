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
package com.github._1c_syntax.bsl.languageserver.types.oscript;

import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.ParameterDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.utils.Absolute;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Типы членов {@code .os}-класса, объявленные в описании метода.
 */
@SpringBootTest
@CleanupContextBeforeClassAndAfterEachTestMethod
class OScriptMethodDescriptionTypesTest {

  @Autowired
  private TypeRegistry typeRegistry;

  @Test
  void parameterTypesAreTakenByParameterNameWithCollectionElements() {
    // given — описание называет параметры не в порядке сигнатуры.
    TestUtils.getDocumentContext(Absolute.uri("file:///ДокументированныйКласс.os"), """
      // Метод с описанными параметрами.
      //
      // Параметры:
      //   Второй - Число - второй параметр.
      //   Первый - Массив из Строка - первый параметр.
      //
      Процедура Метод(Первый, Второй) Экспорт
      КонецПроцедуры
      """);
    var classRef = typeRegistry.resolve("ДокументированныйКласс", FileType.OS).orElseThrow();
    var array = typeRegistry.resolve("Массив").orElseThrow();
    var string = typeRegistry.resolve("Строка").orElseThrow();
    var number = typeRegistry.resolve("Число").orElseThrow();

    // when
    var parameters = parametersOf(typeRegistry.getMembers(classRef, FileType.OS), "Метод");

    // then — тип каждого параметра — из его собственной строки описания, у коллекции есть элемент.
    var first = parameters.get(0).types();
    assertThat(first.refs()).containsExactly(array);
    assertThat(first.getElementTypes(array).refs()).containsExactly(string);
    assertThat(parameters.get(1).types().refs()).containsExactly(number);
  }

  private static List<ParameterDescriptor> parametersOf(Collection<MemberDescriptor> members, String name) {
    return members.stream()
      .filter(member -> name.equals(member.name()))
      .findFirst()
      .orElseThrow()
      .signatures().getFirst().parameters();
  }
}
