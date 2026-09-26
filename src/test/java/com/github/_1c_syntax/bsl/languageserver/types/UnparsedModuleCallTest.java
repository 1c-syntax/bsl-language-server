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
import com.github._1c_syntax.bsl.languageserver.types.inferencer.ExpressionTypeInferencer;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.utils.Absolute;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Обращение в общий модуль, файл которого ещё не разобран.
 * <p>
 * Документы разбираются параллельно, поэтому вызов может встретиться раньше, чем разобран
 * вызываемый модуль. Тип модуля к этому моменту объявлен по метаданным, но членов у него
 * ещё нет, и результат вызова выходит пустым. Пустота эта временная, и расчёт обязан
 * сообщить о ней — иначе он застынет с ней навсегда, а повезло ему или нет, будет решать
 * порядок наполнения.
 */
@CleanupContextBeforeClassAndAfterClass
class UnparsedModuleCallTest extends AbstractServerContextAwareTest {

  @Autowired
  private ExpressionTypeInferencer inferencer;

  @Test
  void callIntoUnparsedModuleReportsIncompleteness() {
    // given: рабочая область добавлена, но её документы не разобраны.
    initServerContext(Path.of(PATH_TO_METADATA), false);
    var documentContext = TestUtils.getDocumentContext(
      Absolute.uri(Path.of(PATH_TO_METADATA, "Ext", "Вызывающий.bsl").toUri()),
      """
        Функция Значение() Экспорт
        	Возврат ОбщегоНазначения.НекийМетод();
        КонецФункции
        """,
      context);
    var method = documentContext.getSymbolTree().getMethods().get(0);

    // when
    var computed = inferencer.computeReturnTypes(method);

    // then: значение пустое, и расчёт это признаёт неполнотой.
    assertThat(computed.types().isEmpty())
      .as("членов у неразобранного модуля ещё нет, поэтому значение вызова пусто")
      .isTrue();
    assertThat(computed.incomplete())
      .as("расчёт сообщает о неполноте, чтобы проход пересчитал его после наполнения")
      .isTrue();
  }
}
