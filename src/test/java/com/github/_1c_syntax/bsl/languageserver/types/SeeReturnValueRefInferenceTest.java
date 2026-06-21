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
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ссылка {@code см. Метод} в описании возвращаемого значения функции
 * (// Возвращаемое значение: см. КонструкторСтруктуры) должна разрешаться
 * в тип возвращаемого значения целевой функции — в т.ч. когда после имени
 * метода идёт текстовое описание ({@code см. Метод - описание}). См. #4178.
 */
@CleanupContextBeforeClassAndAfterClass
class SeeReturnValueRefInferenceTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @Test
  void returnValueSeeRefWithTrailingComment() {
    var t = at("ОтветСКомментарием = ОтправитьЗапрос()", "ОтветСКомментарием = ".length());
    assertThat(t.refs())
      .as("`см. СхемаОтвета - описание` разрешается в возвращаемый тип СхемаОтвета (Структура)")
      .extracting(ref -> ref.qualifiedName())
      .containsExactly("Структура");
  }

  @Test
  void returnValueSeeRefWithoutTrailingComment() {
    var t = at("ОтветБезКомментария = ОтправитьЗапрос2()", "ОтветБезКомментария = ".length());
    assertThat(t.refs())
      .as("`см. СхемаОтвета` разрешается в возвращаемый тип СхемаОтвета (Структура)")
      .extracting(ref -> ref.qualifiedName())
      .containsExactly("Структура");
  }

  private TypeSet at(String marker, int offsetInMarker) {
    var dc = doc();
    var content = dc.getContent();
    int markerStart = content.indexOf(marker);
    int targetOffset = markerStart + offsetInMarker;
    int lineStart = content.lastIndexOf('\n', targetOffset) + 1;
    int line = content.substring(0, targetOffset).split("\n").length - 1;
    int charInLine = targetOffset - lineStart;
    return typeService.expressionTypesAt(dc, new Position(line, charInLine + 1));
  }

  private DocumentContext doc() {
    return TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/SeeReturnValueRef.bsl");
  }
}
