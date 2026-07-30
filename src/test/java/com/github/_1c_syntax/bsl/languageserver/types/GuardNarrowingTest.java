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
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Сужение типа переменной охраняющим условием: проверка типа через {@code ТипЗнч}
 * и проверка на {@code Неопределено}.
 */
@CleanupContextBeforeClassAndAfterClass
class GuardNarrowingTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @Test
  void typeCheckNarrowsTrueBranchToCheckedType() {
    // given / when
    var types = at("ВнутриПроверки = Значение", "ВнутриПроверки = ".length());

    // then
    assertThat(qnames(types)).containsExactly("Строка");
  }

  @Test
  void typeCheckRemovesCheckedTypeOnElseBranch() {
    // given / when
    var types = at("ВВеткеИначе = Значение", "ВВеткеИначе = ".length());

    // then
    assertThat(qnames(types)).containsExactly("Число");
  }

  @Test
  void receiverOfMemberAccessIsNarrowed() {
    // given: тип ресивера нужен автодополнению и hover на `Значение.`
    var documentContext = doc();
    var position = positionOf(documentContext, "Значение.Длина()", "Значение.".length() + 1);

    // when
    var types = typeService.receiverTypesAt(documentContext, position);

    // then
    assertThat(qnames(types)).containsExactly("Строка");
  }

  @Test
  void receiverBeforeDanglingDotIsNarrowed() {
    // given: автодополнение спрашивает тип ресивера сразу после точки, когда члена ещё нет.
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/GuardNarrowingDanglingDot.bsl");
    var position = positionOf(documentContext, "Значение.\n", "Значение.".length());

    // when
    var types = typeService.receiverTypesAt(documentContext, position);

    // then
    assertThat(qnames(types)).containsExactly("Строка");
  }

  @Test
  void loopVariableIsNarrowedInsideCheck() {
    // given: связывание в «Для Каждого» — присваивание, которого нет отдельным оператором.
    // when
    var types = at("ВнутриПроверкиВЦикле = Элемент", "ВнутриПроверкиВЦикле = ".length());

    // then
    assertThat(qnames(types)).containsExactly("Строка");
  }

  @Test
  void checkNarrowsTheRestOfConjunction() {
    // given: `Значение <> Неопределено И СтрДлина(Значение) > 0` — ко второй проверке первая
    // уже верна, иначе до неё бы не дошли.
    // when
    var types = at("СтрДлина(Значение) > 0 Тогда", "СтрДлина(".length());

    // then
    assertThat(qnames(types)).containsExactly("Строка");
  }

  @Test
  void checkNarrowsTheRestOfDisjunctionWithOppositeSign() {
    // given: `Значение = Неопределено ИЛИ СтрДлина(Значение) = 0` — до второй проверки доходят,
    // когда первая ЛОЖНА, то есть значение заполнено.
    // when
    var types = at("СтрДлина(Значение) = 0 Тогда", "СтрДлина(".length());

    // then
    assertThat(qnames(types)).containsExactly("Строка");
  }

  @Test
  void disjunctionOfChecksNarrowsFalseBranch() {
    // given: `А ИЛИ Б` ложно только когда ложны обе части.
    // when
    var types = at("ВетвьНепустого = Значение", "ВетвьНепустого = ".length());

    // then
    assertThat(qnames(types)).containsExactly("Строка");
  }

  @Test
  void disjunctionOfChecksDoesNotNarrowTrueBranch() {
    // given: из истинности `А ИЛИ Б` не следует ни одна из частей.
    // when
    var types = at("ВетвьПустого = Значение", "ВетвьПустого = ".length());

    // then
    assertThat(qnames(types)).containsExactlyInAnyOrder("Строка", "Неопределено");
  }

  @Test
  void checkDoesNotNarrowAcrossDisjunctionOfOppositeSign() {
    // given: `Значение <> Неопределено ИЛИ СтрДлина(Значение) > 1` — во второй части значение
    // как раз не заполнено, и переносить туда первую проверку как истинную нельзя.
    // when
    var types = at("СтрДлина(Значение) > 1 Тогда", "СтрДлина(".length());

    // then
    assertThat(qnames(types)).containsExactly("Неопределено");
  }

  @Test
  void checkDoesNotNarrowLinksBeforeItself() {
    // given: `СтрДлина(Значение) > 0 И Значение <> Неопределено` — проверка на Неопределено стоит
    // ПОСЛЕ обращения и на момент его вычисления ещё ничего не утверждает.
    // when
    var types = at("СтрДлина(Значение) > 0 И Значение", "СтрДлина(".length());

    // then
    assertThat(qnames(types)).containsExactlyInAnyOrder("Строка", "Неопределено");
  }

  @Test
  void mixedChainDoesNotNarrow() {
    // given: `А И Б ИЛИ В` — из исхода условия не следует исход отдельной проверки.
    // when
    var types = at("ПослеСмешанной = Значение", "ПослеСмешанной = ".length());

    // then
    assertThat(qnames(types)).containsExactlyInAnyOrder("Строка", "Неопределено");
  }

  @Test
  void mixedChainDoesNotNarrowInsideCondition() {
    // given: в `А И Б ИЛИ В` порядок вычисления не даёт связи между звеньями разных видов.
    // when
    var types = at("СтрДлина(Значение) > 0 ИЛИ", "СтрДлина(".length());

    // then
    assertThat(qnames(types)).containsExactlyInAnyOrder("Строка", "Неопределено");
  }

  @Test
  void whileConditionNarrowsInsideLoop() {
    // given: условие цикла «Пока» верно на каждой итерации тела.
    // when
    var types = at("ВнутриПока = Значение", "ВнутриПока = ".length());

    // then
    assertThat(qnames(types)).containsExactly("Строка");
  }

  @Test
  void forLoopCounterIsNumber() {
    // given: счётчик «Для Сч = 1 По Граница» — присваивание без отдельного оператора.
    // when
    var types = at("ВнутриФор = Сч", "ВнутриФор = ".length());

    // then
    assertThat(qnames(types)).containsExactly("Число");
  }

  @Test
  void negatedUndefinedCheckNarrows() {
    // given: «Не Х = Неопределено» — то же, что «Х <> Неопределено».
    // when
    var types = at("ВнутриОтрицания = Значение", "ВнутриОтрицания = ".length());

    // then
    assertThat(qnames(types)).containsExactly("Строка");
  }

  @Test
  void negatedTypeCheckRemovesCheckedType() {
    // given: «Не ТипЗнч(Х) = Тип("Строка")» — то же, что «ТипЗнч(Х) <> Тип("Строка")».
    // when
    var types = at("ВнутриОтрицанияТипа = Значение", "ВнутриОтрицанияТипа = ".length());

    // then
    assertThat(qnames(types)).containsExactly("Число");
  }

  @Test
  void conjunctionOfChecksNarrows() {
    // given / when
    var types = at("ВнутриКонъюнкции = Значение", "ВнутриКонъюнкции = ".length());

    // then
    assertThat(qnames(types)).containsExactly("Строка");
  }

  @Test
  void disjunctionDoesNotNarrow() {
    // given: из «А ИЛИ Б» на истинной ветке не следует ни одна из частей.
    // when
    var types = at("ВнутриДизъюнкции = Значение", "ВнутриДизъюнкции = ".length());

    // then
    assertThat(qnames(types)).containsExactlyInAnyOrder("Строка", "Число");
  }

  @Test
  void undefinedCheckRemovesUndefinedOnTrueBranch() {
    // given / when
    var types = at("ВнутриПроверкиНаНеопределено = Значение", "ВнутриПроверкиНаНеопределено = ".length());

    // then
    assertThat(qnames(types)).containsExactly("Строка");
  }

  @Test
  void guardClauseWithReturnNarrowsCodeAfterIt() {
    // given: ветка с Возврат до кода за условием не доходит.
    // when
    var types = at("ПослеОхраны = Значение", "ПослеОхраны = ".length());

    // then
    assertThat(qnames(types)).containsExactly("Строка");
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
    return TestUtils.getDocumentContextFromFile("./src/test/resources/types/GuardNarrowing.bsl");
  }

  private static List<String> qnames(TypeSet types) {
    return types.refs().stream().map(ref -> ref.qualifiedName()).toList();
  }
}
