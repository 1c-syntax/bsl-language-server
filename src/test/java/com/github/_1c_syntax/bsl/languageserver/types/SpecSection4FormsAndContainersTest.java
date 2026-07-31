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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;

import static com.github._1c_syntax.bsl.languageserver.types.SpecProbes.fieldNames;
import static com.github._1c_syntax.bsl.languageserver.types.SpecProbes.names;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Сверка с методической рекомендацией «Типизация кода», раздел «Лучшие практики»:
 * работа с формами, контейнерами, выборками запроса и макетами (пункты 4.43–4.67).
 * <p>
 * Один тест — один пункт рекомендации, номер пункта указан в {@code @DisplayName}.
 * Тест выражает требование рекомендации, а не текущее поведение: пока пункт не закрыт,
 * тест красный, и закрытие пункта его чинит. Исключение — места, где мы точнее
 * рекомендации: там проверяется наше поведение.
 */
@CleanupContextBeforeClassAndAfterClass
class SpecSection4FormsAndContainersTest extends AbstractServerContextAwareTest {

  /** Имя типа формы, как его пишет рекомендация. */
  private static final String FORM_TYPE = "ФормаКлиентскогоПриложения";

  @Autowired
  private TypeService typeService;

  @BeforeEach
  void prepareMetadataServerContext() {
    initServerContextOnce(Path.of(TestUtils.PATH_TO_METADATA));
  }

  // --- Описание частей формы ---------------------------------------------------

  @Test
  @DisplayName("4.43 Описание части формы: «ФормаКлиентскогоПриложения:» с «* Объект»")
  void formMainAttributeFromDocComment() {
    // given / when
    var types = typeOf("4.43");

    // then: совпадает с рекомендацией.
    assertThat(names(types)).containsExactly("СправочникОбъект.Справочник1");
  }

  @Test
  @DisplayName("4.44 Описание элементов формы: «* Элементы - ВсеЭлементыФормы:» с «** Товары»")
  void formItemsFromDocComment() {
    // given / when
    var types = typeOf("4.44");

    // then: совпадает с рекомендацией.
    assertThat(names(types)).containsExactly("ТаблицаФормы");
  }

  @Test
  @DisplayName("4.45 Ссылка на конкретную форму в описании параметра общего модуля")
  void seeRefToConcreteForm() {
    // given: «Форма - см. Справочник.Справочник1.Форма.ФормаЭлемента», обращение к «Объект».
    // when
    var types = typeOf("4.45");
    var reference = typeOfVariable("Проба_4_45_Ссылка");
    var formItem = typeOfVariable("Проба_4_45_Элемент");

    // then: поведение из рекомендации (строки 1605-1606).
    assertThat(names(types))
      .as("рекомендация: через ссылку на форму доступен её основной реквизит")
      .containsExactly("ДанныеФормыСтруктура");
    assertThat(names(reference))
      .as("рекомендация: «Ссылка = Форма.Объект.Ссылка»")
      .containsExactly("СправочникСсылка.Справочник1");
    assertThat(names(formItem))
      .as("рекомендация: «Форма.Элементы.Артикул.Видимость = Истина»")
      .containsExactly("ПолеФормы");
  }

  @Test
  @DisplayName("4.46 Тип расширения управляемой формы")
  void managedFormExtensionType() {
    // given / when
    var types = typeOf("4.46");

    // then: совпадает с рекомендацией.
    assertThat(names(types)).containsExactly("РасширениеУправляемойФормыДляОбъектов");
  }

  // --- Получение и открытие формы ----------------------------------------------

  @Test
  @DisplayName("4.47 «ПолучитьФорму(\"полное.имя\")» даёт конкретный тип формы")
  void getFormByFullName() {
    // given / when
    var types = typeOf("4.47");
    var reference = typeOfVariable("Проба_4_47_Ссылка");

    // then: рекомендация требует, чтобы был доступен весь контекст этой формы.
    assertThat(names(types)).containsExactly(FORM_TYPE);
    assertThat(names(reference))
      .as("рекомендация: у полученной формы доступен её основной реквизит")
      .containsExactly("СправочникСсылка.Справочник1");
  }

  @Test
  @DisplayName("4.48 «Справочники.X.ПолучитьФорму(\"ФормаЭлемента\")» даёт конкретный тип формы")
  void getFormThroughManager() {
    // given / when
    var types = typeOf("4.48");
    var reference = typeOfVariable("Проба_4_48_Ссылка");

    // then
    assertThat(names(types)).containsExactly(FORM_TYPE);
    assertThat(names(reference))
      .as("рекомендация: у полученной формы доступен её основной реквизит")
      .containsExactly("СправочникСсылка.Справочник1");
  }

  @Test
  @DisplayName("4.49 Имя формы в переменной даёт общий тип «ФормаКлиентскогоПриложения»")
  void formNameInVariableGivesGenericType() {
    // given / when
    var types = typeOf("4.49");

    // then
    assertThat(names(types))
      .as("рекомендация: имя в переменной даёт общий тип формы, а не отсутствие типа")
      .containsExactly("ФормаКлиентскогоПриложения");
  }

  @Test
  @DisplayName("4.50 Имя основной формы даёт общий тип формы")
  void defaultFormNameGivesGenericType() {
    // given: «ПолучитьФорму(\"Справочник.Справочник1.ФормаОбъекта\")».
    // when
    var types = typeOf("4.50");

    // then: рекомендация (строка 1626) говорит, что ссылки на основные формы возвращают
    // общий тип «ФормаКлиентскогоПриложения».
    assertThat(names(types)).containsExactly(FORM_TYPE);
  }

  // --- Контейнеры --------------------------------------------------------------

  @Test
  @DisplayName("4.53 Значение из временного хранилища типизируется строчной ссылкой")
  void valueFromTemporaryStorageBySeeRef() {
    // given / when
    var types = typeOf("4.53");

    // then
    assertThat(names(types))
      .as("рекомендация: значение из временного хранилища получает тип конструктора")
      .containsExactly("Структура");
    assertThat(fieldNames(types))
      .as("рекомендация: вместе с типом приходят его поля")
      .containsExactlyInAnyOrder("Ссылка", "Количество");
  }

  @Test
  @DisplayName("4.54 Тот же приём для «ДополнительныеПараметры» и прочих контейнеров")
  void valueFromAdditionalParametersBySeeRef() {
    // given / when
    var types = typeOf("4.54");

    // then
    assertThat(names(types))
      .as("рекомендация: тот же приём работает и для полей объектов-контейнеров")
      .containsExactly("Структура");
  }

  @Test
  @DisplayName("4.58 «Параметры.Свойство(\"Ссылка\")» и чтение по индексу со строчным типом")
  void readByStringIndexWithInlineType() {
    // given / when
    var types = typeOf("4.58");

    // then: совпадает с рекомендацией.
    assertThat(names(types)).containsExactly("СправочникСсылка.Справочник1");
  }

  // --- Выборка запроса, передача объекта, макеты --------------------------------

  @Test
  @DisplayName("4.63 Поля выборки описаны в возвращаемом значении функции-получателя")
  void querySelectionFieldsFromDocComment() {
    // given / when
    var types = typeOf("4.63");

    // then: совпадает с рекомендацией.
    assertThat(names(types)).containsExactly("СправочникСсылка.Справочник1");
  }

  @Test
  @DisplayName("4.64 Поля выборки выводятся из текста запроса в той же процедуре")
  void querySelectionFieldsFromQueryText() {
    // given / when
    var types = typeOf("4.64");

    // then: рекомендация приводит эту запись как правильную, отмечая, что в 1C:EDT
    // динамическая типизация полей выборки отнесена к будущим версиям.
    assertThat(names(types))
      .as("рекомендация: поле выборки типизируется по тексту запроса")
      .containsExactly("СправочникСсылка.Справочник1");
  }

  @Test
  @DisplayName("4.65 Объект данных, уходящий в другой модуль, описан возвращаемым значением")
  void dataObjectPassedToAnotherModule() {
    // given / when
    var types = typeOf("4.65");

    // then: совпадает с рекомендацией — прямой межмодульный вызов переносит поля.
    assertThat(names(types)).containsExactly("Структура");
    assertThat(fieldNames(types)).contains("Подпись", "Комментарий", "ДатаПодписи");
  }

  @Test
  @DisplayName("4.66 Тип макета задан строчной ссылкой")
  void templateTypeByInlineSeeRef() {
    // given / when
    var types = typeOf("4.66");

    // then: раздел рекомендации называется «Описание типов макетов СКД и Табличного
    // документа», а макет фикстуры — табличный документ.
    assertThat(names(types))
      .as("рекомендация: строчная ссылка задаёт тип макета")
      .containsExactly("ТабличныйДокумент");
  }

  @Test
  @DisplayName("4.67 Смешанный тип «Структура + СправочникОбъект.X» в функции-конструкторе")
  void mixedTypeOfObjectCopy() {
    // given / when
    var types = typeOf("4.67");
    var extraColumn = typeOfVariable("Проба_4_67_Колонка");
    var objectAttribute = typeOfVariable("Проба_4_67_Реквизит");

    // then: рекомендация (строка 1845) допускает смешанный тип и добавление к нему
    // дополнительных колонок для технических целей.
    assertThat(names(types))
      .containsExactlyInAnyOrder("Структура", "СправочникОбъект.Справочник1");
    assertThat(names(extraColumn))
      .as("рекомендация: дополнительная колонка смешанного типа доступна")
      .containsExactly("Строка");
    assertThat(names(objectAttribute))
      .as("рекомендация: реквизиты объекта в смешанном типе доступны")
      .containsExactly("Строка");
  }

  private TypeSet typeOf(String item) {
    return SpecProbes.typeOf(typeService, document(), item);
  }

  private TypeSet typeOfVariable(String variable) {
    return SpecProbes.typeOfVariable(typeService, document(), variable);
  }

  private static DocumentContext document() {
    return TestUtils.getDocumentContextFromFile(SpecProbes.SECTION_4);
  }
}
