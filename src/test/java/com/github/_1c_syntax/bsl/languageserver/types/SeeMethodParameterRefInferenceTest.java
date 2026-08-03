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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ссылка {@code См.} на параметр метода другого модуля: тип параметра берётся
 * из описания метода-цели.
 */
@CleanupContextBeforeClassAndAfterClass
class SeeMethodParameterRefInferenceTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @BeforeEach
  void setUpWorkspace() {
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
  }

  @Test
  void seeRefToParameterOfCommonModuleMethod() {
    // given: у метода-цели параметр объявлен как «ИмяМодуля - Строка».
    var documentContext = TestUtils.getDocumentContext("""
      // Параметры:
      //  ИмяМодуля - См. ОбщегоНазначения.ОбщийМодуль.ИмяМодуля
      Процедура ОбработкаИмени(ИмяМодуля) Экспорт

      	ТипИмени = ИмяМодуля;

      КонецПроцедуры
      """, context);

    // when
    var types = at(documentContext, "ТипИмени = ИмяМодуля", "ТипИмени = ".length());

    // then
    assertThat(names(types)).containsExactly("Строка");
  }

  @Test
  void seeRefToCollectionParameterKeepsElementType() {
    // given: у метода-цели параметр объявлен как «Массив из Строка».
    var documentContext = TestUtils.getDocumentContext("""
      // Параметры:
      //  Строки - См. СправочникОбъект.СправочникСМенеджером.МетодМодуляОбъекта.Строки
      Процедура ОбработкаСтрок(Строки) Экспорт

      	ТипПараметра = Строки;

      	Для Каждого Элемент Из Строки Цикл
      		ТипЭлемента = Элемент;
      	КонецЦикла;

      КонецПроцедуры
      """, context);

    // when
    var parameter = at(documentContext, "ТипПараметра = Строки", "ТипПараметра = ".length());
    var element = at(documentContext, "ТипЭлемента = Элемент", "ТипЭлемента = ".length());

    // then: вместе с типом параметра приезжает и объявленный тип его элементов.
    assertThat(names(parameter)).containsExactly("Массив");
    assertThat(names(element)).containsExactly("Строка");
  }

  @Test
  void parameterTypesInheritedFromMethodOfAnotherModule() {
    // given: у метода нет описания параметров — только ссылка на метод-интерфейс
    // другого модуля, где параметр с тем же именем объявлен как «Строка».
    var documentContext = TestUtils.getDocumentContext("""
      // См. ОбщегоНазначения.ОбщийМодуль
      Процедура ОбработкаИмени(ИмяМодуля) Экспорт

      	ТипИмени = ИмяМодуля;

      КонецПроцедуры
      """, context);

    // when
    var types = at(documentContext, "ТипИмени = ИмяМодуля", "ТипИмени = ".length());

    // then
    assertThat(names(types)).containsExactly("Строка");
  }

  private TypeSet at(DocumentContext documentContext, String marker, int offsetInMarker) {
    var content = documentContext.getContent();
    var markerStart = content.indexOf(marker);
    assertThat(markerStart).as("маркер '%s' найден в фикстуре", marker).isNotNegative();
    var targetOffset = markerStart + offsetInMarker;
    var lineStart = content.lastIndexOf('\n', targetOffset - 1) + 1;
    var line = content.substring(0, targetOffset).split("\n").length - 1;
    return typeService.expressionTypesAt(documentContext, new Position(line, targetOffset - lineStart + 1));
  }

  private static List<String> names(TypeSet types) {
    return types.refs().stream().map(TypeRef::qualifiedName).toList();
  }
}
