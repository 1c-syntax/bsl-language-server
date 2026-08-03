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
 * Ссылка {@code См.} на форму по её полному имени — так, как форму называют
 * в документирующих комментариях общих модулей и модулей менеджеров.
 */
@CleanupContextBeforeClassAndAfterClass
class SeeFormRefInferenceTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @BeforeEach
  void setUpWorkspace() {
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
  }

  @Test
  void seeRefToFormGivesItsType() {
    // given: параметр типизирован ссылкой на конкретную форму справочника.
    var documentContext = documentWithFormReference();

    // when
    var types = at(documentContext, "ТипФормы = Форма", "ТипФормы = ".length());

    // then
    assertThat(names(types))
      .containsExactly("ФормаКлиентскогоПриложения.Справочник.Справочник1.Форма.ФормаЭлемента");
  }

  @Test
  void formFromSeeRefGivesItsAttributesAndItems() {
    // given: через тип формы доступны её основной реквизит и элементы.
    var documentContext = documentWithFormReference();

    // when
    var reference = at(documentContext, "СсылкаОбъекта = Форма.Объект.Ссылка",
      "СсылкаОбъекта = Форма.Объект.".length());
    var item = at(documentContext, "ЭлементФормы = Форма.Элементы.Наименование",
      "ЭлементФормы = Форма.Элементы.".length());

    // then
    assertThat(names(reference)).containsExactly("СправочникСсылка.Справочник1");
    assertThat(names(item)).containsExactly("ПолеФормы");
  }

  @Test
  void seeRefToFormAttributeGivesItsType() {
    // given: ссылка на основной реквизит формы — полное имя формы плюс имя реквизита.
    var documentContext = TestUtils.getDocumentContext("""
      // Параметры:
      //  Объект - См. Справочник.Справочник1.Форма.ФормаЭлемента.Объект
      Процедура ОбработкаОбъекта(Объект) Экспорт

      	ТипРеквизита = Объект;
      	ЗначениеРеквизита = Объект.Реквизит1;

      КонецПроцедуры
      """, context);

    // when
    var attribute = at(documentContext, "ТипРеквизита = Объект", "ТипРеквизита = ".length());
    var value = at(documentContext, "ЗначениеРеквизита = Объект.Реквизит1",
      "ЗначениеРеквизита = Объект.".length());

    // then: тот же тип, что и у «Форма.Объект», с реквизитами объекта.
    assertThat(names(attribute)).containsExactly("ДанныеФормыСтруктура.СправочникОбъект.Справочник1");
    assertThat(names(value)).containsExactly("Строка");
  }

  @Test
  void seeRefToFormItemGivesItsType() {
    // given: ссылка на элемент формы — полное имя формы, «Элементы» и имя элемента.
    var documentContext = TestUtils.getDocumentContext("""
      // Параметры:
      //  Элемент - См. Справочник.Справочник1.Форма.ФормаЭлемента.Элементы.Наименование
      Процедура ОбработкаЭлемента(Элемент) Экспорт

      	ТипЭлемента = Элемент;

      КонецПроцедуры
      """, context);

    // when
    var item = at(documentContext, "ТипЭлемента = Элемент", "ТипЭлемента = ".length());

    // then
    assertThat(names(item)).containsExactly("ПолеФормы");
  }

  @Test
  void refinedRowRecordGivesRowOfFormTable() {
    // given: голова записи говорит, что это строка, а ссылка указывает на таблицу формы.
    var documentContext = TestUtils.getDocumentContext("""
      // Параметры:
      //  СтрокаТаблицы - ДанныеФормыЭлементКоллекции: См. Справочник.Справочник1.Форма.ФормаСписка.Элементы.Список
      Процедура ОбработкаСтроки(СтрокаТаблицы) Экспорт

      	ТипСтроки = СтрокаТаблицы;

      КонецПроцедуры
      """, context);

    // when
    var row = at(documentContext, "ТипСтроки = СтрокаТаблицы", "ТипСтроки = ".length());

    // then: строка таблицы, а не сама таблица — такую же строку даёт «Список.ТекущиеДанные».
    assertThat(names(row)).containsExactly("ДанныеФормыЭлементКоллекции.ДинамическийСписок");
  }

  private DocumentContext documentWithFormReference() {
    return TestUtils.getDocumentContext("""
      // Параметры:
      //  Форма - См. Справочник.Справочник1.Форма.ФормаЭлемента
      Процедура ПриСозданииНаСервере(Форма) Экспорт

      	ТипФормы = Форма;
      	СсылкаОбъекта = Форма.Объект.Ссылка;
      	ЭлементФормы = Форма.Элементы.Наименование;

      КонецПроцедуры
      """, context);
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
