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
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ключ структуры известен из {@code Вставить} даже тогда, когда тип его значения вывести
 * не удалось: знание об имени поля и знание о его типе — разные вещи.
 */
@CleanupContextBeforeClassAndAfterClass
class StructureFieldWithoutTypeTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @Test
  void fieldNameSurvivesWhenItsValueTypeIsUnknown() {
    // given
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/StructureFieldWithoutType.bsl");

    // when
    var types = typeService.expressionTypesAt(documentContext,
      positionOf(documentContext, "ОписаниеРезультата(Параметр);"));

    // then: поле, значение которого вывести не удалось, из типа не пропадает — иначе
    // обращение к нему выглядит обращением к несуществующему свойству.
    assertThat(types.getAllFieldNames())
      .contains("Известное", "Пустое", "Невыводимое");
  }

  /** Позиция первого вхождения текста в документе — на его первом символе. */
  private static Position positionOf(DocumentContext documentContext, String text) {
    var content = documentContext.getContent();
    var index = content.indexOf(text);
    var line = content.substring(0, index).split("\n").length - 1;
    var lineStart = content.lastIndexOf('\n', index) + 1;
    return new Position(line, index - lineStart + 1);
  }
}
