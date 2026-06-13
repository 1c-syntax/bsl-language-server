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
package com.github._1c_syntax.bsl.languageserver.inlayhints;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintKind;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@CleanupContextBeforeClassAndAfterEachTestMethod
class VariableTypeInlayHintSupplierTest extends AbstractServerContextAwareTest {

  private static final String FILE_PATH =
    "./src/test/resources/inlayhints/VariableTypeInlayHintSupplier.bsl";

  @Autowired
  private VariableTypeInlayHintSupplier supplier;

  @Test
  void testInferredTypeHintIsProducedForAssignment() {

    // given
    var documentContext = TestUtils.getDocumentContextFromFile(FILE_PATH);
    var firstMethod = documentContext.getSymbolTree().getMethods().getFirst();

    var textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());
    var params = new InlayHintParams(textDocumentIdentifier, firstMethod.getRange());

    // when
    List<InlayHint> inlayHints = supplier.getInlayHints(documentContext, params);

    // then
    assertThat(inlayHints)
      .hasSize(1)
      .first()
      .satisfies(inlayHint -> {
        assertThat(inlayHint.getLabel()).isEqualTo(Either.forLeft(": Массив"));
        assertThat(inlayHint.getKind()).isEqualTo(InlayHintKind.Type);
        assertThat(inlayHint.getPaddingRight()).isTrue();
        // позиция — сразу после имени переменной «Контрагент» (строка 1, длина имени)
        assertThat(inlayHint.getPosition().getLine()).isEqualTo(1);
        assertThat(inlayHint.getPosition().getCharacter()).isEqualTo("\tКонтрагент".length());
      });
  }

  @Test
  void testNoHintForLiteralAssignment() {

    // given
    var documentContext = TestUtils.getDocumentContextFromFile(FILE_PATH);
    var firstMethod = documentContext.getSymbolTree().getMethods().getFirst();

    var textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());
    var params = new InlayHintParams(textDocumentIdentifier, firstMethod.getRange());

    // when
    List<InlayHint> inlayHints = supplier.getInlayHints(documentContext, params);

    // then
    // присваивание «Простая = 1» тривиально (тип очевиден из литерала) — хинта нет
    assertThat(inlayHints)
      .noneSatisfy(inlayHint ->
        assertThat(inlayHint.getLabel().getLeft()).contains("Число"));
  }

  @Test
  void testNoHintForMemberTargetAssignment() {

    // given
    // цель присваивания — обращение к члену объекта, а не простая переменная
    var documentContext = TestUtils.getDocumentContext("""
      Процедура Тест()
      	Объект = Новый Структура();
      	Объект.Поле = Новый Массив();
      КонецПроцедуры
      """);
    var method = documentContext.getSymbolTree().getMethods().getFirst();

    var textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());
    var params = new InlayHintParams(textDocumentIdentifier, method.getRange());

    // when
    List<InlayHint> inlayHints = supplier.getInlayHints(documentContext, params);

    // then
    // присваивание «Объект.Поле = ...» — не простая переменная-цель, хинта для неё нет
    assertThat(inlayHints)
      .noneSatisfy(inlayHint ->
        assertThat(inlayHint.getPosition().getLine()).isEqualTo(2));
  }

  @Test
  void testNoHintWhenTypeIsNotInferred() {

    // given
    // тип правой части не выводится (вызов неизвестного метода)
    var documentContext = TestUtils.getDocumentContext("""
      Процедура Тест()
      	Неизвестная = НеизвестныйМетод();
      КонецПроцедуры
      """);
    var method = documentContext.getSymbolTree().getMethods().getFirst();

    var textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());
    var params = new InlayHintParams(textDocumentIdentifier, method.getRange());

    // when
    List<InlayHint> inlayHints = supplier.getInlayHints(documentContext, params);

    // then
    // тип не выведен (или Произвольный) — хинт не строится
    assertThat(inlayHints).isEmpty();
  }

  @Test
  void testNoHintWhenAssignmentIsOutsideRequestedRange() {

    // given
    var documentContext = TestUtils.getDocumentContextFromFile(FILE_PATH);

    var textDocumentIdentifier = TestUtils.getTextDocumentIdentifier(documentContext.getUri());
    // запрашиваемый диапазон не покрывает присваивания (нулевая первая строка)
    var emptyRange = new Range(new Position(0, 0), new Position(0, 0));
    var params = new InlayHintParams(textDocumentIdentifier, emptyRange);

    // when
    List<InlayHint> inlayHints = supplier.getInlayHints(documentContext, params);

    // then
    assertThat(inlayHints).isEmpty();
  }
}
