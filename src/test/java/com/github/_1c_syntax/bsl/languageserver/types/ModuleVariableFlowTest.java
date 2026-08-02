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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тип переменной модуля в точке использования: её меняют из разных методов, поэтому
 * расчёт по потоку тела опирается на объединение по всей области видимости и возвращается
 * к нему там, где управление могло уйти в другой метод.
 */
@CleanupContextBeforeClassAndAfterClass
class ModuleVariableFlowTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @Test
  void assignmentInBodyOverridesScopeUnion() {
    // given: `Кэш` присваивают в двух методах — объединение по модулю даёт оба типа.
    // when
    var types = at("ПослеПрисваивания = Кэш", "ПослеПрисваивания = ".length());

    // then: после присваивания в этом же теле остаётся только присвоенный тип.
    assertThat(qnames(types)).containsExactly("Соответствие");
  }

  @Test
  void callResetsTypeToScopeUnion() {
    // given: вызванный метод мог присвоить переменной что угодно — включая состояние
    // от объявления, ведь тело модуля «Кэш» не инициализирует.
    // when
    var types = at("ПослеВызова = Кэш", "ПослеВызова = ".length());

    // then
    assertThat(qnames(types)).containsExactlyInAnyOrder("Неопределено", "Соответствие", "Массив");
  }

  @Test
  void useBeforeFirstAssignmentTakesScopeUnion() {
    // given: до присваивания в этом теле переменная содержит то, что оставил другой метод,
    // либо «Неопределено» от объявления — тело модуля «Кэш» не инициализирует.
    // when
    var types = at("ДоПрисваивания = Кэш", "ДоПрисваивания = ".length());

    // then
    assertThat(qnames(types)).containsExactlyInAnyOrder("Неопределено", "Соответствие", "Массив");
  }

  @Test
  void mutatorFieldsSurviveThroughScopeUnion() {
    // given: поле, вставленное в структуру, видно и после возврата к объединению.
    // when
    var types = at("ПослеВставки = Настройки", "ПослеВставки = ".length());

    // then
    assertThat(qnames(types)).contains("Структура");
    assertThat(types.localFields().values().stream().flatMap(fields -> fields.keySet().stream()).toList())
      .contains("Режим");
  }

  @Test
  void valueBeforeCallIsVisibleToAnotherBody() {
    // given: вызванный метод застаёт переменную такой, какой она была в точке вызова, —
    // значит это часть её значения на входе в другие тела.
    // when
    var types = at("ВЧужомТеле = ПередВызовом", "ВЧужомТеле = ".length());

    // then
    assertThat(qnames(types)).contains("Структура");
  }

  @Test
  void intermediateValueDoesNotLeaveItsBody() {
    // given: `Промежуточный` дважды присваивается подряд без вызовов между присваиваниями,
    // поэтому первый тип другие тела увидеть не могут.
    // when
    var types = at("ВДругомТеле = Промежуточный", "ВДругомТеле = ".length());

    // then: «Массив» своё тело не покинул; «Неопределено» — состояние до первого вызова,
    // тело модуля «Промежуточный» не инициализирует.
    assertThat(qnames(types)).containsExactlyInAnyOrder("Неопределено", "Соответствие");
  }

  @Test
  void initializationInModuleBodyLeavesNoUndefined() {
    // given: «Перем ИнициализированнаяВТелеМодуля;» без комментария, присваивание — в теле
    // модуля, которое отрабатывает раньше любой процедуры.
    // when
    var types = at("ИзТелаМодуля = ИнициализированнаяВТелеМодуля", "ИзТелаМодуля = ".length());

    // then: состояние «до присваивания» из процедуры наблюдать неоткуда.
    assertThat(qnames(types)).containsExactly("Массив");
  }

  @Test
  void initializationUnderConditionInModuleBodyLeavesUndefined() {
    // given: присваивание стоит в теле модуля, но внутри условия — выполнится оно или нет,
    // из кода не следует.
    // when
    var types = at("ИзУсловия = ИнициализированнаяПодУсловием", "ИзУсловия = ".length());

    // then: состояние от объявления остаётся наблюдаемым.
    assertThat(qnames(types)).containsExactlyInAnyOrder("Неопределено", "Массив");
  }

  @Test
  void initializationInBothBranchesLeavesNoUndefined() {
    // given: присваивание есть в обеих ветках условия в теле модуля — какой бы путь ни
    // выбрало выполнение, значение будет присвоено.
    // when
    var types = at("ИзОбеихВеток = ИнициализированнаяВОбеихВетках", "ИзОбеихВеток = ".length());

    // then: состояние от объявления наблюдать неоткуда.
    assertThat(qnames(types)).containsExactlyInAnyOrder("Массив", "Соответствие");
  }

  private TypeSet at(String marker, int offsetInMarker) {
    var documentContext = doc();
    return typeService.expressionTypesAt(documentContext, positionOf(documentContext, marker, offsetInMarker + 1));
  }

  private static Position positionOf(DocumentContext documentContext, String marker, int offsetInMarker) {
    var content = documentContext.getContent();
    var markerStart = content.indexOf(marker);
    assertThat(markerStart).as("маркер '%s' найден в фикстуре", marker).isNotNegative();
    var targetOffset = markerStart + offsetInMarker;
    var lineStart = content.lastIndexOf('\n', targetOffset - 1) + 1;
    var line = content.substring(0, targetOffset).split("\n").length - 1;
    var charInLine = targetOffset - lineStart;
    return new Position(line, charInLine);
  }

  private static DocumentContext doc() {
    return TestUtils.getDocumentContextFromFile("./src/test/resources/types/ModuleVariableFlow.bsl");
  }

  private static List<String> qnames(TypeSet types) {
    return types.refs().stream().map(TypeRef::qualifiedName).toList();
  }
}
