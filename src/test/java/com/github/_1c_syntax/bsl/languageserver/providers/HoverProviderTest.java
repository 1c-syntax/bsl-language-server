/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class HoverProviderTest {

  @Autowired
  private HoverProvider hoverProvider;

  @Test
  void getEmptyHover() {
    HoverParams params = new HoverParams();
    params.setPosition(new Position(0, 0));

    DocumentContext documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/providers/hover.bsl");
    Optional<Hover> optionalHover = hoverProvider.getHover(documentContext, params);

    assertThat(optionalHover).isNotPresent();
  }

  @Test
  void testLocalMethods() {
    // given
    DocumentContext documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/providers/hover.bsl");

    HoverParams params = new HoverParams();
    params.setPosition(new Position(15, 0));

    // when
    Optional<Hover> optionalHover = hoverProvider.getHover(documentContext, params);

    assertThat(optionalHover).isPresent();

    var hover = optionalHover.get();
    var content = hover.getContents().getRight().getValue();

    assertThat(content)
      .contains("providers/hover.bsl")
      .contains("```bsl\nФункция ИмяФункции(Знач П1: Дата | Число, П2: Число = -10, Знач П3: Строка = \"\", П4, ПДата = '20100101', ПДатаВремя = '20110101121212', П6 = Ложь, П7 = Истина, П8 = Неопределено, П9 = NULL) Экспорт");
  }

  @Test
  @Disabled
  void testHoverOnMethodDefinitionOfLocalModule() {
    // todo
  }

  @Test
  @Disabled
  void testMethodsFromManagerModule() {
    // todo
    //    hover.contents[0].should.has.a.key("_value").which.startWith("Метод из")
    //      .and.endWith("Document/Ext/ManagerModule.bsl");
    //    hover.contents[2].should.has.a.key("_value").which.is
    //      .equal("```bsl\nПроцедура ПроцедураМодуляМенеджера()\n```\n");
  }

  @Test
  @Disabled
  void testMethodsFromCommonModule() {
    // todo
  }

  @Test
  @Disabled
  void testMethodsFromGlobalContext() {
    // todo
    //    hover.contents[0].should.has.a.key("_value").which.startWith("Метод глобального контекста");
    //    hover.contents[2].should.has.a.key("_value").which.startWith("```bsl\nПроцедура Сообщить(");
    //    hover.contents[3].should.has.a.key("_value").which.startWith("***ТекстСообщения***");
  }

}
