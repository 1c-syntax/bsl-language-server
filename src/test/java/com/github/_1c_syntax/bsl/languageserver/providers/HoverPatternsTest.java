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

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hover в типичных позициях: на имени метода, имени переменной, поле
 * структуры, цепочке через возврат функции с JsDoc.
 */
@CleanupContextBeforeClassAndAfterClass
class HoverPatternsTest extends AbstractServerContextAwareTest {

  @Autowired
  private HoverProvider hoverProvider;

  @Test
  void hoverOnLocalMethodNameReturnsHover() {
    // курсор на «Удвоить» в вызове.
    var hover = hoverAt("Результат = Удвоить(5)", "Результат = ".length() + 2);
    assertThat(hover).isPresent();
  }

  @Test
  void hoverOnStructureFieldAfterFunctionCall() {
    // курсор на «ID» в Свертка.ID.
    var hover = hoverAt("Идентификатор = Свертка.ID", "Идентификатор = Свертка.".length() + 1);
    assertThat(hover).isNotNull();
  }

  @Test
  void hoverOnPlatformMethodCall() {
    // курсор на «СтрДлина».
    var hover = hoverAt("Длина = СтрДлина(Стр)", "Длина = ".length() + 4);
    assertThat(hover).isNotNull();
  }

  @Test
  void hoverOnLocalProcedureNameReturnsHover() {
    var hover = hoverAt("СохранитьПользователя(\"Иван\", 30)", 5);
    assertThat(hover).isNotNull();
  }

  @Test
  void hoverOnVariableReturnsHover() {
    // курсор на «Свертка» в присваивании.
    var hover = hoverAt("Свертка = СоздатьСвернутыйОбъект()", 2);
    assertThat(hover).isNotNull();
  }

  private java.util.Optional<org.eclipse.lsp4j.Hover> hoverAt(String marker, int offsetInMarker) {
    var dc = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/HoverPatterns.bsl");
    var content = dc.getContent();
    int markerStart = content.indexOf(marker);
    int targetOffset = markerStart + offsetInMarker;
    int lineStart = content.lastIndexOf('\n', targetOffset) + 1;
    int line = content.substring(0, targetOffset).split("\n").length - 1;
    int charInLine = targetOffset - lineStart;
    var params = new HoverParams();
    params.setTextDocument(new org.eclipse.lsp4j.TextDocumentIdentifier(dc.getUri().toString()));
    params.setPosition(new Position(line, charInLine));
    return hoverProvider.getHover(dc, params);
  }
}
