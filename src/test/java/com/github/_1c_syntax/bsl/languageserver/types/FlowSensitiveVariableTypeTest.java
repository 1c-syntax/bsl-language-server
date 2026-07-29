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
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.references.model.OccurrenceType;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тип переменной зависит от места использования: присваивание перекрывает прежний тип,
 * а в точках слияния путей типы объединяются.
 */
@CleanupContextBeforeClassAndAfterClass
class FlowSensitiveVariableTypeTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @Test
  void typeBeforeReassignmentIsTheFirstOne() {
    // given / when
    var types = at("Первое = Значение", "Первое = ".length());

    // then
    assertThat(qnames(types)).containsExactly("Число");
  }

  @Test
  void typeAfterReassignmentIsTheSecondOne() {
    // given / when
    var types = at("Второе = Значение", "Второе = ".length());

    // then
    assertThat(qnames(types)).containsExactly("Булево");
  }

  @Test
  void typesOfBranchesAreJoinedAfterCondition() {
    // given / when
    var types = at("ПослеВетвления = Данные", "ПослеВетвления = ".length());

    // then
    assertThat(qnames(types)).containsExactlyInAnyOrder("Число", "Строка");
  }

  @Test
  void assignmentInsideBranchIsNotVisibleBeforeIt() {
    // given / when
    var types = at("ДоВетвления = Счётчик", "ДоВетвления = ".length());

    // then
    assertThat(qnames(types)).containsExactly("Число");
  }

  @Test
  void typeAfterOptionalBranchJoinsBothPaths() {
    // given / when
    var types = at("ПослеВетвления = Счётчик", "ПослеВетвления = ".length());

    // then
    assertThat(qnames(types)).containsExactlyInAnyOrder("Число", "Строка");
  }

  @Test
  void structureFieldIsNotVisibleBeforeItsInsert() {
    // given / when
    var types = at("ПослеПервойВставки = Данные", "ПослеПервойВставки = ".length());

    // then: вторая вставка идёт ниже по коду и здесь ещё не случилась.
    assertThat(fieldNames(types)).containsExactly("Первое");
  }

  @Test
  void structureFieldsAccumulateInStatementOrder() {
    // given / when
    var types = at("ПослеВторойВставки = Данные", "ПослеВторойВставки = ".length());

    // then
    assertThat(fieldNames(types)).containsExactlyInAnyOrder("Первое", "Второе");
  }

  @Test
  void valueTableColumnIsNotVisibleBeforeItsAdd() {
    // given / when
    var types = at("ПослеПервойКолонки = Таблица", "ПослеПервойКолонки = ".length());

    // then
    assertThat(columnNames(types)).containsExactly("Номенклатура");
  }

  @Test
  void valueTableColumnsAccumulateInStatementOrder() {
    // given / when
    var types = at("ПослеВторойКолонки = Таблица", "ПослеВторойКолонки = ".length());

    // then
    assertThat(columnNames(types)).containsExactlyInAnyOrder("Номенклатура", "Количество");
  }

  @Test
  void fieldInsertedInBranchIsVisibleAfterJoin() {
    // given: поле добавлено только в одной ветке — после слияния путей оно возможно.
    // when
    var types = at("ПослеВетвления = Набор", "ПослеВетвления = ".length());

    // then
    assertThat(fieldNames(types)).containsExactly("ИзВетки");
  }

  @Test
  void danglingDotDoesNotOfferFieldsInsertedBelow() {
    // given: автодополнение на `ЧтоТо.` выше по коду, чем вставки полей.
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/FlowAccumulationDanglingDot.bsl");
    var content = documentContext.getContent();
    var markerStart = content.indexOf("ЧтоТо.\n");
    assertThat(markerStart).as("висячая точка найдена в фикстуре").isNotNegative();
    var line = content.substring(0, markerStart).split("\n").length - 1;
    var lineStart = content.lastIndexOf('\n', markerStart - 1) + 1;
    var position = new Position(line, markerStart - lineStart + "ЧтоТо.".length());

    // when
    var types = typeService.receiverTypesAt(documentContext, position);

    // then: вставки ниже по коду здесь ещё не случились.
    assertThat(fieldNames(types)).isEmpty();
  }

  @Test
  void typeAtReferenceIsTakenAtThatOccurrence() {
    // given: тем же путём тип берёт hover — через ссылку, без узла дерева разбора.
    var documentContext = doc();
    var symbolTree = documentContext.getSymbolTree();
    var method = symbolTree.getMethodSymbol("ПоляСтруктурыНакапливаютсяПоПорядку").orElseThrow();
    var variable = symbolTree.getVariableSymbol("Данные", method).orElseThrow();
    var declaration = new Reference(
      variable, variable, documentContext.getUri(), variable.getSelectionRange(), OccurrenceType.DEFINITION);

    // when
    var types = typeService.typesAt(declaration);

    // then: в точке объявления вставок ещё не было.
    assertThat(fieldNames(types)).isEmpty();
  }

  @Test
  void loopBindingGivesElementType() {
    // given: связывание в «Для Каждого» — присваивание, которого нет отдельным оператором.
    // when
    var types = at("ВнутриЦикла = Элемент", "ВнутриЦикла = ".length());

    // then
    assertThat(qnames(types)).containsExactly("Строка");
  }

  @Test
  void labelMergesTypesFromAllJumps() {
    // given: к метке ведут два пути — переход из ветки и обычное продолжение.
    // when
    var types = at("ПослеМетки = Значение", "ПослеМетки = ".length());

    // then
    assertThat(qnames(types)).containsExactlyInAnyOrder("Число", "Строка");
  }

  @Test
  void selfAssignmentReadsPreviousType() {
    // given / when
    var types = at("Накопитель = Накопитель + \"хвост\"", "Накопитель = ".length());

    // then
    assertThat(qnames(types)).containsExactly("Строка");
  }

  private TypeSet at(String marker, int offsetInMarker) {
    var documentContext = doc();
    var content = documentContext.getContent();
    var markerStart = content.indexOf(marker);
    assertThat(markerStart).as("маркер '%s' найден в фикстуре", marker).isNotNegative();
    var targetOffset = markerStart + offsetInMarker;
    var lineStart = content.lastIndexOf('\n', targetOffset) + 1;
    var line = content.substring(0, targetOffset).split("\n").length - 1;
    var charInLine = targetOffset - lineStart;
    return typeService.expressionTypesAt(documentContext, new Position(line, charInLine + 1));
  }

  private static DocumentContext doc() {
    return TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/FlowSensitiveVariableTypes.bsl");
  }

  private static List<String> qnames(TypeSet types) {
    return types.refs().stream().map(ref -> ref.qualifiedName()).toList();
  }

  /** Имена полей «открытого» объекта данных — того единственного типа, что в наборе. */
  private static List<String> fieldNames(TypeSet types) {
    assertThat(types.refs()).hasSize(1);
    return List.copyOf(types.getLocalFields(types.refs().iterator().next()).keySet());
  }

  /** Имена колонок таблицы значений — поля типа строки, привязанного элементом. */
  private static List<String> columnNames(TypeSet types) {
    assertThat(types.refs()).hasSize(1);
    var tableRef = types.refs().iterator().next();
    var rowTypes = types.getElementTypes(tableRef);
    assertThat(rowTypes.refs()).hasSize(1);
    return List.copyOf(rowTypes.getLocalFields(rowTypes.refs().iterator().next()).keySet());
  }
}
