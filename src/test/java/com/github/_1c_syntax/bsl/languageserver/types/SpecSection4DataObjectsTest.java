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

import static com.github._1c_syntax.bsl.languageserver.types.SpecProbes.elementNames;
import static com.github._1c_syntax.bsl.languageserver.types.SpecProbes.fieldNames;
import static com.github._1c_syntax.bsl.languageserver.types.SpecProbes.names;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Сверка с методической рекомендацией «Типизация кода», раздел «Лучшие практики»:
 * переменные, структуры, массивы, таблицы значений, соответствия, списки значений
 * и функции-конструкторы (пункты 4.1–4.42).
 * <p>
 * Один тест — один пункт рекомендации, номер пункта указан в {@code @DisplayName}.
 * Тест выражает требование рекомендации, а не текущее поведение: пока пункт не закрыт,
 * тест красный, и закрытие пункта его чинит.
 */
@CleanupContextBeforeClassAndAfterClass
class SpecSection4DataObjectsTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @BeforeEach
  void prepareMetadataServerContext() {
    initServerContextOnce(Path.of(TestUtils.PATH_TO_METADATA));
  }

  // --- Инициализация переменных ------------------------------------------------

  @Test
  @DisplayName("4.1 «Перем» типа не даёт, инициализация значением даёт")
  void peremVersusInitializationByValue() {
    // given / when
    var afterPerem = typeOfVariable("Проба_4_1_1");
    var afterInitialization = typeOfVariable("Проба_4_1_2");

    // then: совпадает с рекомендацией — правильный вариант типизирован, неправильный нет.
    assertThat(afterPerem.isEmpty()).isTrue();
    assertThat(names(afterInitialization)).containsExactly("СправочникСсылка.Справочник1");
  }

  @Test
  @DisplayName("4.2 Инициализация до условия вместо «Перем» и присваивания в ветке")
  void initializationBeforeCondition() {
    // given / when
    var types = typeOf("4.2");

    // then: совпадает с рекомендацией.
    assertThat(names(types)).containsExactly("Строка");
  }

  @Test
  @DisplayName("4.4 Уточнение типа переменной строчным комментарием после вызова")
  void inlineTypeCommentAfterCall() {
    // given / when
    var types = typeOf("4.4");

    // then: совпадает с рекомендацией.
    assertThat(names(types)).containsExactly("СправочникОбъект.Справочник1");
  }

  @Test
  @DisplayName("4.5 Строчная ссылка на функцию-конструктор при получении из контейнера")
  void inlineSeeRefAfterCall() {
    // given / when
    var types = typeOf("4.5");

    // then
    assertThat(names(types))
      .as("рекомендация: строчная ссылка разворачивается в тип функции-конструктора")
      .containsExactly("Структура");
    assertThat(fieldNames(types))
      .as("рекомендация: вместе с типом приходят его поля")
      .containsExactlyInAnyOrder("Ссылка", "Количество");
  }

  @Test
  @DisplayName("4.6 «Новый ОписаниеТипов(…)» и «ПривестиЗначение(Неопределено)»")
  void typeDescriptionAdjustValue() {
    // given / when
    var types = typeOf("4.6");

    // then: совпадает с рекомендацией.
    assertThat(names(types)).containsExactly("СправочникСсылка.Справочник1");
  }

  @Test
  @DisplayName("4.7 Переменная модуля: тип в комментарии объявления")
  void moduleVariableTypeComment() {
    // given / when
    var types = typeOf("4.7");

    // then: совпадает с рекомендацией.
    assertThat(names(types)).containsExactly("Число");
  }

  @Test
  @DisplayName("4.8 Переменная модуля: ссылка в комментарии объявления")
  void moduleVariableSeeRefComment() {
    // given: «Перем ОбъектДанныхМодуля; // см. ОбщегоНазначения.НовыеСвойстваПодписи».
    // when
    var types = typeOf("4.8");

    // then: совпадает с рекомендацией — приходит структура вместе с полями.
    assertThat(names(types)).containsExactly("Структура");
    assertThat(fieldNames(types)).contains("Подпись", "Комментарий", "ДатаПодписи");
  }

  @Test
  @DisplayName("4.9 Переменная модуля инициализируется в теле модуля")
  void moduleVariableInitializedInModuleBody() {
    // given / when
    var types = typeOf("4.9");

    // then: совпадает с рекомендацией.
    assertThat(names(types)).containsExactly("СправочникСсылка.Справочник1");
  }

  // --- Ключи структуры ---------------------------------------------------------

  @Test
  @DisplayName("4.10 Ключ структуры инициализирован пустым значением нужного типа в конструкторе")
  void structureKeyFromConstructorValue() {
    // given / when
    var types = typeOf("4.10");

    // then: совпадает с рекомендацией.
    assertThat(names(types)).containsExactly("СправочникСсылка.Справочник1");
  }

  @Test
  @DisplayName("4.11 Ключ структуры добавлен через «Вставить(\"Имя\", Значение)»")
  void structureKeyFromInsert() {
    // given / when
    var types = typeOf("4.11");

    // then: совпадает с рекомендацией.
    assertThat(names(types)).containsExactly("СправочникСсылка.Справочник1");
  }

  @Test
  @DisplayName("4.13 Ключ, вставленный в другой области видимости, в текущей не виден")
  void keyInsertedInAnotherScopeIsNotVisible() {
    // given: структуру заполняет вызываемая процедура.
    // when
    var types = typeOf("4.13");

    // then: совпадает с рекомендацией — такой код для анализатора непрозрачен.
    assertThat(names(types)).isEmpty();
  }

  @Test
  @DisplayName("4.14 «Если Структура.Свойство(\"Имя\")» и строчный тип при чтении поля")
  void inlineTypeWhenReadingUnknownField() {
    // given / when
    var types = typeOf("4.14");

    // then: совпадает с рекомендацией и даже опережает её — она отмечает этот приём как
    // «будет реализовано в будущих версиях 1C:EDT», у нас он работает.
    assertThat(names(types)).containsExactly("СправочникСсылка.Справочник1");
  }

  @Test
  @DisplayName("4.15 Переменная-приёмник метода «Свойство» инициализируется заранее")
  void outParameterInitializedBeforehand() {
    // given / when
    var types = typeOf("4.15");

    // then: совпадает с рекомендацией.
    assertThat(names(types)).containsExactly("Строка");
  }

  // --- Массивы -----------------------------------------------------------------

  @Test
  @DisplayName("4.16 Тип элементов массива задан строчным комментарием")
  void arrayElementTypeFromInlineComment() {
    // given: «СписокСсылок = Новый Массив; // Массив из СправочникСсылка.Справочник1 -».
    // when
    var types = typeOf("4.16");

    // then
    assertThat(names(types))
      .as("рекомендация: элемент обхода получает объявленный в комментарии тип")
      .containsExactly("СправочникСсылка.Справочник1");
  }

  @Test
  @DisplayName("4.17 Тип элементов массива задан возвращаемым значением функции-конструктора")
  void arrayElementTypeFromConstructorFunction() {
    // given / when
    var types = typeOf("4.17");

    // then: совпадает с рекомендацией.
    assertThat(names(types)).containsExactly("СправочникСсылка.Справочник1");
  }

  @Test
  @DisplayName("4.19 Тип элементов массива указан в описании параметра")
  void arrayElementTypeInParameterDescription() {
    // given / when
    var types = typeOf("4.19");

    // then: совпадает с рекомендацией.
    assertThat(names(types)).containsExactly("СправочникСсылка.Справочник1");
  }

  // --- Таблицы значений --------------------------------------------------------

  @Test
  @DisplayName("4.20 Колонки таблицы описаны в возвращаемом значении функции")
  void tableColumnsFromReturnValue() {
    // given: колонки объявлены в описании функции-конструктора, строку берут обходом.
    // when
    var types = typeOf("4.20");

    // then
    assertThat(names(types))
      .as("рекомендация: колонка строки имеет объявленный тип")
      .containsExactly("СправочникСсылка.Справочник1");
  }

  @Test
  @DisplayName("4.21 Параметр экспортного метода — ссылка на функцию-конструктор таблицы")
  void tableParameterBySeeRefToConstructor() {
    // given / when
    var types = typeOf("4.21");

    // then
    assertThat(names(types))
      .as("рекомендация: колонка строки таблицы, переданной по ссылке на конструктор, типизирована")
      .containsExactly("СправочникСсылка.Справочник1");
  }

  @Test
  @DisplayName("4.22 «ТаблицаЗначений - произвольная таблица:» с перечислением колонок")
  void arbitraryTableWithDeclaredColumns() {
    // given / when
    var types = typeOf("4.22");

    // then
    assertThat(names(types))
      .as("рекомендация: перечисленные колонки произвольной таблицы типизированы")
      .containsExactly("СправочникСсылка.Справочник1");
  }

  @Test
  @DisplayName("4.23 Обращение к колонке по имени в переменной")
  void columnAccessByNameInVariable() {
    // given: «СтрокаТаблицы[ИмяКолонки]» — случай, который рекомендация типизировать не требует
    // (обработка таблицы с неопределённым набором колонок).
    // when
    var types = typeOf("4.23");

    // then: ТОЧНЕЕ РЕКОМЕНДАЦИИ — она типа здесь не ждёт, а мы отдаём объединение типов
    // объявленных колонок, как и для структуры с вычисляемым ключом
    // (KeyValueIndexAccessInferenceTest#structureIndexAccessWithDynamicKeyUnionsValueTypes).
    assertThat(names(types))
      .containsExactlyInAnyOrder("СправочникСсылка.Справочник1", "Число");
  }

  @Test
  @DisplayName("4.24 Строка таблицы: «СтрокаТаблицыЗначений:» с перечислением колонок")
  void tableRowWithDeclaredColumns() {
    // given / when
    var types = typeOf("4.24");

    // then: совпадает с рекомендацией.
    assertThat(names(types)).containsExactly("СправочникСсылка.Справочник1");
  }

  @Test
  @DisplayName("4.25 Строка таблицы: «СтрокаТаблицыЗначений: См. КонструкторТаблицы»")
  void tableRowBySeeRefToConstructor() {
    // given / when
    var types = typeOf("4.25");

    // then
    assertThat(names(types))
      .as("рекомендация: колонки строки берутся из конструктора по ссылке после двоеточия")
      .containsExactly("СправочникСсылка.Справочник1");
  }

  // --- Соответствия и списки значений ------------------------------------------

  @Test
  @DisplayName("4.26 «Соответствие из КлючИЗначение:» с полями «Ключ» и «Значение»")
  void mapKeyAndValueTypes() {
    // given / when
    var key = typeOfVariable("Проба_4_26_1");
    var value = typeOfVariable("Проба_4_26_2");

    // then: совпадает с рекомендацией.
    assertThat(names(key)).containsExactly("Строка");
    assertThat(names(value)).containsExactly("СправочникСсылка.Справочник1");
  }

  @Test
  @DisplayName("4.27 Типы ключа и значения соответствия в строчном комментарии не задаются")
  void mapContentIsNotTypedByInlineComment() {
    // given / when
    var types = typeOf("4.27");

    // then: совпадает с рекомендацией — так это не работает.
    assertThat(elementNames(types)).containsExactly("КлючИЗначение");
  }

  @Test
  @DisplayName("4.28 Объект из контейнера типизируется ссылкой на функцию-конструктор")
  void objectFromContainerBySeeRef() {
    // given / when
    var types = typeOf("4.28");

    // then
    assertThat(names(types))
      .as("рекомендация: значение из контейнера получает тип функции-конструктора")
      .containsExactly("Соответствие");
  }

  @Test
  @DisplayName("4.29 «СписокЗначений из <Тип>»: «Добавить()» даёт элемент списка")
  void valueListAddGivesItem() {
    // given / when
    var types = typeOf("4.29");

    // then: совпадает с рекомендацией.
    assertThat(names(types)).containsExactly("ЭлементСпискаЗначений");
  }

  @Test
  @DisplayName("4.30 Свойство «Значение» элемента списка типизировано объявленным типом")
  void valueListItemValueType() {
    // given / when
    var types = typeOf("4.30");

    // then
    assertThat(names(types))
      .as("рекомендация: «Значение» элемента имеет объявленный тип списка")
      .containsExactly("СправочникСсылка.Справочник1");
  }

  @Test
  @DisplayName("4.31 Тип значения списка в строчном комментарии не задаётся")
  void valueListContentIsNotTypedByInlineComment() {
    // given / when
    var types = typeOf("4.31");

    // then: совпадает с рекомендацией — так это не работает.
    assertThat(elementNames(types)).containsExactly("ЭлементСпискаЗначений");
  }

  @Test
  @DisplayName("4.32 Функция-получатель возвращает заполненный список объявленного типа")
  void filledValueListFromGetter() {
    // given: обход списка, полученного функцией-получателем.
    // when
    var types = typeOf("4.32");

    // then
    assertThat(names(types))
      .as("рекомендация: элементы полученного списка имеют объявленный тип значения")
      .containsExactly("СправочникСсылка.Справочник1");
  }

  // --- Функции-конструкторы ----------------------------------------------------

  @Test
  @DisplayName("4.33 Функция-конструктор сложного объекта данных описывает его тип")
  void constructorFunctionDescribesItsType() {
    // given / when
    var types = typeOf("4.33");

    // then: совпадает с рекомендацией.
    assertThat(names(types)).containsExactly("Структура");
    assertThat(fieldNames(types)).containsExactlyInAnyOrder("Ссылка", "Количество");
  }

  @Test
  @DisplayName("4.34 «Состав полей см. в функции …» в описании типа не типизирует")
  void fieldsSeeInFunctionTextDoesNotType() {
    // given / when
    var types = typeOf("4.34");
    var columnOnRow = typeOfVariable("Проба_4_34_Колонка");

    // then: совпадает с рекомендацией — тип есть, а колонок от текста описания нет
    // ни у таблицы, ни у её строки.
    assertThat(names(types)).containsExactly("ТаблицаЗначений");
    assertThat(fieldNames(types)).isEmpty();
    assertThat(names(columnOnRow)).isEmpty();
  }

  @Test
  @DisplayName("4.35 Ссылка в описании метода вместо секции параметров не типизирует")
  void seeRefInMethodDescriptionDoesNotType() {
    // given / when
    var types = typeOf("4.35");

    // then: совпадает с рекомендацией.
    assertThat(names(types)).isEmpty();
  }

  @Test
  @DisplayName("4.36 Голая ссылка строкой описания не типизирует")
  void bareSeeRefLineDoesNotType() {
    // given / when
    var types = typeOf("4.36");

    // then: совпадает с рекомендацией.
    assertThat(names(types)).isEmpty();
  }

  @Test
  @DisplayName("4.37 Ссылка с точкой в конце не типизирует")
  void seeRefWithTrailingDotDoesNotType() {
    // given / when
    var types = typeOf("4.37");

    // then: совпадает с рекомендацией.
    assertThat(names(types)).isEmpty();
  }

  @Test
  @DisplayName("4.38 Ссылка с описанием после неё не типизирует")
  void seeRefWithDescriptionAfterIt() {
    // given / when
    var types = typeOf("4.38");

    // then
    assertThat(names(types))
      .as("рекомендация относит запись «см. Метод - описание» к неправильным")
      .isEmpty();
  }

  @Test
  @DisplayName("4.39 Ссылка без базового типа и без описания типизирует параметр")
  void seeRefWithoutBaseTypeAndDescription() {
    // given / when
    var types = typeOf("4.39");
    var columnOnRow = typeOfVariable("Проба_4_39_Колонка");
    var columnOnTable = typeOfVariable("Проба_4_39_ЧленТаблицы");

    // then: параметр получает тип таблицы из конструктора, объявленные колонки видны у строки.
    assertThat(names(types)).containsExactly("ТаблицаЗначений");
    assertThat(names(columnOnRow))
      .as("рекомендация: код работает с колонками строки таблицы, полученной по ссылке")
      .containsExactly("СправочникСсылка.Справочник1");
    assertThat(names(columnOnTable))
      .as("у самой таблицы значений свойства с именем колонки нет")
      .isEmpty();
  }

  @Test
  @DisplayName("4.40 Функция-получатель описывает возвращаемый объект со всеми полями")
  void getterFunctionDescribesAllFields() {
    // given: «Возвращаемое значение: ДеревоЗначений:» с колонками, обход через «Строки».
    // when
    var types = typeOf("4.40");

    // then
    assertThat(names(types))
      .as("рекомендация: колонка строки дерева имеет объявленный тип")
      .containsExactly("Строка");
  }

  // --- Описание программных интерфейсов ----------------------------------------

  @Test
  @DisplayName("4.41 «ТабличнаяЧасть Из СтрокаТабличнойЧасти:» с полями строки")
  void tabularSectionInterfaceThroughElementType() {
    // given / when
    var types = typeOf("4.41");

    // then: совпадает с рекомендацией.
    assertThat(names(types)).containsExactly("СправочникСсылка.Справочник1");
  }

  @Test
  @DisplayName("4.42 Расширение полей на самой коллекции не работает")
  void fieldExtensionOnCollectionItselfDoesNotWork() {
    // given: «ТабличнаяЧасть, ДанныеФормыКоллекция:» с полями строки — запись,
    // которую рекомендация помечает как неправильную.
    // when
    var types = typeOf("4.42");

    // then: совпадает с рекомендацией.
    assertThat(names(types)).isEmpty();
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
