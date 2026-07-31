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
 * Сверка с методической рекомендацией «Типизация кода», раздел «Возможности типизирующих
 * документирующих комментариев»: синтаксис секций и ссылки на типы.
 * <p>
 * Один тест — один пункт рекомендации, номер пункта указан в {@code @DisplayName}.
 * Тест выражает требование рекомендации, а не текущее поведение: пока пункт не закрыт,
 * тест красный, и закрытие пункта его чинит.
 */
@CleanupContextBeforeClassAndAfterClass
class SpecSection3Test extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @BeforeEach
  void prepareMetadataServerContext() {
    initServerContextOnce(Path.of(TestUtils.PATH_TO_METADATA));
  }

  // --- Описание структуры данных документирующего комментария -----------------

  @Test
  @DisplayName("3.2 «Параметры:» с двоеточием открывает секцию параметров")
  void parameterSectionWithColon() {
    // given / when
    var types = typeOf("3.2");

    // then: совпадает с рекомендацией.
    assertThat(names(types)).containsExactly("СправочникОбъект.Справочник1");
  }

  @Test
  @DisplayName("3.3 «Параметры» без двоеточия — это описание метода, а не секция")
  void parameterSectionWithoutColon() {
    // given / when
    var types = typeOf("3.3");

    // then: совпадает с рекомендацией.
    assertThat(names(types)).isEmpty();
  }

  @Test
  @DisplayName("3.4 Параметр с описанием и без объявления типов")
  void parameterWithoutTypes() {
    // given / when
    var types = typeOf("3.4");

    // then: совпадает с рекомендацией.
    assertThat(names(types)).isEmpty();
  }

  @Test
  @DisplayName("3.5 Описание параметра после секции типов")
  void descriptionAfterTypeSection() {
    // given / when
    var types = typeOf("3.5");

    // then: совпадает с рекомендацией.
    assertThat(names(types)).containsExactly("СправочникОбъект.Справочник1");
  }

  @Test
  @DisplayName("3.6 Каждый тип с новой строки через дефис, у каждого своё описание")
  void typesOnSeparateLines() {
    // given / when
    var types = typeOf("3.6");

    // then: совпадает с рекомендацией.
    assertThat(names(types))
      .containsExactlyInAnyOrder("СправочникОбъект.Справочник1", "ДокументОбъект.Документ1");
  }

  @Test
  @DisplayName("3.7 Виды тире, кроме дефиса-минуса, не допускаются")
  void emDashIsNotAllowedAsSeparator() {
    // given: тип и описание разделены длинным тире.
    // when
    var types = typeOf("3.7");

    // then
    assertThat(names(types))
      .as("рекомендация: разделителем считается только дефис-минус, значит типов здесь нет")
      .isEmpty();
  }

  @Test
  @DisplayName("3.8 Несколько типов через запятую")
  void typesSeparatedByComma() {
    // given / when
    var types = typeOf("3.8");

    // then: совпадает с рекомендацией.
    assertThat(names(types))
      .containsExactlyInAnyOrder("СправочникОбъект.Справочник1", "ДокументОбъект.Документ1");
  }

  @Test
  @DisplayName("3.9 Многострочное описание параметра")
  void multilineParameterDescription() {
    // given / when
    var types = typeOf("3.9");

    // then: совпадает с рекомендацией.
    assertThat(names(types)).containsExactly("СправочникОбъект.Справочник1");
  }

  @Test
  @DisplayName("3.10 «Массив из <Тип>» — единственный тип элементов коллекции")
  void singleCollectionElementType() {
    // given / when
    var types = typeOf("3.10");

    // then: совпадает с рекомендацией.
    assertThat(names(types)).containsExactly("СправочникСсылка.Справочник1");
  }

  @Test
  @DisplayName("3.11 «Массив из Т1, Т2» — составной тип элементов коллекции")
  void compositeCollectionElementTypes() {
    // given / when
    var types = typeOf("3.11");

    // then: совпадает с рекомендацией.
    assertThat(names(types))
      .containsExactlyInAnyOrder("СправочникСсылка.Справочник1", "ДокументСсылка.Документ1");
  }

  @Test
  @DisplayName("3.12 Расширение полей после двоеточия с вложенностью «*» и «**»")
  void nestedFieldExtension() {
    // given / when
    var types = typeOf("3.12");

    // then: совпадает с рекомендацией.
    assertThat(names(types)).containsExactly("ТаблицаЗначений");
    assertThat(fieldNames(types)).containsExactly("ИмяКолонки");
  }

  @Test
  @DisplayName("3.13 Расширение полей без описаний, только типы и двоеточие")
  void fieldExtensionWithoutDescriptions() {
    // given / when
    var types = typeOf("3.13");

    // then: совпадает с рекомендацией.
    assertThat(names(types)).containsExactly("ТаблицаЗначений");
    assertThat(fieldNames(types)).containsExactly("ИмяКолонки");
  }

  @Test
  @DisplayName("3.14 Завершающий дефис явно обозначает секцию типов")
  void trailingHyphenMarksTypeSection() {
    // given / when
    var types = typeOf("3.14");

    // then: совпадает с рекомендацией.
    assertThat(names(types)).containsExactlyInAnyOrder("Структура", "ТаблицаЗначений");
  }

  @Test
  @DisplayName("3.15 «Возвращаемое значение:» со списком типов без дефиса — это описание")
  void returnTypesWithoutTrailingHyphenAreDescription() {
    // given / when
    var types = typeOf("3.15");

    // then
    assertThat(names(types))
      .as("рекомендация: без завершающего дефиса это описание, а не декларация типов")
      .isEmpty();
  }

  @Test
  @DisplayName("3.16 «Возвращаемое значение:» со списком типов и завершающим дефисом")
  void returnTypesWithTrailingHyphen() {
    // given / when
    var types = typeOf("3.16");

    // then: совпадает с рекомендацией.
    assertThat(names(types)).containsExactlyInAnyOrder("Структура", "ТаблицаЗначений");
  }

  @Test
  @DisplayName("3.17 «Возвращаемое значение:» списком, каждый тип с новой строки через дефис")
  void returnTypesAsList() {
    // given / when
    var types = typeOf("3.17");

    // then: совпадает с рекомендацией.
    assertThat(names(types)).containsExactlyInAnyOrder("Структура", "ТаблицаЗначений");
  }

  @Test
  @DisplayName("3.18 Ссылка на конструктор рядом с типом параметра бесполезна")
  void seeRefNextToTypeIsUseless() {
    // given / when
    var types = typeOf("3.18");

    // then: совпадает с рекомендацией — работает только сам тип, полей от ссылки нет.
    assertThat(names(types)).containsExactly("ТаблицаЗначений");
    assertThat(fieldNames(types)).isEmpty();
  }

  @Test
  @DisplayName("3.19 Ссылка вместо типа параметра")
  void seeRefInsteadOfType() {
    // given / when
    var types = typeOf("3.19");

    // then: совпадает с рекомендацией — приходит тип конструктора вместе с полями.
    assertThat(names(types)).containsExactly("Структура");
    assertThat(fieldNames(types)).containsExactlyInAnyOrder("Ссылка", "Количество");
  }

  @Test
  @DisplayName("3.20 Ссылка на тип в возвращаемом значении")
  void seeRefInReturnValue() {
    // given: пример рекомендации — «Возвращаемое значение: См. Справочник.Товары.ЕдиницыИзмерения»,
    // то есть ссылка на табличную часть.
    // when
    var types = typeOf("3.20");
    var attribute = typeOfVariable("Проба_3_20_Реквизит");

    // then
    assertThat(names(types))
      .as("рекомендация: возвращаемое значение получает тип по ссылке")
      .containsExactly("ТабличнаяЧасть");
    assertThat(names(attribute))
      .as("рекомендация: с этим типом работают как с табличной частью — обходят и читают реквизит")
      .containsExactly("Строка");
  }

  @Test
  @DisplayName("3.21 Ссылка на локальную не экспортную функцию-конструктор в возвращаемом значении")
  void seeRefToLocalConstructorInReturnValue() {
    // given / when
    var types = typeOf("3.21");

    // then: совпадает с рекомендацией.
    assertThat(names(types)).containsExactly("Структура");
    assertThat(fieldNames(types)).containsExactlyInAnyOrder("Ссылка", "Количество");
  }

  // --- Ссылки на типы из объектов метаданных ----------------------------------

  @Test
  @DisplayName("3.22 Ссылка на табличную часть объекта")
  void seeRefToTabularSection() {
    // given / when
    var types = typeOf("3.22");
    var attribute = typeOfVariable("Проба_3_22_Реквизит");

    // then
    assertThat(names(types))
      .as("рекомендация: параметр получает тип табличной части")
      .containsExactly("ТабличнаяЧасть");
    assertThat(names(attribute))
      .as("рекомендация: табличную часть обходят и читают реквизит её строки")
      .containsExactly("Строка");
  }

  @Test
  @DisplayName("3.23 Ссылка на строку табличной части")
  void seeRefToTabularSectionRow() {
    // given / when
    var types = typeOf("3.23");
    var attribute = typeOfVariable("Проба_3_23_Реквизит");

    // then: рекомендация предписывает эту запись, отмечая, что 1C:EDT её пока не поддерживает.
    assertThat(names(types))
      .as("рекомендация: параметр получает тип строки табличной части")
      .containsExactly("СтрокаТабличнойЧасти");
    assertThat(names(attribute))
      .as("рекомендация: у строки табличной части читают её реквизит")
      .containsExactly("Строка");
  }

  @Test
  @DisplayName("3.24 Ссылка на реквизит объекта")
  void seeRefToObjectAttribute() {
    // given / when
    var types = typeOf("3.24");

    // then: совпадает с рекомендацией.
    assertThat(names(types)).containsExactly("Строка");
  }

  @Test
  @DisplayName("3.25 Ссылка на реквизит табличной части")
  void seeRefToTabularSectionAttribute() {
    // given / when
    var types = typeOf("3.25");

    // then
    assertThat(names(types))
      .as("рекомендация: параметр получает тип реквизита табличной части")
      .containsExactly("Строка");
  }

  @Test
  @DisplayName("3.26 Ссылка на объект XDTO-пакета")
  void seeRefToXdtoObject() {
    // given / when
    var types = typeOf("3.26");
    var street = typeOfVariable("Проба_3_26_Улица");

    // then: имени типа рекомендация не называет, поведение — да (строка 692: «Адрес.Улица»).
    assertThat(names(types))
      .as("рекомендация: параметр получает тип объекта XDTO-пакета")
      .isNotEmpty();
    assertThat(names(street))
      .as("рекомендация: у объекта XDTO доступны его свойства")
      .containsExactly("Строка");
  }

  @Test
  @DisplayName("3.27 «ФабрикаXDTO.Тип(…)» и «Создать» дают тип XDTO-объекта")
  void xdtoFactoryCreateGivesObjectType() {
    // given / when
    var types = typeOf("3.27");
    var street = typeOfVariable("Проба_3_27_Улица");

    // then: пример рекомендации (строки 689-692) заканчивается «Адрес.Улица = "...";».
    assertThat(names(types))
      .as("рекомендация: созданный фабрикой объект получает тип из указанного пакета")
      .isNotEmpty();
    assertThat(names(street))
      .as("рекомендация: у созданного объекта доступны свойства его типа")
      .containsExactly("Строка");
  }

  @Test
  @DisplayName("3.28 Строчная ссылка на XDTO-тип при вычисляемом пространстве имён")
  void inlineSeeRefToXdtoType() {
    // given / when
    var types = typeOf("3.28");
    var street = typeOfVariable("Проба_3_28_Улица");

    // then: пример рекомендации (строки 705-706) заканчивается «Адрес.Улица = "...";».
    assertThat(names(types))
      .as("рекомендация: строчная ссылка задаёт тип, когда пакет вычисляется в коде")
      .isNotEmpty();
    assertThat(names(street))
      .as("рекомендация: у объекта с типом из строчной ссылки доступны его свойства")
      .containsExactly("Строка");
  }

  @Test
  @DisplayName("3.29 Ссылка на форму по полному имени")
  void seeRefToForm() {
    // given / when
    var types = typeOf("3.29");
    var objectAttribute = typeOfVariable("Проба_3_29_Ссылка");
    var formItem = typeOfVariable("Проба_3_29_Элемент");

    // then: поведение из рекомендации (строки 1605-1606) — «Форма.Объект.Ссылка» и
    // «Форма.Элементы.Артикул».
    assertThat(names(types))
      .as("рекомендация: параметр получает тип формы")
      .containsExactly("ФормаКлиентскогоПриложения");
    assertThat(names(objectAttribute))
      .as("рекомендация: через форму доступен реквизит её основного реквизита")
      .containsExactly("СправочникСсылка.Справочник1");
    assertThat(names(formItem))
      .as("рекомендация: через форму доступен её элемент")
      .containsExactly("ПолеФормы");
  }

  @Test
  @DisplayName("3.30 Ссылка на реквизит формы")
  void seeRefToFormAttribute() {
    // given / when
    var types = typeOf("3.30");
    var attribute = typeOfVariable("Проба_3_30_Реквизит");

    // then: имя типа — из рекомендации (строки 1535, 1588), где основной реквизит формы
    // описан как «ДанныеФормыСтруктура».
    assertThat(names(types))
      .as("рекомендация: параметр получает тип реквизита формы")
      .containsExactly("ДанныеФормыСтруктура");
    assertThat(names(attribute))
      .as("рекомендация: у реквизита формы читают реквизиты объекта")
      .containsExactly("Строка");
  }

  @Test
  @DisplayName("3.31 Ссылка на элемент формы")
  void seeRefToFormItem() {
    // given / when
    var types = typeOf("3.31");
    var currentData = typeOfVariable("Проба_3_31_ТекущиеДанные");

    // then: поведение из рекомендации (строки 749-754) — «Список.ТекущиеДанные.Артикул».
    assertThat(names(types))
      .as("рекомендация: параметр получает тип элемента формы")
      .containsExactly("ТаблицаФормы");
    assertThat(names(currentData))
      .as("рекомендация: через элемент формы читают текущие данные списка")
      .containsExactly("Строка");
  }

  @Test
  @DisplayName("3.32 Текущие данные динамического списка через элемент формы")
  void currentDataOfDynamicListThroughFormItem() {
    // given: параметр объявлен обобщённым типом ТаблицаФормы.
    // when
    var types = typeOf("3.32");
    var attribute = typeOfVariable("Проба_3_32_Реквизит");

    // then: пример рекомендации (строка 753) — «ЗначениеАртикула = Список.ТекущиеДанные.Артикул».
    assertThat(names(types))
      .as("рекомендация: «ТекущиеДанные» элемента формы дают строку его данных")
      .isNotEmpty();
    assertThat(names(attribute))
      .as("рекомендация: у текущих данных читают реквизит списка")
      .containsExactly("Строка");
  }

  @Test
  @DisplayName("3.33 Ссылка на строку таблицы формы")
  void seeRefToFormTableRow() {
    // given / when
    var types = typeOf("3.33");
    var attribute = typeOfVariable("Проба_3_33_Реквизит");

    // then: имя типа взято из записи рекомендации (строка 765).
    assertThat(names(types))
      .as("рекомендация: параметр получает тип строки таблицы формы")
      .containsExactly("ДанныеФормыЭлементКоллекции");
    assertThat(names(attribute))
      .as("рекомендация: у строки таблицы формы читают её реквизит")
      .containsExactly("Строка");
  }

  // --- Ссылки на типы объектов кода -------------------------------------------

  @Test
  @DisplayName("3.34 Ссылка на параметр метода модуля менеджера")
  void seeRefToManagerMethodParameter() {
    // given / when
    var types = typeOf("3.34");

    // then
    assertThat(names(types))
      .as("рекомендация: параметр получает объявленный тип параметра метода менеджера")
      .containsExactly("СправочникСсылка.Справочник1");
  }

  @Test
  @DisplayName("3.35 Ссылка на параметр метода модуля объекта")
  void seeRefToObjectModuleMethodParameter() {
    // given / when
    var types = typeOf("3.35");
    var element = typeOfVariable("Проба_3_35_Элемент");

    // then: у метода-цели параметр объявлен как «Массив из Строка».
    assertThat(names(types))
      .as("рекомендация: параметр получает объявленный тип параметра метода модуля объекта")
      .containsExactly("Массив");
    assertThat(names(element))
      .as("рекомендация: вместе с типом приходит объявленный тип его элементов")
      .containsExactly("Строка");
  }

  @Test
  @DisplayName("3.36 Наследование типов параметров по сигнатуре метода другого модуля")
  void signatureInheritanceFromAnotherModule() {
    // given / when
    var types = typeOf("3.36");

    // then: внутри модуля наследование работает
    // (SignatureInheritanceInferenceTest#parameterTypesInheritedFromLinkedMethod).
    assertThat(names(types))
      .as("рекомендация: имена параметров сопоставляются с методом-интерфейсом другого модуля")
      .containsExactly("Строка");
  }

  // --- Типизация локальной переменной в строке ---------------------------------

  @Test
  @DisplayName("3.37 Тип локальной переменной в строке присваивания")
  void inlineTypeOnIndexedAccess() {
    // given: «Данные = Форма[ИмяРеквизита]; // ПолеВвода -» — пример из рекомендации.
    // when
    var types = typeOf("3.37");

    // then: после вызова метода строчный тип применяется (пункт 4.4), здесь должен тоже.
    assertThat(names(types))
      .as("рекомендация: строчный тип задаёт тип переменной")
      .containsExactly("ПолеВвода");
  }

  @Test
  @DisplayName("3.38 Ссылка на тип в строке присваивания")
  void inlineSeeRefOnAssignment() {
    // given / when
    var types = typeOf("3.38");

    // then: в объявлении «Перем» такая же ссылка уже работает (пункт 4.8).
    assertThat(names(types))
      .as("рекомендация: строчная ссылка разворачивается в тип конструктора")
      .containsExactly("Структура");
    assertThat(fieldNames(types))
      .as("рекомендация: вместе с типом приходят его поля")
      .containsExactlyInAnyOrder("Ссылка", "Количество");
  }

  private TypeSet typeOf(String item) {
    return SpecProbes.typeOf(typeService, document(), item);
  }

  private TypeSet typeOfVariable(String variable) {
    return SpecProbes.typeOfVariable(typeService, document(), variable);
  }

  private static DocumentContext document() {
    return TestUtils.getDocumentContextFromFile(SpecProbes.SECTION_3);
  }
}
