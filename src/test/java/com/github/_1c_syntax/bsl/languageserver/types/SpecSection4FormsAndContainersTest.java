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
import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;

/**
 * Сверка с методической рекомендацией «Типизация кода», раздел «Лучшие практики»:
 * работа с формами, контейнерами, выборками запроса и макетами (пункты 4.43–4.67).
 * <p>
 * Один тест — один пункт рекомендации, номер пункта указан в имени теста, а само требование —
 * в javadoc теста: подраздел рекомендации, её формулировка и приведённая там запись. Нумерация
 * пунктов — наша, в тексте рекомендации её нет.
 * <p>
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

  /**
   * «Описание используемых реквизитов и элементов форм для общего кода по работе с формами»:
   * «Допускается описание части формы, общие для нескольких форм или реализация некого интерфейса
   * для общего механизма конфигурации».
   * <pre>
   * // Параметры:
   * // Форма - ФормаКлиентскогоПриложения:
   * // * Объект - ДанныеФормыСтруктура, СправочникОбъект, ДокументОбъект - основной реквизит формы
   * Процедура ПриСозданииНаСервере(Форма)
   *     Ссылка = Форма.Объект.Ссылка;
   * </pre>
   */
  @Test
  @DisplayName("4.43 Описание части формы: «ФормаКлиентскогоПриложения:» с «* Объект»")
  void formMainAttributeFromDocComment() {
    // given / when
    var types = typeOf("4.43");

    // then: совпадает с рекомендацией.
    assertThat(names(types)).containsExactly("СправочникОбъект.Справочник1");
  }

  /**
   * «Описание используемых реквизитов и элементов форм для общего кода по работе с формами»:
   * в том же описании перечисляются элементы формы, на которые рассчитывает код.
   * <pre>
   * // * Элементы - ВсеЭлементыФормы:
   * //  ** Товары - ТаблицаФормы - элемент таблицы товаров
   * Процедура ПриСозданииНаСервере(Форма)
   *     ТекущиеДанные = Форма.Элементы.Товары.ТекущиеДанные;
   * </pre>
   */
  @Test
  @DisplayName("4.44 Описание элементов формы: «* Элементы - ВсеЭлементыФормы:» с «** Товары»")
  void formItemsFromDocComment() {
    // given / when
    var types = typeOf("4.44");

    // then: совпадает с рекомендацией.
    assertThat(names(types)).containsExactly("ТаблицаФормы");
  }

  /**
   * «Описание используемых реквизитов и элементов форм…»: «Если используется конкретная форма,
   * следует в документирующих комментариях модулей (общих, менеджеров) указывать полную ссылку
   * на форму».
   * <pre>
   * // Параметры:
   * // Форма - см. Справочник.Номенклатура.Форма.ФормаЭлемента
   * Процедура ПриСозданииНаСервере(Форма)
   *     Ссылка = Форма.Объект.Ссылка;
   *     Форма.Элементы.Артикул.Видимость = Истина;
   * </pre>
   */
  @Test
  @DisplayName("4.45 Ссылка на конкретную форму в описании параметра общего модуля")
  void seeRefToConcreteForm() {
    // given: «Форма - см. Справочник.Справочник1.Форма.ФормаЭлемента», обращение к «Объект».
    // when
    var types = typeOf("4.45");
    var reference = typeOfVariable("Проба_4_45_Ссылка");
    var formItem = typeOfVariable("Проба_4_45_Элемент");

    // then: имя типа расходится с буквой рекомендации. Она называет тип обобщённо —
    // «ДанныеФормыСтруктура», а система типов отдаёт тип основного реквизита этой конкретной
    // формы («ДанныеФормыСтруктура.СправочникОбъект.Справочник1»): он строго точнее, потому
    // что знает её реквизиты. Поэтому имя сверяется по вхождению, а не точным совпадением.
    assertThat(names(types))
      .as("рекомендация: через ссылку на форму доступен её основной реквизит")
      .singleElement(as(STRING))
      .contains("ДанныеФормыСтруктура");
    assertThat(names(reference))
      .as("рекомендация: «Ссылка = Форма.Объект.Ссылка»")
      .containsExactly("СправочникСсылка.Справочник1");
    assertThat(names(formItem))
      .as("рекомендация: «Форма.Элементы.Артикул.Видимость = Истина»")
      .containsExactly("ПолеФормы");
  }

  /**
   * «Описание используемых реквизитов и элементов форм…»: «Для функций общих модулей,
   * рассчитывающих на дополнительные свойства и методы расширения типа
   * {@code ФормаКлиентскогоПриложения} следует использовать соответствующие типы расширений
   * управляемой формы: {@code РасширениеУправляемойФормыДляОбъектов},
   * {@code РасширениеУправляемойФормыДляДокумента}… и так далее».
   * <pre>
   * // Параметры:
   * // Форма - РасширениеУправляемойФормыДляОбъектов -
   * Процедура ОбработкаЗакрытия(Форма) Экспорт
   *     Форма.Записать();
   * </pre>
   */
  @Test
  @DisplayName("4.46 Тип расширения управляемой формы")
  void managedFormExtensionType() {
    // given / when
    var types = typeOf("4.46");

    // then: совпадает с рекомендацией.
    assertThat(names(types)).containsExactly("РасширениеУправляемойФормыДляОбъектов");
  }

  // --- Получение и открытие формы ----------------------------------------------

  /**
   * «Получение и открытие формы»: «При использовании методов {@code ПолучитьФорму()} и
   * {@code ОткрытьФорму()} с присвоением возвращаемого значения формы после открытия — следует
   * указывать строковый литерал с полным именем формы первым параметром».
   * <pre>
   * Форма = ПолучитьФорму("Справочник.Номенклатура.Форма.ФормаЭлемента");
   * </pre>
   */
  @Test
  @DisplayName("4.47 «ПолучитьФорму(\"полное.имя\")» даёт конкретный тип формы")
  void getFormByFullName() {
    // given / when
    var types = typeOf("4.47");
    var reference = typeOfVariable("Проба_4_47_Ссылка");

    // then: рекомендация требует, чтобы был доступен весь контекст этой формы, то есть
    // конкретный тип формы. Обобщённое имя «ФормаКлиентскогоПриложения» — это голова имени
    // конкретного типа, поэтому оно сверяется по вхождению.
    assertThat(names(types)).singleElement(as(STRING)).contains(FORM_TYPE);
    assertThat(names(reference))
      .as("рекомендация: у полученной формы доступен её основной реквизит")
      .containsExactly("СправочникСсылка.Справочник1");
  }

  /**
   * «Получение и открытие формы», второй правильный вариант — форма запрашивается у менеджера
   * объекта по имени формы.
   * <pre>
   * Форма = Справочники.Номенклатура.ПолучитьФорму("ФормаЭлемента");
   * </pre>
   */
  @Test
  @DisplayName("4.48 «Справочники.X.ПолучитьФорму(\"ФормаЭлемента\")» даёт конкретный тип формы")
  void getFormThroughManager() {
    // given / when
    var types = typeOf("4.48");
    var reference = typeOfVariable("Проба_4_48_Ссылка");

    // then: как и в 4.47, конкретный тип формы сверяется по вхождению обобщённого имени.
    assertThat(names(types)).singleElement(as(STRING)).contains(FORM_TYPE);
    assertThat(names(reference))
      .as("рекомендация: у полученной формы доступен её основной реквизит")
      .containsExactly("СправочникСсылка.Справочник1");
  }

  /**
   * «Получение и открытие формы»: «Не следует выносить строковый литерал в отдельную переменную
   * т.к. в этом случае переменная форма будет содержать общий тип
   * {@code ФормаКлиентскогоПриложения} а не конкретный тип формы… и весь контекст формы не будет
   * доступен».
   * <pre>
   * // НЕПРАВИЛЬНО
   * ИмяФормы = "Справочник.Номенклатура.Форма.ФормаЭлемента";
   * Форма = ПолучитьФорму(ИмяФормы);
   * </pre>
   */
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

  /**
   * «Получение и открытие формы»: «В 1C:EDT на текущий момент поддерживается полная типизация
   * только для полных имен форм, ссылки на основные формы для объекта метаданного
   * {@code ФормаОбъекта}, {@code ФормаСписка} и т.д. возвращают общий тип
   * {@code ФормаКлиентскогоПриложения}».
   */
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

  /**
   * «Использование временного хранилища и других контейнеров»: «Поместить во временное хранилище
   * и после получить из него — можно все что угодно, поэтому необходимо выделять функцию
   * возвращающую тип помещенный во временное хранилище использовать на нее ссылку при получении».
   * <pre>
   * Данные = ПолучитьИзВременногоХранилища(Адрес); // см. НовыйОбъектДанных
   * </pre>
   */
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

  /**
   * «Использование временного хранилища и других контейнеров»: «Аналогичный подход следует
   * использовать при помещении пользовательского объекта внутрь другого объекта-контейнера,
   * например {@code ДополнительныеПараметры} у объектов, или {@code Параметры} формы,
   * пользовательские параметры элементов {@code СКД}, {@code ДополнительныеПараметры}
   * у обработчиков оповещения и так далее».
   */
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

  /**
   * «Использование строковых литералов в качестве имен»: чтение по строковому индексу допустимо,
   * когда наличие элемента проверено, и тип полученного значения указан в строке.
   * <pre>
   * Если Параметры.Свойство("Ссылка") Тогда
   *     Ссылка = Параметры["Ссылка"]; // СправочникСсылка -
   * </pre>
   */
  @Test
  @DisplayName("4.58 «Параметры.Свойство(\"Ссылка\")» и чтение по индексу со строчным типом")
  void readByStringIndexWithInlineType() {
    // given / when
    var types = typeOf("4.58");

    // then: совпадает с рекомендацией.
    assertThat(names(types)).containsExactly("СправочникСсылка.Справочник1");
  }

  // --- Выборка запроса, передача объекта, макеты --------------------------------

  /**
   * «Выборка и выгрузка из результата запроса», первый способ: «Статическое описание всех полей
   * выборки (полей таблицы значений выгрузки) в возвращаемом значении функции-получателе данных».
   * <pre>
   * // Возвращаемое значение:
   * // ВыборкаИзРезультатаЗапроса:
   * //  * Номенклатура - СправочникСсылка.Номенклатура
   * Функция ОстаткиДляОбработки()
   * </pre>
   */
  @Test
  @DisplayName("4.63 Поля выборки описаны в возвращаемом значении функции-получателя")
  void querySelectionFieldsFromDocComment() {
    // given / when
    var types = typeOf("4.63");

    // then: совпадает с рекомендацией.
    assertThat(names(types)).containsExactly("СправочникСсылка.Справочник1");
  }

  /**
   * «Выборка и выгрузка из результата запроса», второй способ: «Динамическая типизация полей
   * выборки на основе текста запроса… при условии что текст запроса и выборка (результат запроса)
   * находятся в одной процедуре, текст запроса не содержит ошибок… выборка/выгрузка из результата
   * запроса не передается в другой модуль для обработки». Рекомендация помечает способ как
   * возможный «в будущих версиях 1C:EDT».
   */
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

  /**
   * «Передача пользовательского объекта данных в другой модуль»: «Объекты данных созданные
   * пользовательским кодом и использованные вне текущего модуля — должны быть описаны функцией
   * возвращающей значение».
   */
  @Test
  @DisplayName("4.65 Объект данных, уходящий в другой модуль, описан возвращаемым значением")
  void dataObjectPassedToAnotherModule() {
    // given / when
    var types = typeOf("4.65");

    // then: совпадает с рекомендацией — прямой межмодульный вызов переносит поля.
    assertThat(names(types)).containsExactly("Структура");
    assertThat(fieldNames(types)).contains("Подпись", "Комментарий", "ДатаПодписи");
  }

  /**
   * «Описание типов макетов СКД и Табличного документа»: «В случае получения макета через
   * универсальную процедуру — следует указывать в строке тип макета».
   * <pre>
   * Макет = УправлениеПечатью.МакетПечатнойФормы("Документ.РеализацияТоваровУслуг.ПечатнаяФорма"); //см. Документ.РеализацияТоваровУслуг.Макет.ПечатнаяФорма
   * </pre>
   */
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

  /**
   * «Ограничение на создание пользовательских объектов-копий (Структуры, ТЗ) из платформенных
   * объектов»: «В исключительных случаях… можно сделать функцию-конструктор описывающую смешанный
   * тип {@code Структура + СправочникОбъект.Номенклатура} чтобы не описывать весь тип. При этом
   * можно добавить в описание дополнительные колонки».
   */
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
