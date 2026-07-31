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
 * Каждый тест подаёт код, нарушающий свой пункт, и требует диагностику с ожидаемым ключом.
 * Диагностик с такими ключами ещё нет, поэтому тесты красные, а их появление тесты чинит.
 * Сами ключи — предмет договорённости при реализации.
 */
@CleanupContextBeforeClassAndAfterClass
class SpecStrictTypingControlTest extends AbstractServerContextAwareTest {

  /** Нет типа у создаваемого объекта или переменной (пункты 2.18, 2.24). */
  private static final String MISSING_TYPE = "MissingType";

  /** Обращение к члену значения без типа (пункт 2.19). */
  private static final String UNTYPED_MEMBER_ACCESS = "UntypedMemberAccess";

  /** Не определён тип возвращаемого значения (пункт 2.20). */
  private static final String MISSING_RETURN_TYPE = "MissingReturnType";

  /** Смена типа переменной или свойства (пункты 2.21, 4.12). */
  private static final String TYPE_CHANGE = "TypeChange";

  /** Тип аргумента не совместим с типом параметра (пункты 2.22, 2.30). */
  private static final String INCOMPATIBLE_ARGUMENT_TYPE = "IncompatibleArgumentType";

  /** Объявленный тип расходится с анализом потока данных (пункт 2.23). */
  private static final String DECLARED_TYPE_MISMATCH = "DeclaredTypeMismatch";

  /** Инициализация переменной внутри цикла или условия (пункт 4.3). */
  private static final String INITIALIZATION_IN_LOOP = "VariableInitializationInLoop";

  /** Разнотипные значения коллекции (пункт 4.18). */
  private static final String DISSIMILAR_COLLECTION_VALUES = "DissimilarCollectionValues";

  /** Нет описания типов у параметра или возвращаемого значения (пункты 4.51, 4.52). */
  private static final String MISSING_TYPE_DESCRIPTION = "MissingTypeDescription";

  /** Обращение к элементу коллекции строковым индексом (пункт 4.55). */
  private static final String STRING_INDEX_ACCESS = "StringIndexAccess";

  /** Параметр формы с типом «Произвольный» (пункт 4.61). */
  private static final String ARBITRARY_FORM_PARAMETER = "ArbitraryFormParameter";

  /** Обращение к параметру формы, которого нет в её описании (пункт 4.62). */
  private static final String UNDECLARED_FORM_PARAMETER = "UndeclaredFormParameter";

  /** Составной тип из непохожих типов (пункт 4.68). */
  private static final String DISSIMILAR_COMPOSITE_TYPE = "DissimilarCompositeType";

  /** Все ключи контроля типов — для пункта 2.31, где ошибок быть не должно. */
  private static final Set<String> TYPE_CONTROL_KEYS = Set.of(
    MISSING_TYPE,
    UNTYPED_MEMBER_ACCESS,
    MISSING_RETURN_TYPE,
    TYPE_CHANGE,
    INCOMPATIBLE_ARGUMENT_TYPE,
    DECLARED_TYPE_MISMATCH,
    INITIALIZATION_IN_LOOP,
    DISSIMILAR_COLLECTION_VALUES,
    MISSING_TYPE_DESCRIPTION,
    STRING_INDEX_ACCESS,
    ARBITRARY_FORM_PARAMETER,
    UNDECLARED_FORM_PARAMETER,
    DISSIMILAR_COMPOSITE_TYPE
  );

  @BeforeEach
  void prepareMetadataServerContext() {
    initServerContextOnce(Path.of(TestUtils.PATH_TO_METADATA));
  }

  @Test
  @DisplayName("2.18 Контроль наличия типа в месте создания объекта или переменной")
  void controlOfMissingTypeAtCreation() {
    // given: переменной присвоен результат вызова, тип которого неизвестен.
    var codes = codesFor("""
      Процедура Тест()
        Значение = НеизвестнаяФункция();
      КонецПроцедуры
      """);

    // then
    assertThat(codes)
      .as("рекомендация: отсутствие типа в месте создания переменной диагностируется")
      .contains(MISSING_TYPE);
  }

  @Test
  @DisplayName("2.19 Контроль наличия типа в месте использования")
  void controlOfMissingTypeAtUsage() {
    // given: обращение к полю значения без типа.
    var codes = codesFor("""
      Процедура Тест()
        Значение = НеизвестнаяФункция();
        Значение.НесуществующееПоле = 1;
      КонецПроцедуры
      """);

    // then
    assertThat(codes)
      .as("рекомендация: обращение к члену нетипизированного значения диагностируется")
      .contains(UNTYPED_MEMBER_ACCESS);
  }

  @Test
  @DisplayName("2.20 Контроль типов возвращаемых значений")
  void controlOfReturnTypes() {
    // given: функция без объявленного типа возврата, у результата читают несуществующее поле.
    var codes = codesFor("""
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
      .contains(MISSING_RETURN_TYPE);
  }

  @Test
  @DisplayName("2.21 Запрет смены типа переменной и свойства объекта")
  void controlOfTypeChange() {
    // given: переменной сначала присвоено число, потом строка.
    var codes = codesFor("""
      Процедура Тест()
        Значение = 1;
        Значение = "строка";
      КонецПроцедуры
      """);

    // then
    assertThat(codes)
      .as("рекомендация: смена типа переменной запрещена и диагностируется")
      .contains(TYPE_CHANGE);
  }

  @Test
  @DisplayName("2.22 Пересечение типов при передаче объекта в параметр метода")
  void controlOfArgumentTypeIntersection() {
    // given: в параметр, объявленный документом, передают справочник.
    var codes = codesFor("""
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
      .contains(INCOMPATIBLE_ARGUMENT_TYPE);
  }

  @Test
  @DisplayName("2.23 Контроль декларированных типов против типов из анализа потока данных")
  void controlOfDeclaredVersusComputedFieldTypes() {
    // given: в поле, объявленное ссылкой, кладут число.
    var codes = codesFor("""
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
      .contains(DECLARED_TYPE_MISMATCH);
  }

  @Test
  @DisplayName("2.24 Аннотация «//@strict-types» включает строгую типизацию модуля")
  void strictTypesAnnotationEnablesControl() {
    // given: модуль с аннотацией и нетипизированной переменной.
    var codes = codesFor("""
      //@strict-types

      Процедура Тест()
        Перем Значение;
        Значение = НеизвестнаяФункция();
      КонецПроцедуры
      """);

    // then
    assertThat(codes)
      .as("рекомендация: аннотация включает контроль типов для всего модуля")
      .contains(MISSING_TYPE);
  }

  @Test
  @DisplayName("2.30 Несоответствие типа аргумента объявленному типу параметра")
  void controlOfArgumentTypeMismatch() {
    // given: вызов функции с типом, отличным от объявленного в её описании.
    var codes = codesFor("""
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
      .contains(INCOMPATIBLE_ARGUMENT_TYPE);
  }

  @Test
  @DisplayName("2.31 При двух расчётных типах параметра ошибок внутри тела нет")
  void noErrorsInsideBodyWithTwoComputedParameterTypes() {
    // given: метод вызывают со справочником и с документом, внутри обращаются к общему реквизиту.
    var codes = codesFor("""
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
      .doesNotContainAnyElementsOf(TYPE_CONTROL_KEYS);
  }

  @Test
  @DisplayName("4.3 Инициализация внутри цикла с использованием вне его")
  void controlOfInitializationInsideLoop() {
    // given: переменная получает значение только внутри цикла, читают её после.
    var codes = codesFor("""
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
      .contains(INITIALIZATION_IN_LOOP);
  }

  @Test
  @DisplayName("4.12 Смена типа значения ключа структуры не допускается")
  void controlOfStructureKeyTypeChange() {
    // given: ключ создан числом, затем в него кладут строку.
    var codes = codesFor("""
      Процедура Тест()
        Параметры = Новый Структура("Ключ", 0);
        Параметры.Ключ = "строка";
      КонецПроцедуры
      """);

    // then
    assertThat(codes)
      .as("рекомендация: смена типа значения ключа структуры диагностируется")
      .contains(TYPE_CHANGE);
  }

  @Test
  @DisplayName("4.18 Значения массива разных типов — повод для рефакторинга")
  void controlOfMixedArrayValues() {
    // given: в один массив кладут число, строку и ссылку.
    var codes = codesFor("""
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
      .contains(DISSIMILAR_COLLECTION_VALUES);
  }

  @Test
  @DisplayName("4.51 Все параметры и возвращаемые значения экспортных методов имеют типы")
  void controlOfExportedMethodTypes() {
    // given: экспортный метод без описания типов параметра.
    var codes = codesFor("""
      Процедура ЭкспортнаяБезТипов(Параметр) Экспорт
        Возврат;
      КонецПроцедуры
      """);

    // then
    assertThat(codes)
      .as("рекомендация: отсутствие типов в описании экспортного метода диагностируется")
      .contains(MISSING_TYPE_DESCRIPTION);
  }

  @Test
  @DisplayName("4.52 Не экспортный метод без прямого вызова требует описания типов")
  void controlOfDetachedMethodTypes() {
    // given: метод без вызовов в модуле и без описания типов параметра.
    var codes = codesFor("""
      Процедура БезПрямогоВызова(Параметр)
        Параметр.Реквизит1 = "";
      КонецПроцедуры
      """);

    // then
    assertThat(codes)
      .as("рекомендация: при разрыве прямого контекста типы параметров обязательны")
      .contains(MISSING_TYPE_DESCRIPTION);
  }

  @Test
  @DisplayName("4.55 Обращение к элементу коллекции по имени члена, а не строковым индексом")
  void controlOfStringIndexAccess() {
    // given: обращение к элементу формы строковым индексом.
    var codes = codesFor("""
      &НаКлиенте
      Процедура Тест()
        Элементы["Номер"].Видимость = Ложь;
      КонецПроцедуры
      """);

    // then
    assertThat(codes)
      .as("рекомендация: строковый индекс вместо имени члена диагностируется")
      .contains(STRING_INDEX_ACCESS);
  }

  @Test
  @DisplayName("4.61 Параметр формы с типом «Произвольный» для передачи структуры запрещён")
  void controlOfArbitraryFormParameter() {
    // given: параметр объявлен «Произвольный», из него читают поле.
    var codes = codesFor("""
      // Параметры:
      //  Параметры - Произвольный - параметры формы
      Процедура ПриСозданииНаСервере(Параметры)
        Значение = Параметры.Ссылка;
      КонецПроцедуры
      """);

    // then
    assertThat(codes)
      .as("рекомендация: параметр «Произвольный» для передачи структуры запрещён")
      .contains(ARBITRARY_FORM_PARAMETER);
  }

  @Test
  @DisplayName("4.62 Все параметры формы описаны на вкладке «Параметры»")
  void controlOfUndeclaredFormParameters() {
    // given: чтение параметра формы, которого нет в описании формы.
    var codes = codesFor("""
      &НаСервере
      Процедура ПриСозданииНаСервере(Отказ, СтандартнаяОбработка)
        Значение = Параметры.НеописанныйПараметр;
      КонецПроцедуры
      """);

    // then
    assertThat(codes)
      .as("рекомендация: обращение к неописанному параметру формы диагностируется")
      .contains(UNDECLARED_FORM_PARAMETER);
  }

  @Test
  @DisplayName("4.68 Составной тип из непохожих типов — повод для рефакторинга")
  void controlOfDissimilarCompositeTypes() {
    // given: параметр объявлен строкой и ссылочным объектом одновременно.
    var codes = codesFor("""
      // Параметры:
      //  Значение - Строка, СправочникОбъект.Справочник1 - непохожие типы в одном наборе
      Процедура Тест(Значение)
        Использование = Значение;
      КонецПроцедуры
      """);

    // then
    assertThat(codes)
      .as("рекомендация: составной тип из непохожих типов диагностируется")
      .contains(DISSIMILAR_COMPOSITE_TYPE);
  }

  /**
   * Коды диагностик, сработавших на примере.
   *
   * @param content текст модуля.
   * @return коды сработавших диагностик.
   */
  private List<String> codesFor(String content) {
    var documentContext = TestUtils.getDocumentContext(content, context);
    return documentContext.getDiagnostics().stream()
      .map(diagnostic -> diagnostic.getCode() == null ? "?" : diagnostic.getCode().getLeft())
      .distinct()
      .sorted()
      .toList();
  }
}
