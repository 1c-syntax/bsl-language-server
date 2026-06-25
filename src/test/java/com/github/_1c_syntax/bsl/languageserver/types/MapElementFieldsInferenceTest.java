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
 * #4194 (отчёт alisher-nil): `Соответствие из КлючИЗначение` с описанными полями
 * элемента (* Ключ - Строка, * Значение - Число). Тип присваиваемой переменной
 * считается верно, но при обходе `Для Каждого` тип элемента теряется.
 */
@CleanupContextBeforeClassAndAfterClass
class MapElementFieldsInferenceTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeService typeService;

  @Test
  void mapElementCarriesDeclaredFields() {
    // Тип элемента Соответствия (КлючИЗначение) должен нести поля Ключ/Значение.
    var declared = typeService.getDeclaredReturnTypes(method("Тело"));
    var mapRef = declared.refs().iterator().next();
    var element = declared.getElementTypes(mapRef);

    assertThat(element.getAllFieldNames())
      .as("поля элемента КлючИЗначение не должны оседать на самом Соответствии")
      .contains("Ключ", "Значение");
  }

  @Test
  void forEachElementKeyType() {
    var t = at("КлючЭлемента = Элемент.Ключ", "КлючЭлемента = ".length());
    assertThat(t.refs())
      .as("Элемент.Ключ при обходе Соответствия разрешается в Строку")
      .extracting(TypeRef::qualifiedName)
      .contains("Строка");
  }

  @Test
  void forEachElementValueType() {
    var t = at("ЗначениеЭлемента = Элемент.Значение", "ЗначениеЭлемента = ".length());
    assertThat(t.refs())
      .as("Элемент.Значение при обходе Соответствия разрешается в Число")
      .extracting(TypeRef::qualifiedName)
      .contains("Число");
  }

  private com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol method(String name) {
    return doc().getSymbolTree().getMethods().stream()
      .filter(m -> m.getName().equalsIgnoreCase(name))
      .findFirst()
      .orElseThrow();
  }

  private TypeSet at(String marker, int offsetInMarker) {
    var dc = doc();
    var content = dc.getContent();
    int markerStart = content.indexOf(marker);
    int targetOffset = markerStart + offsetInMarker;
    int lineStart = content.lastIndexOf('\n', targetOffset) + 1;
    int line = content.substring(0, targetOffset).split("\n").length - 1;
    int charInLine = targetOffset - lineStart;
    return typeService.expressionTypesAt(dc, new Position(line, charInLine + 1));
  }

  private DocumentContext doc() {
    return TestUtils.getDocumentContextFromFile("./src/test/resources/types/MapElementFields.bsl");
  }
}
