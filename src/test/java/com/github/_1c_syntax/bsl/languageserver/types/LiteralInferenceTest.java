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
 * Покрывает {@code inferLiteral} (все типы литералов) и edge-cases
 * индексатора в {@link com.github._1c_syntax.bsl.languageserver.types.inferencer.ExpressionTypeInferencer}.
 */
@CleanupContextBeforeClassAndAfterClass
class LiteralInferenceTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @Test
  void stringLiteralIsString() {
    assertThat(qnames(at("СтроковыйЛитерал = \"строка\"",
      "СтроковыйЛитерал = ".length())))
      .containsExactly("Строка");
  }

  @Test
  void numericLiteralIsNumber() {
    assertThat(qnames(at("ЧисловойЛитерал = 12345",
      "ЧисловойЛитерал = ".length())))
      .containsExactly("Число");
  }

  @Test
  void booleanTrueIsBoolean() {
    assertThat(qnames(at("ЛогическийИстина = Истина", "ЛогическийИстина = ".length())))
      .containsExactly("Булево");
  }

  @Test
  void booleanFalseIsBoolean() {
    assertThat(qnames(at("ЛогическийЛожь = Ложь", "ЛогическийЛожь = ".length())))
      .containsExactly("Булево");
  }

  @Test
  void dateLiteralIsDate() {
    assertThat(qnames(at("ДатаЛитерал = '20200101'", "ДатаЛитерал = ".length())))
      .containsExactly("Дата");
  }

  @Test
  void undefinedLiteralIsUndefined() {
    assertThat(qnames(at("ЛитералНеопределено = Неопределено",
      "ЛитералНеопределено = ".length())))
      .containsExactly("Неопределено");
  }

  @Test
  void nullLiteralIsNull() {
    assertThat(qnames(at("ЛитералNull = NULL", "ЛитералNull = ".length())))
      .containsExactly("Null");
  }

  @Test
  void mapDynamicKeyUnionsAllValueTypes() {
    // given — ключ — переменная (не строковый литерал); ожидается union value-типов.
    var types = at("ЗначениеПоДинамКлючу = Карта[ДинамКлюч]",
      "ЗначениеПоДинамКлючу = ".length());

    // then
    assertThat(qnames(types)).containsExactlyInAnyOrder("Число", "Строка");
  }

  @Test
  void structureUnknownStringKeyReturnsEmpty() {
    // given / when
    var types = at("НесуществующееПоле = Стр[\"НеТакоеИмя\"]",
      "НесуществующееПоле = ".length());

    // then
    assertThat(types.refs()).isEmpty();
  }

  @Test
  void unaryPlusIsNumber() {
    assertThat(qnames(at("ПозитивноеЧисло = +100", "ПозитивноеЧисло = ".length())))
      .containsExactly("Число");
  }

  @Test
  void floatLiteralIsNumber() {
    assertThat(qnames(at("ДробноеЧисло = 12.345", "ДробноеЧисло = ".length())))
      .containsExactly("Число");
  }

  private TypeSet at(String marker, int offsetInMarker) {
    var documentContext = doc();
    var content = documentContext.getContent();
    int markerStart = content.indexOf(marker);
    int targetOffset = markerStart + offsetInMarker;
    int lineStart = content.lastIndexOf('\n', targetOffset) + 1;
    int line = content.substring(0, targetOffset).split("\n").length - 1;
    int charInLine = targetOffset - lineStart;
    return typeService.expressionTypesAt(documentContext, new Position(line, charInLine + 1));
  }

  private DocumentContext doc() {
    return TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/LiteralInference.bsl");
  }

  private static java.util.List<String> qnames(TypeSet ts) {
    return ts.refs().stream().map(r -> r.qualifiedName()).toList();
  }
}
