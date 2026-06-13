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
 * Покрытие веток инференции {@code Новый ОписаниеТипов(...)} в
 * {@link com.github._1c_syntax.bsl.languageserver.types.inferencer.ExpressionTypeInferencer#applyTypeDescriptionConstructorTypes}.
 */
@CleanupContextBeforeClassAndAfterClass
class TypeDescriptionConstructorInferenceTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @Test
  void singleTypeStringPopulatesElementTypes() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/TypeDescriptionConstructor.bsl");

    // when
    var types = inferAtMarker(documentContext, "ОписаниеЧисла = Новый ОписаниеТипов(\"Число\")",
      "ОписаниеЧисла = ".length());

    // then
    assertThat(types.refs())
      .extracting(ref -> ref.qualifiedName())
      .contains("ОписаниеТипов");
    var ref = types.refs().iterator().next();
    assertThat(types.getElementTypes(ref).refs())
      .extracting(r -> r.qualifiedName())
      .contains("Число");
  }

  @Test
  void multipleTypesPopulateElementTypes() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/TypeDescriptionConstructor.bsl");

    // when
    var types = inferAtMarker(documentContext,
      "ОписаниеСтрокиИЧисла = Новый ОписаниеТипов(\"Строка, Число\")",
      "ОписаниеСтрокиИЧисла = ".length());

    // then
    var ref = types.refs().iterator().next();
    assertThat(types.getElementTypes(ref).refs())
      .extracting(r -> r.qualifiedName())
      .containsExactlyInAnyOrder("Строка", "Число");
  }

  @Test
  void emptyArgListReturnsBareTypeDescription() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/TypeDescriptionConstructor.bsl");

    // when — пустой конструктор не имеет аргумента, applyTypeDescriptionConstructorTypes
    // возвращает базу как есть.
    var types = inferAtMarker(documentContext, "ПустоеОписание = Новый ОписаниеТипов()",
      "ПустоеОписание = ".length());

    // then
    assertThat(types.refs())
      .extracting(ref -> ref.qualifiedName())
      .contains("ОписаниеТипов");
  }

  @Test
  void unknownTypeNameInLiteralProducesNoElementType() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/TypeDescriptionConstructor.bsl");

    // when
    var types = inferAtMarker(documentContext,
      "ОписаниеНеизвестногоТипа = Новый ОписаниеТипов(\"НеизвестныйТипX\")",
      "ОписаниеНеизвестногоТипа = ".length());

    // then — у element-types нет «НеизвестныйТипX» (имя не зарезолвилось)
    assertThat(types.refs())
      .extracting(ref -> ref.qualifiedName())
      .contains("ОписаниеТипов");
    var ref = types.refs().iterator().next();
    assertThat(types.getElementTypes(ref).refs())
      .extracting(r -> r.qualifiedName())
      .doesNotContain("НеизвестныйТипX");
  }

  @Test
  void typeDescriptionWithNonStringFirstArgReturnsBaseUnchanged() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/TypeDescriptionConstructor.bsl");

    // when — literal == null → return base.
    var types = inferAtMarker(documentContext,
      "ОписаниеНеСтрокаАрг = Новый ОписаниеТипов(КакаяТоПеременная)",
      "ОписаниеНеСтрокаАрг = ".length());

    // then — base preserved, есть ref ОписаниеТипов.
    assertThat(types.refs()).isNotEmpty();
    var ref = types.refs().iterator().next();
    assertThat(ref.qualifiedName()).isEqualToIgnoringCase("ОписаниеТипов");
  }

  @Test
  void emptyTypeNamesBetweenCommasAreSkipped() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/TypeDescriptionConstructor.bsl");

    // when
    var types = inferAtMarker(documentContext,
      "ОписаниеСПустыми = Новый ОписаниеТипов(\",Число, ,Строка,\")",
      "ОписаниеСПустыми = ".length());

    // then — пустые элементы между запятыми отброшены, валидные собраны.
    var ref = types.refs().iterator().next();
    assertThat(types.getElementTypes(ref).refs())
      .extracting(r -> r.qualifiedName())
      .containsExactlyInAnyOrder("Число", "Строка");
  }

  @Test
  void englishConstructorTypeDescriptionAlsoTriggersTypeExtraction() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/TypeDescriptionConstructor.bsl");

    // when
    var types = inferAtMarker(documentContext, "TypeOfNumbers = Новый TypeDescription(\"Число\")",
      "TypeOfNumbers = ".length());

    // then
    assertThat(types.refs()).isNotEmpty();
    var ref = types.refs().iterator().next();
    assertThat(types.getElementTypes(ref).refs())
      .extracting(r -> r.qualifiedName())
      .contains("Число");
  }

  private TypeSet inferAtMarker(DocumentContext documentContext, String marker, int offsetInMarker) {
    var content = documentContext.getContent();
    int markerStart = content.indexOf(marker);
    int targetOffset = markerStart + offsetInMarker;
    int lineStart = content.lastIndexOf('\n', targetOffset) + 1;
    int line = content.substring(0, targetOffset).split("\n").length - 1;
    int charInLine = targetOffset - lineStart;
    return typeService.expressionTypesAt(documentContext, new Position(line, charInLine + 1));
  }
}
