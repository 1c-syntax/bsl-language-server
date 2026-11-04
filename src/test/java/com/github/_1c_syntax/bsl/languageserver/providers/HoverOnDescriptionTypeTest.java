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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверяет hover по имени типа внутри описания метода (BSLDoc) — секции
 * {@code // Параметры:} и {@code // Возвращаемое значение:}.
 */
@SpringBootTest
class HoverOnDescriptionTypeTest {

  private static final String CONTENT = """
    // Параметры:
    //  Данные - ТаблицаЗначений - входные данные
    //
    // Возвращаемое значение:
    //  СтрокаТаблицыЗначений - найденная строка
    //
    Функция НайтиСтроку(Данные) Экспорт
      Возврат Неопределено;
    КонецФункции
    """;

  @Autowired
  private HoverProvider hoverProvider;

  @Test
  void hoverOnInstantiableTypeInParametersSection() {
    // given
    var documentContext = TestUtils.getDocumentContext(CONTENT);
    var params = new HoverParams();
    params.setPosition(new Position(1, 20)); // курсор внутри «ТаблицаЗначений»

    // when
    var optionalHover = hoverProvider.getHover(documentContext, params);

    // then
    assertThat(optionalHover).isPresent();
    var hover = optionalHover.get();
    assertThat(hover.getContents().getRight().getValue())
      .contains("ТаблицаЗначений")
      .doesNotContain("Новый");
    assertThat(hover.getRange()).isEqualTo(Ranges.create(1, 13, 28));
  }

  @Test
  void hoverOnNonInstantiableTypeInReturnsSection() {
    // given
    var documentContext = TestUtils.getDocumentContext(CONTENT);
    var params = new HoverParams();
    params.setPosition(new Position(4, 10)); // курсор внутри «СтрокаТаблицыЗначений»

    // when
    var optionalHover = hoverProvider.getHover(documentContext, params);

    // then
    assertThat(optionalHover).isPresent();
    var hover = optionalHover.get();
    assertThat(hover.getContents().getRight().getValue())
      .contains("СтрокаТаблицыЗначений")
      .doesNotContain("Новый");
    assertThat(hover.getRange()).isEqualTo(Ranges.create(4, 4, 25));
  }

  @Test
  void noHoverOnUnknownTypeNameInDescription() {
    // given
    var content = """
      // Параметры:
      //  Данные - НесуществующийТип123 - входные данные
      Процедура Тест(Данные) Экспорт
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(content);
    var params = new HoverParams();
    params.setPosition(new Position(1, 20)); // курсор внутри «НесуществующийТип123»

    // when
    var optionalHover = hoverProvider.getHover(documentContext, params);

    // then
    assertThat(optionalHover).isNotPresent();
  }

  @Test
  void noHoverOnDescriptionTextOutsideTypeName() {
    // given
    var documentContext = TestUtils.getDocumentContext(CONTENT);
    var params = new HoverParams();
    params.setPosition(new Position(1, 6)); // курсор на имени параметра «Данные» в описании

    // when
    var optionalHover = hoverProvider.getHover(documentContext, params);

    // then
    assertThat(optionalHover).isNotPresent();
  }
}
