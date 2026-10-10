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
 * Тип по {@code См.}-ссылке на макет объекта метаданных: значение макета — то, что отдаёт его
 * получение ({@code ПолучитьМакет}), и вид этого значения задан видом макета в метаданных.
 */
@CleanupContextBeforeClassAndAfterClass
class SeeTemplateRefInferenceTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @BeforeEach
  void setUpWorkspace() {
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
  }

  @Test
  void seeRefInAssignmentGivesTemplateValueType() {
    // given: макет получен универсальной функцией, а его вид назван ссылкой в строке.
    // Макет справочника в тестовой конфигурации — табличный документ.
    var documentContext = TestUtils.getDocumentContext("""
      Процедура ПолучениеМакета() Экспорт

      	Макет = ПолучитьОбщийМакет("Имя"); // см. Справочник.Справочник1.Макет.Макет
      	ТипМакета = Макет;

      КонецПроцедуры
      """, context);

    // when
    var types = at(documentContext, "ТипМакета = Макет", "ТипМакета = ".length());

    // then
    assertThat(names(types)).containsExactly("ТабличныйДокумент");
  }

  @Test
  void seeRefInParameterGivesTemplateValueType() {
    // given: параметр типизирован ссылкой на макет.
    var documentContext = TestUtils.getDocumentContext("""
      // Параметры:
      //  Макет - см. Справочник.Справочник1.Макет.Макет
      Процедура ВыводМакета(Макет) Экспорт

      	ТипМакета = Макет;

      КонецПроцедуры
      """, context);

    // when
    var types = at(documentContext, "ТипМакета = Макет", "ТипМакета = ".length());

    // then
    assertThat(names(types)).containsExactly("ТабличныйДокумент");
  }

  @Test
  void seeRefToCommonTemplateGivesTemplateValueType() {
    // given: общий макет конфигурации — схема компоновки данных.
    var documentContext = TestUtils.getDocumentContext("""
      Процедура ПолучениеОбщегоМакета() Экспорт

      	Макет = ПолучитьОбщийМакет("Имя"); // см. ОбщийМакет.ДанныеПечатиРегистрСимволов
      	ТипМакета = Макет;

      КонецПроцедуры
      """, context);

    // when
    var types = at(documentContext, "ТипМакета = Макет", "ТипМакета = ".length());

    // then
    assertThat(names(types)).containsExactly("СхемаКомпоновкиДанных");
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
