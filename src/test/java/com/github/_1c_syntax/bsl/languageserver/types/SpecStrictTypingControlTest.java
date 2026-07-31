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
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Сверка с методической рекомендацией «Типизация кода»: пункты про контроль типов —
 * «строгая типизация» и запреты из лучших практик.
 * <p>
 * Один тест — один пункт рекомендации, номер пункта указан в {@code @DisplayName}.
 * Каждый тест подаёт код, нарушающий свой пункт, и требует, чтобы нарушение
 * диагностировалось. Пока такой диагностики нет, тест красный; её появление его чинит.
 * <p>
 * Диагностики, срабатывающие на этих примерах по причинам, не связанным с типизацией,
 * из результата исключаются — см. {@link #UNRELATED_TO_TYPING}.
 */
@CleanupContextBeforeClassAndAfterClass
class SpecStrictTypingControlTest extends AbstractServerContextAwareTest {

  /** Диагностики, которые срабатывают на примерах не из-за типов. */
  private static final Set<String> UNRELATED_TO_TYPING = Set.of(
    "UnusedLocalVariable",
    "UnusedLocalMethod",
    "MagicNumber",
    "UsingServiceTag",
    "MissingReturnedValueDescription",
    "MissingParameterDescription",
    "MissingSpaceRight",
    "IfElseIfEndsWithElse"
  );

  @BeforeEach
  void prepareMetadataServerContext() {
    initServerContextOnce(Path.of(TestUtils.PATH_TO_METADATA));
  }

  @Test
  @DisplayName("2.18 Контроль наличия типа в месте создания объекта или переменной")
  void controlOfMissingTypeAtCreation() {
    // given: переменной присвоен результат вызова, тип которого неизвестен.
    var codes = typingCodesFor("""
      Процедура Тест()
        Значение = НеизвестнаяФункция();
      КонецПроцедуры
      """);

    // then
    assertThat(codes)
      .as("рекомендация: отсутствие типа в месте создания переменной диагностируется")
      .isNotEmpty();
  }

  @Test
  @DisplayName("2.19 Контроль наличия типа в месте использования")
  void controlOfMissingTypeAtUsage() {
    // given: обращение к полю значения без типа.
    var codes = typingCodesFor("""
      Процедура Тест()
        Значение = НеизвестнаяФункция();
        Значение.НесуществующееПоле = 1;
      КонецПроцедуры
      """);

    // then
    assertThat(codes)
      .as("рекомендация: обращение к члену нетипизированного значения диагностируется")
      .isNotEmpty();
  }

  @Test
  @DisplayName("2.20 Контроль типов возвращаемых значений")
  void controlOfReturnTypes() {
    // given: функция без объявленного типа возврата, у результата читают несуществующее поле.
    var codes = typingCodesFor("""
      Функция БезОбъявленногоТипаВозврата()
        Возврат 1;
      КонецФункции

      Процедура Тест()
        Значение = БезОбъявленногоТипаВозврата();
        Значение.НесуществующееПоле = 1;
      КонецПроцедуры
      """);

    // then
    assertThat(codes)
      .as("рекомендация: тип возвращаемого значения контролируется")
      .isNotEmpty();
  }

  @Test
  @DisplayName("2.21 Запрет смены типа переменной и свойства объекта")
  void controlOfTypeChange() {
    // given: переменной сначала присвоено число, потом строка.
    var codes = typingCodesFor("""
      Процедура Тест()
        Значение = 1;
        Значение = "строка";
      КонецПроцедуры
      """);

    // then
    assertThat(codes)
      .as("рекомендация: смена типа переменной запрещена и диагностируется")
      .isNotEmpty();
  }

  @Test
  @DisplayName("2.22 Пересечение типов при передаче объекта в параметр метода")
  void controlOfArgumentTypeIntersection() {
    // given: в параметр, объявленный документом, передают справочник.
    var codes = typingCodesFor("""
      // Параметры:
      //  Док - ДокументОбъект.Документ1 -
      Процедура МакетПечати(Док)
        Возврат;
      КонецПроцедуры

      Процедура Тест()
        МакетПечати(Справочники.Справочник1.СоздатьЭлемент());
      КонецПроцедуры
      """);

    // then
    assertThat(codes)
      .as("рекомендация: пустое пересечение типов аргумента и параметра диагностируется")
      .isNotEmpty();
  }

  @Test
  @DisplayName("2.23 Контроль декларированных типов против типов из анализа потока данных")
  void controlOfDeclaredVersusComputedFieldTypes() {
    // given: в поле, объявленное ссылкой, кладут число.
    var codes = typingCodesFor("""
      // Параметры:
      //  Данные - Структура:
      //  * Ссылка - СправочникСсылка.Справочник1
      Процедура Тест(Данные)
        Данные.Вставить("Ссылка", 10);
      КонецПроцедуры
      """);

    // then
    assertThat(codes)
      .as("рекомендация: расхождение декларации и анализа потока данных диагностируется")
      .isNotEmpty();
  }

  @Test
  @DisplayName("2.24 Аннотация «//@strict-types» включает строгую типизацию модуля")
  void strictTypesAnnotationEnablesControl() {
    // given: модуль с аннотацией и нетипизированной переменной.
    var codes = typingCodesFor("""
      //@strict-types

      Процедура Тест()
        Перем Значение;
        Значение = НеизвестнаяФункция();
      КонецПроцедуры
      """);

    // then
    assertThat(codes)
      .as("рекомендация: аннотация включает контроль типов для всего модуля")
      .isNotEmpty();
  }

  @Test
  @DisplayName("2.30 Несоответствие типа аргумента объявленному типу параметра")
  void controlOfArgumentTypeMismatch() {
    // given: вызов функции с типом, отличным от объявленного в её описании.
    var codes = typingCodesFor("""
      // Параметры:
      //  Док - ДокументОбъект.Документ1 -
      Функция МакетПечати(Док)
        Возврат Док;
      КонецФункции

      Процедура Тест()
        Ответ = МакетПечати(Справочники.Справочник1.СоздатьЭлемент());
      КонецПроцедуры
      """);

    // then
    assertThat(codes)
      .as("рекомендация: ошибка несоответствия типов показывается в месте вызова")
      .isNotEmpty();
  }

  @Test
  @DisplayName("2.31 При двух расчётных типах параметра ошибок внутри тела нет")
  void noErrorsInsideBodyWithTwoComputedParameterTypes() {
    // given: метод вызывают со справочником и с документом, внутри обращаются к общему реквизиту.
    var codes = typingCodesFor("""
      Процедура ОбработкаОбъекта(Док)
        Док.Реквизит1 = "";
      КонецПроцедуры

      Процедура Тест()
        ОбработкаОбъекта(Справочники.Справочник1.СоздатьЭлемент());
        ОбработкаОбъекта(Документы.Документ1.СоздатьДокумент());
      КонецПроцедуры
      """);

    // then: совпадает с рекомендацией — реквизит есть у обоих типов, ошибок быть не должно.
    assertThat(codes)
      .as("рекомендация: обращение к члену, который есть у всех расчётных типов, не ошибка")
      .isEmpty();
  }

  @Test
  @DisplayName("4.3 Инициализация внутри цикла с использованием вне его")
  void controlOfInitializationInsideLoop() {
    // given: переменная получает значение только внутри цикла, читают её после.
    var codes = typingCodesFor("""
      Процедура Тест(Коллекция)
        Для Каждого Элемент Из Коллекция Цикл
          Накопитель = Элемент;
        КонецЦикла;
        Использование = Накопитель;
      КонецПроцедуры
      """);

    // then
    assertThat(codes)
      .as("рекомендация: инициализация в цикле с использованием вне него запрещена")
      .isNotEmpty();
  }

  @Test
  @DisplayName("4.12 Смена типа значения ключа структуры не допускается")
  void controlOfStructureKeyTypeChange() {
    // given: ключ создан числом, затем в него кладут строку.
    var codes = typingCodesFor("""
      Процедура Тест()
        Параметры = Новый Структура("Ключ", 0);
        Параметры.Ключ = "строка";
      КонецПроцедуры
      """);

    // then
    assertThat(codes)
      .as("рекомендация: смена типа значения ключа структуры диагностируется")
      .isNotEmpty();
  }

  @Test
  @DisplayName("4.18 Значения массива разных типов — повод для рефакторинга")
  void controlOfMixedArrayValues() {
    // given: в один массив кладут число, строку и ссылку.
    var codes = typingCodesFor("""
      Процедура Тест()
        Массив = Новый Массив;
        Массив.Добавить(1);
        Массив.Добавить("строка");
        Массив.Добавить(Справочники.Справочник1.ПустаяСсылка());
      КонецПроцедуры
      """);

    // then
    assertThat(codes)
      .as("рекомендация: разнотипные значения коллекции диагностируются")
      .isNotEmpty();
  }

  @Test
  @DisplayName("4.51 Все параметры и возвращаемые значения экспортных методов имеют типы")
  void controlOfExportedMethodTypes() {
    // given: экспортный метод без описания типов параметра.
    var codes = typingCodesFor("""
      Процедура ЭкспортнаяБезТипов(Параметр) Экспорт
        Возврат;
      КонецПроцедуры
      """);

    // then
    assertThat(codes)
      .as("рекомендация: отсутствие типов в описании экспортного метода диагностируется")
      .isNotEmpty();
  }

  @Test
  @DisplayName("4.52 Не экспортный метод без прямого вызова требует описания типов")
  void controlOfDetachedMethodTypes() {
    // given: метод без вызовов в модуле и без описания типов параметра.
    var codes = typingCodesFor("""
      Процедура БезПрямогоВызова(Параметр)
        Параметр.Реквизит1 = "";
      КонецПроцедуры
      """);

    // then
    assertThat(codes)
      .as("рекомендация: при разрыве прямого контекста типы параметров обязательны")
      .isNotEmpty();
  }

  @Test
  @DisplayName("4.55 Обращение к элементу коллекции по имени члена, а не строковым индексом")
  void controlOfStringIndexAccess() {
    // given: обращение к элементу формы строковым индексом.
    var codes = typingCodesFor("""
      &НаКлиенте
      Процедура Тест()
        Элементы["Номер"].Видимость = Ложь;
      КонецПроцедуры
      """);

    // then
    assertThat(codes)
      .as("рекомендация: строковый индекс вместо имени члена диагностируется")
      .isNotEmpty();
  }

  @Test
  @DisplayName("4.61 Параметр формы с типом «Произвольный» для передачи структуры запрещён")
  void controlOfArbitraryFormParameter() {
    // given: параметр объявлен «Произвольный», из него читают поле.
    var codes = typingCodesFor("""
      // Параметры:
      //  Параметры - Произвольный - параметры формы
      Процедура ПриСозданииНаСервере(Параметры)
        Значение = Параметры.Ссылка;
      КонецПроцедуры
      """);

    // then
    assertThat(codes)
      .as("рекомендация: параметр «Произвольный» для передачи структуры запрещён")
      .isNotEmpty();
  }

  @Test
  @DisplayName("4.62 Все параметры формы описаны на вкладке «Параметры»")
  void controlOfUndeclaredFormParameters() {
    // given: чтение параметра формы, которого нет в описании формы.
    var codes = typingCodesFor("""
      &НаСервере
      Процедура ПриСозданииНаСервере(Отказ, СтандартнаяОбработка)
        Значение = Параметры.НеописанныйПараметр;
      КонецПроцедуры
      """);

    // then
    assertThat(codes)
      .as("рекомендация: обращение к неописанному параметру формы диагностируется")
      .isNotEmpty();
  }

  @Test
  @DisplayName("4.68 Составной тип из непохожих типов — повод для рефакторинга")
  void controlOfDissimilarCompositeTypes() {
    // given: параметр объявлен строкой и ссылочным объектом одновременно.
    var codes = typingCodesFor("""
      // Параметры:
      //  Значение - Строка, СправочникОбъект.Справочник1 - непохожие типы в одном наборе
      Процедура Тест(Значение)
        Использование = Значение;
      КонецПроцедуры
      """);

    // then
    assertThat(codes)
      .as("рекомендация: составной тип из непохожих типов диагностируется")
      .isNotEmpty();
  }

  /**
   * Коды диагностик, сработавших на примере, за вычетом тех, что к типизации отношения
   * не имеют.
   *
   * @param content текст модуля.
   * @return коды диагностик про типы.
   */
  private List<String> typingCodesFor(String content) {
    var documentContext = TestUtils.getDocumentContext(content, context);
    return documentContext.getDiagnostics().stream()
      .map(diagnostic -> diagnostic.getCode() == null ? "?" : diagnostic.getCode().getLeft())
      .filter(code -> !UNRELATED_TO_TYPING.contains(code))
      .distinct()
      .sorted()
      .toList();
  }
}
