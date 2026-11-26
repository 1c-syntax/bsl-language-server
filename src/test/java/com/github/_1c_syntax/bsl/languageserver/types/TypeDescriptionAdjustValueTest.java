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
 * Приведение значения описанием типов: {@code ОписаниеЧисла.ПривестиЗначение(Х)} даёт
 * значение ровно тех типов, что описаны, хотя платформа объявляет возврат произвольным.
 */
@CleanupContextBeforeClassAndAfterClass
class TypeDescriptionAdjustValueTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @Test
  void literalDescriptionGivesItsType() {
    // given: описание собрано литеральным конструктором.
    // when
    var types = at("Приведённое = ТипЧисло.ПривестиЗначение", "Приведённое = ".length());

    // then
    assertThat(qnames(types)).containsExactly("Число");
  }

  @Test
  void severalDescribedTypesAreAllReturned() {
    // given / when
    var types = at("ПриведённоеИзДвух = ТипыЗначения.ПривестиЗначение", "ПриведённоеИзДвух = ".length());

    // then
    assertThat(qnames(types)).containsExactlyInAnyOrder("Число", "Строка");
  }

  @Test
  void descriptionWithoutLiteralGivesNothingCertain() {
    // given: состав описания собран в переменной — статически он неизвестен, и выдумывать
    // тип нельзя.
    // when
    var types = at(
      "ПриведённоеБезЛитерала = ОписаниеИзПеременной.ПривестиЗначение",
      "ПриведённоеБезЛитерала = ".length());

    // then
    assertThat(qnames(types)).doesNotContain("Число", "Строка", "Дата");
  }

  @Test
  void adjustedValueContinuesTheChain() {
    // given: без уточнения цепочка обрывалась бы на «произвольном».
    // when
    var types = at("ГодПриведённой = ОписаниеДаты.ПривестиЗначение", "ГодПриведённой = ".length());

    // then
    assertThat(qnames(types)).containsExactly("Дата");
  }

  private TypeSet at(String marker, int offsetInMarker) {
    var documentContext = doc();
    var content = documentContext.getContent();
    var markerStart = content.indexOf(marker);
    assertThat(markerStart).as("маркер '%s' найден в фикстуре", marker).isNotNegative();
    var targetOffset = markerStart + offsetInMarker;
    var lineStart = content.lastIndexOf('\n', targetOffset) + 1;
    var line = content.substring(0, targetOffset).split("\n").length - 1;
    return typeService.expressionTypesAt(documentContext, new Position(line, targetOffset - lineStart + 1));
  }

  private static DocumentContext doc() {
    return TestUtils.getDocumentContextFromFile("./src/test/resources/types/TypeDescriptionAdjustValue.bsl");
  }

  private static List<String> qnames(TypeSet types) {
    return types.refs().stream().map(ref -> ref.qualifiedName()).toList();
  }
}
