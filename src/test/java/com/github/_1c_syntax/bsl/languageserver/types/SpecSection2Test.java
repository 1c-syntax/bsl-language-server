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

import static com.github._1c_syntax.bsl.languageserver.types.SpecProbes.names;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Сверка с методической рекомендацией «Типизация кода», раздел «Как это работает»:
 * расчётная типизация, расширение и сокращение типов.
 * <p>
 * Один тест — один пункт рекомендации, номер пункта указан в {@code @DisplayName}.
 * Тест выражает требование рекомендации, а не текущее поведение: пока пункт не закрыт,
 * тест красный, и закрытие пункта его чинит. Исключение — места, где мы точнее
 * рекомендации: там проверяется наше поведение.
 */
@CleanupContextBeforeClassAndAfterClass
class SpecSection2Test extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @BeforeEach
  void prepareMetadataServerContext() {
    initServerContextOnce(Path.of(TestUtils.PATH_TO_METADATA));
  }

  @Test
  @DisplayName("2.1 Тип переменной до переприсваивания — тип первого присваивания")
  void variableTypeBeforeReassignment() {
    // given / when
    var types = typeOf("2.1");

    // then: совпадает с рекомендацией.
    assertThat(names(types)).containsExactly("Число");
  }

  @Test
  @DisplayName("2.2 Тип переменной после переприсваивания — тип второго присваивания")
  void variableTypeAfterReassignment() {
    // given / when
    var types = typeOf("2.2");

    // then: совпадает с рекомендацией.
    assertThat(names(types)).containsExactly("Булево");
  }

  @Test
  @DisplayName("2.3 Свойство, объявленное в конструкторе структуры без значения, — Неопределено")
  void propertyDeclaredWithoutValueIsUndefined() {
    // given / when
    var types = typeOf("2.3");

    // then: совпадает с рекомендацией.
    assertThat(names(types)).containsExactly("Неопределено");
  }

  @Test
  @DisplayName("2.4 Присваивание значения такому свойству его тип не меняет")
  void assignmentDoesNotRetypeUninitializedProperty() {
    // given / when
    var types = typeOf("2.4");

    // then: совпадает с рекомендацией — остаётся Неопределено, а не Число.
    assertThat(names(types)).containsExactly("Неопределено");
  }

  @Test
  @DisplayName("2.5 «Вставить(\"Имя\", Ложь)» инициализирует свойство типом значения")
  void insertInitializesPropertyWithValueType() {
    // given / when
    var types = typeOf("2.5");

    // then: совпадает с рекомендацией.
    assertThat(names(types)).containsExactly("Булево");
  }

  @Test
  @DisplayName("2.6 Присваивание другого значения инициализированному свойству тип не меняет")
  void assignmentDoesNotRetypeInitializedProperty() {
    // given / when
    var types = typeOf("2.6");

    // then: совпадает с рекомендацией — остаётся Булево, а не Число.
    assertThat(names(types)).containsExactly("Булево");
  }

  @Test
  @DisplayName("2.7 Проверка ТипЗнч над свойством его тип не сужает")
  void typeCheckDoesNotNarrowProperty() {
    // given / when
    var types = typeOf("2.7");

    // then: совпадает с рекомендацией — внутри проверки на Число свойство остаётся Булево.
    assertThat(names(types)).containsExactly("Булево");
  }

  @Test
  @DisplayName("2.8 Переменная, объявленная через «Перем» без комментария, содержит Неопределено")
  void declaredVariableWithoutCommentIsUndefined() {
    // given: значение переменной присваивается только внутри цикла.
    // when
    var types = typeOf("2.8");

    // then
    assertThat(names(types))
      .as("рекомендация: такая переменная всегда Неопределено")
      .containsExactly("Неопределено");
  }

  @Test
  @DisplayName("2.11 Типизирующий комментарий дополняет расчётные типы, а не заменяет их")
  void declaredParameterTypeIsNotUnionedWithCallSiteTypes() {
    // given: у параметра объявлен тип СправочникОбъект.Справочник1, а локальный вызов
    // передаёт число.
    // when
    var types = typeOf("2.11");

    // then
    assertThat(names(types))
      .as("рекомендация: комментарий дополняет расчётные типы, поэтому виден и тип места вызова")
      .containsExactlyInAnyOrder("СправочникОбъект.Справочник1", "Число");
  }

  @Test
  @DisplayName("2.26 Расчёт объединения типов возврата по телу функции")
  void returnTypeIsNotComputedFromFunctionBody() {
    // given: функция без документирующего комментария с «Возврат Новый Массив» и «Возврат 10».
    // when
    var types = typeOf("2.26");

    // then
    assertThat(names(types))
      .as("рекомендация: EDT сама рассчитывает два типа по телу функции")
      .containsExactlyInAnyOrder("Массив", "Число");
  }

  @Test
  @DisplayName("2.27 Комментарий расширяет расчётные типы функции третьим типом")
  void inlineCommentDoesNotExtendComputedReturnTypes() {
    // given: тот же вызов с комментарием «// Булево -».
    // when
    var types = typeOf("2.27");

    // then
    assertThat(names(types))
      .as("рекомендация: комментарий добавляет третий тип к двум расчётным")
      .containsExactlyInAnyOrder("Массив", "Число", "Булево");
  }

  @Test
  @DisplayName("2.28 Сужение типа проверкой ТипЗнч внутри условия")
  void typeCheckNarrowsVariableInsideCondition() {
    // given / when
    var types = typeOf("2.28");

    // then: совпадает с рекомендацией.
    assertThat(names(types)).containsExactly("СправочникОбъект.Справочник1");
  }

  @Test
  @DisplayName("2.29 Объявленный в комментарии тип параметра действует внутри метода")
  void declaredParameterTypeAppliesInsideMethod() {
    // given: параметр объявлен как ДокументОбъект.Документ1, а локальный вызов передаёт
    // СправочникОбъект.Справочник1.
    // when
    var types = typeOf("2.29");

    // then: совпадает с рекомендацией — внутри метода действует объявленный тип.
    assertThat(names(types)).containsExactly("ДокументОбъект.Документ1");
  }

  @Test
  @DisplayName("2.32 Разбор ветками ТипЗнч даёт в каждой ветке свой тип")
  void eachTypeCheckBranchNarrowsToItsOwnType() {
    // given / when
    var documentBranch = typeOfVariable("Проба_2_32_1");
    var catalogBranch = typeOfVariable("Проба_2_32_2");

    // then: совпадает с рекомендацией.
    assertThat(names(documentBranch)).containsExactly("ДокументОбъект.Документ1");
    assertThat(names(catalogBranch)).containsExactly("СправочникОбъект.Справочник1");
  }

  private TypeSet typeOf(String item) {
    return SpecProbes.typeOf(typeService, document(), item);
  }

  private TypeSet typeOfVariable(String variable) {
    return SpecProbes.typeOfVariable(typeService, document(), variable);
  }

  private static DocumentContext document() {
    return TestUtils.getDocumentContextFromFile(SpecProbes.SECTION_2);
  }
}
