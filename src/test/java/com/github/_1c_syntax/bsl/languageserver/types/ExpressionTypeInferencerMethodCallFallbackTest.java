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

import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Локальная процедура может называться так же, как глобальная функция (или,
 * внутри модуля объекта/менеджера/набора записей, self-метод) — тогда вызов
 * резолвится в неё. Но если у самой процедуры нет объявленного возврата, нельзя
 * молча подставлять тип одноимённой глобальной функции/self-члена: это разные
 * символы с общим именем, а не один и тот же (см. аналогичный случай для
 * голого идентификатора в {@code ExpressionTypeInferencerSelfPropertyFallbackTest}).
 */
@SpringBootTest
class ExpressionTypeInferencerMethodCallFallbackTest {

  @Autowired
  private TypeService typeService;

  @Test
  void localProcedureShadowingGlobalFunctionKeepsHonestlyEmptyReturnType() {
    var content = """
      Процедура СтрНайти(Строка, ПодСтрока)
      КонецПроцедуры

      Процедура Тест()
        Б = СтрНайти("а", "б");
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(content);
    var lines = content.split("\n");
    var line = 4;
    var col = lines[line].indexOf("СтрНайти");

    // "СтрНайти" здесь — локальная процедура без объявленного возврата, а НЕ
    // глобальная функция СтрНайти (которая вернула бы Число). Вызов должен
    // резолвиться в локальный символ и унаследовать его честно пустой тип.
    var types = typeService.expressionTypesAt(documentContext, new Position(line, col));

    assertThat(types.refs()).isEmpty();
  }
}
