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

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@CleanupContextBeforeClassAndAfterClass
class OScriptIterableTest extends AbstractServerContextAwareTest {

  @Autowired
  private OScriptIterable oScriptIterable;

  @BeforeEach
  void init() {
    initServerContext();
  }

  @Test
  void isIterableTrueForIterableMarker() {
    // given — конструктор помечен &Обходимое (маркер обходимой коллекции).
    var dc = os("&Обходимое\nПроцедура ПриСозданииОбъекта()\nКонецПроцедуры\n");

    // when / then
    assertThat(oScriptIterable.isIterable(dc)).isTrue();
  }

  @Test
  void isIterableTrueForEnglishMarker() {
    // given — английский псевдоним аннотации &Iterable.
    var dc = os("&Iterable\nПроцедура ПриСозданииОбъекта()\nКонецПроцедуры\n");

    // when / then
    assertThat(oScriptIterable.isIterable(dc)).isTrue();
  }

  @Test
  void isIterableFalseForPlainClassAndBsl() {
    // given — обычный класс без маркера и bsl-файл.
    var os = os("Процедура ПриСозданииОбъекта()\nКонецПроцедуры\n");
    var bsl = TestUtils.getDocumentContext("Процедура П()\nКонецПроцедуры\n");

    // when / then
    assertThat(oScriptIterable.isIterable(os)).isFalse();
    assertThat(oScriptIterable.isIterable(bsl)).isFalse();
  }

  @Test
  void isIterableIgnoresAnnotationOnNonConstructorMethod() {
    // given — &Обходимое на вспомогательном методе, а не на конструкторе.
    var dc = os("""
      Процедура ПриСозданииОбъекта()
      КонецПроцедуры

      &Обходимое
      Процедура Вспомогательный()
      КонецПроцедуры
      """);

    // when / then — коллекция не объявлена (аннотация не на конструкторе).
    assertThat(oScriptIterable.isIterable(dc)).isFalse();
  }

  private DocumentContext os(String content) {
    return TestUtils.getDocumentContext(TestUtils.FAKE_OSCRIPT_DOCUMENT_URI, content, context);
  }
}
