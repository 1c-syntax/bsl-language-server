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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Индексатор соответствия: {@code Соответствие[Ключ]} — это значение по ключу.
 * <p>
 * Парой «ключ и значение» соответствие отдаётся только обходу {@code Для Каждого}, и
 * подставлять её результату индексатора нельзя: обращение к свойству значения выглядело
 * бы тогда обращением к несуществующему члену пары.
 */
@CleanupContextBeforeClassAndAfterClass
class MapIndexAccessTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @Test
  void indexAccessOnMapIsNotKeyAndValuePair() {
    // given / when: ключи соответствия в коде не вставлялись, поэтому их состав неизвестен.
    var types = at("Правило = Правила[ИмяПравила]", "Правило = ".length());

    // then
    var names = types.refs().stream().map(TypeRef::qualifiedName).toList();
    assertThat(names.contains("КлючИЗначение"))
      .as("значение по ключу — не пара «ключ и значение», получено %s", names)
      .isFalse();
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
    return TestUtils.getDocumentContextFromFile("./src/test/resources/types/MapIndexAccess.bsl");
  }
}
