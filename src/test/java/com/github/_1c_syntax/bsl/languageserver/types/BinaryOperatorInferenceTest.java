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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Покрывает {@code inferBinary}, {@code inferUnary}, {@code inferTernary}
 * в {@link com.github._1c_syntax.bsl.languageserver.types.inferencer.ExpressionTypeInferencer}.
 */
@CleanupContextBeforeClassAndAfterClass
class BinaryOperatorInferenceTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @Test
  void logicalAndOrResultIsBoolean() {
    // given
    var documentContext = doc();

    // when / then
    assertThat(qnames(infer(documentContext, "ЛогическоеИ = Истина И Ложь",
      "ЛогическоеИ = ".length()))).containsExactly("Булево");
    assertThat(qnames(infer(documentContext, "ЛогическоеИли = Истина Или Ложь",
      "ЛогическоеИли = ".length()))).containsExactly("Булево");
  }

  @Test
  void unaryNotIsBoolean() {
    // given
    var documentContext = doc();

    // when / then
    assertThat(qnames(infer(documentContext, "Отрицание = НЕ Истина",
      "Отрицание = ".length()))).containsExactly("Булево");
  }

  @Test
  void unaryMinusIsNumber() {
    // given
    var documentContext = doc();

    // when / then
    assertThat(qnames(infer(documentContext, "Унарный = -100",
      "Унарный = ".length()))).containsExactly("Число");
  }

  @Test
  void comparisonResultIsBoolean() {
    // given
    var documentContext = doc();

    // when / then
    assertThat(qnames(infer(documentContext, "СравнениеРавно = 100 = 100",
      "СравнениеРавно = ".length()))).containsExactly("Булево");
    assertThat(qnames(infer(documentContext, "СравнениеБольше = 500 > 300",
      "СравнениеБольше = ".length()))).containsExactly("Булево");
    assertThat(qnames(infer(documentContext, "СравнениеНеравно = \"abc\" <> \"def\"",
      "СравнениеНеравно = ".length()))).containsExactly("Булево");
  }

  @Test
  void arithmeticIsNumber() {
    // given
    var documentContext = doc();

    // when / then
    assertThat(qnames(infer(documentContext, "Сумма = 200 + 300",
      "Сумма = ".length()))).containsExactly("Число");
    assertThat(qnames(infer(documentContext, "Разность = 500 - 200",
      "Разность = ".length()))).containsExactly("Число");
    assertThat(qnames(infer(documentContext, "Произведение = 400 * 200",
      "Произведение = ".length()))).containsExactly("Число");
    assertThat(qnames(infer(documentContext, "Деление = 1000 / 200",
      "Деление = ".length()))).containsExactly("Число");
    assertThat(qnames(infer(documentContext, "ОстатокОтДеления = 700 % 300",
      "ОстатокОтДеления = ".length()))).containsExactly("Число");
  }

  @Test
  void leftStringPromotesToString() {
    // given
    var documentContext = doc();

    // when / then
    assertThat(qnames(infer(documentContext, "СтрокаПлюсСтрока = \"abc\" + \"def\"",
      "СтрокаПлюсСтрока = ".length()))).containsExactly("Строка");
    assertThat(qnames(infer(documentContext, "СтрокаПлюсЧисло = \"пять \" + 500",
      "СтрокаПлюсЧисло = ".length()))).containsExactly("Строка");
  }

  @Test
  void leftNumberStaysNumberEvenWithStringRight() {
    // given
    var documentContext = doc();

    // when / then — правый приводится к Числу (в runtime будет ошибка
    // конвертации, но статически — арифметика).
    assertThat(qnames(infer(documentContext, "ЧислоПлюсСтрока = 500 + \" пять\"",
      "ЧислоПлюсСтрока = ".length()))).containsExactly("Число");
  }

  @Test
  void datePlusNumberIsDate() {
    // given
    var documentContext = doc();

    // when / then
    assertThat(qnames(infer(documentContext, "ДатаПлюсСекунды = '20200101' + 86400",
      "ДатаПлюсСекунды = ".length()))).containsExactly("Дата");
  }

  @Test
  void selfConcatenationVariableStaysString() {
    // given — #4205: ПолноеИмя = ПолноеИмя + "..." не должно расширять тип
    // переменной до "Строка, Число".
    var documentContext = doc();

    // when — тип переменной ПолноеИмя в месте использования (union по всем
    // присваиваниям).
    var types = infer(documentContext, "ИтогИмя = ПолноеИмя", "ИтогИмя = ".length());

    // then
    assertThat(qnames(types)).containsExactly("Строка");
  }

  @Test
  void selfConcatenationExpressionIsString() {
    // given — #4205: само выражение `ПолноеИмя + "..."` должно выводиться в
    // Строку, а не терять тип (self-reference резолвится в накопленный тип).
    var documentContext = doc();

    // when — позиция внутри правой части: findExpressionContext поднимается до
    // ближайшего expression-узла, то есть до всего бинарного `X + "..."`.
    var types = infer(documentContext, "ПолноеИмя = ПолноеИмя + \"Загадочное\"",
      "ПолноеИмя = ".length());

    // then
    assertThat(qnames(types)).containsExactly("Строка");
  }

  @Test
  void numericSelfAccumulatorStaysNumber() {
    // given — обратная сторона #4205: числовой аккумулятор не должен внезапно
    // стать Строкой — общая ветка `+` осталась прежней.
    var documentContext = doc();

    // when
    var types = infer(documentContext, "ИтогСчёт = Счётчик", "ИтогСчёт = ".length());

    // then
    assertThat(qnames(types)).containsExactly("Число");
  }

  @Test
  void ternaryProducesUnionOfBranches() {
    // given
    var documentContext = doc();

    // when
    var types = infer(documentContext, "УсловныйЧислоИлиСтрока = ?(Истина, 100, \"строка\")",
      "УсловныйЧислоИлиСтрока = ".length());

    // then
    assertThat(qnames(types)).containsExactlyInAnyOrder("Число", "Строка");
  }

  private DocumentContext doc() {
    return TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/BinaryOperatorInference.bsl");
  }

  private TypeSet infer(DocumentContext documentContext, String marker, int offsetInMarker) {
    var content = documentContext.getContent();
    int markerStart = content.indexOf(marker);
    int targetOffset = markerStart + offsetInMarker;
    int lineStart = content.lastIndexOf('\n', targetOffset) + 1;
    int line = content.substring(0, targetOffset).split("\n").length - 1;
    int charInLine = targetOffset - lineStart;
    return typeService.expressionTypesAt(documentContext, new Position(line, charInLine + 1));
  }

  private static java.util.List<String> qnames(TypeSet ts) {
    return ts.refs().stream()
      .map(r -> r.qualifiedName())
      .toList();
  }
}
