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
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.nio.file.Path;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Локальная переменная, объявленная через {@code Перем}, может называться так
 * же, как собственный реквизит модуля — тогда она его перекрывает (см.
 * {@link com.github._1c_syntax.bsl.languageserver.context.symbol.SelfMemberClassifier}).
 * Но если инференс её типа не даёт результата (переменная нигде не
 * присваивается — тип неизвестен, а не «нет ссылки вовсе»), нельзя молча
 * подставлять тип одноимённого self-свойства: это два разных символа с
 * общим именем, а не один и тот же.
 */
class ExpressionTypeInferencerSelfPropertyFallbackTest extends AbstractServerContextAwareTest {

  private static final URI OBJECT_MODULE_URI = Path.of(
    "./src/test/resources/metadata/designer/Catalogs/Справочник1/Ext/ObjectModule.bsl").toUri();

  @Autowired
  private TypeService typeService;

  @Test
  void untypedLocalVariableShadowingSelfPropertyIsNotConfusedWithIt() {
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    var content = """
      Процедура Тест()
        Перем Реквизит1;
        Б = Реквизит1;
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(OBJECT_MODULE_URI, content, context);

    // "Реквизит1" на строке "Б = Реквизит1;" — явно объявленная (Перем) локальная
    // переменная без единого присваивания: её тип честно не выведен (EMPTY), а
    // НЕ тип одноимённого self-реквизита (Строка).
    var types = typeService.expressionTypesAt(documentContext, new Position(2, 6));

    assertThat(types.refs()).isEmpty();
  }

  @Test
  void bareSelfPropertyWithoutShadowingVariableStillInfersItsType() {
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    var content = """
      Процедура Тест()
        Б = Реквизит1;
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(OBJECT_MODULE_URI, content, context);

    // Без объявленной одноимённой переменной голое имя по-прежнему должно
    // резолвиться в self-свойство — самого fallback'а фикс не отменяет.
    var types = typeService.expressionTypesAt(documentContext, new Position(1, 6));

    assertThat(types.refs()).isNotEmpty();
  }

  @Test
  void locallyAssignedVariableShadowingSelfPropertyKeepsItsOwnType() {
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    var content = """
      Процедура Тест()
        Перем Реквизит1;
        Реквизит1 = 1;
        Б = Реквизит1;
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(OBJECT_MODULE_URI, content, context);

    // А если переменная реально проинициализирована — берётся её собственный
    // (числовой) тип, а не тип self-свойства (Строка).
    var types = typeService.expressionTypesAt(documentContext, new Position(3, 6));

    assertThat(types.refs()).hasSize(1);
    assertThat(types.refs().iterator().next().qualifiedName()).isEqualToIgnoringCase("Число");
  }
}
