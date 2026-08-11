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
package com.github._1c_syntax.bsl.languageserver.types;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.types.index.SymbolTypeIndex;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тип, названный в описании метода, не отменяет того, что посчитано по его телу.
 */
@CleanupContextBeforeClassAndAfterClass
class DeclaredStructureFieldsTest extends AbstractServerContextAwareTest {

  @Autowired
  private SymbolTypeIndex symbolTypeIndex;

  @Test
  void declaredTypeKeepsFieldsCollectedFromBody() {
    // given: у функции в описании назван тип «Структура», а состав ключей известен из тела.
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/DeclaredStructureWithFields.bsl");
    var method = documentContext.getSymbolTree().getMethodSymbol("ОписаниеСобытий");
    assertThat(method).isPresent();

    // when
    var returnTypes = symbolTypeIndex.getReturnTypes(method.get());

    // then: описание и вывод по телу дополняют друг друга. Если названный в описании тип
    // вычитать из выведенного, вместе с ним пропадут и посчитанные поля — а обращение к
    // ним станет обращением к несуществующему свойству.
    assertThat(returnTypes.getAllFieldNames()).contains("Первое", "Второе");
  }
}
