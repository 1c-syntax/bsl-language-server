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
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Возвращаемое значение-{@code Структура}, у поля которого тип записан на
 * нескольких строках (составной тип, std453 п.5.3), должно выводиться как
 * единый тип со всеми полями, а не «разрезаться» continuation-строкой типа
 * на второй тип верхнего уровня (регресс на разбор сложного типа в bsl-parser).
 */
@CleanupContextBeforeClassAndAfterClass
class ReturnStructureMultilineFieldTypeInferenceTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @Test
  void multilineFieldTypeKeepsSingleStructureWithAllFields() {
    // given: функция с JsDoc-возвратом Структура, у поля "Подпись" составной тип на двух строках.
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/ReturnStructureMultilineFieldType.bsl");

    // when: выводим тип переменной, присвоенной из вызова функции.
    var types = inferAtMarker(documentContext, "Свойства = НовыеСвойстваПодписи()", 1);

    // then: ровно один тип Структура (не разрезан на Структура + Строка)...
    assertThat(types.refs())
      .extracting(TypeRef::qualifiedName)
      .containsExactly("Структура");

    // ...со всеми полями, объявленными в JsDoc.
    var structureRef = types.refs().iterator().next();
    assertThat(types.getLocalFields(structureRef).keySet())
      .contains("Подпись", "Комментарий", "ДатаПодписи");

    // у поля с многострочным составным типом сохранены оба типа.
    assertThat(types.getLocalFields(structureRef).get("Подпись").types().refs())
      .extracting(TypeRef::qualifiedName)
      .contains("ДвоичныеДанные", "Строка");
  }

  private TypeSet inferAtMarker(DocumentContext documentContext, String marker, int offsetInMarker) {
    var content = documentContext.getContent();
    int markerStart = content.indexOf(marker);
    assertThat(markerStart).as("маркер '%s' должен быть в фикстуре", marker).isGreaterThanOrEqualTo(0);
    int targetOffset = markerStart + offsetInMarker;
    int lineStart = content.lastIndexOf('\n', targetOffset) + 1;
    int line = content.substring(0, targetOffset).split("\n").length - 1;
    int charInLine = targetOffset - lineStart;
    return typeService.expressionTypesAt(documentContext, new Position(line, charInLine));
  }
}
