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
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Автодополнение по элементу {@code Соответствие из КлючИЗначение}: для
 * переменной цикла {@code Для Каждого Элемент Из ...} после точки должны
 * предлагаться {@code Ключ}/{@code Значение} с типами из JsDoc-возврата функции
 * (Строка/Число), а не бестиповые одноимённые члены платформы (#4206).
 */
@CleanupContextBeforeClassAndAfterClass
class MapElementCompletionTest extends AbstractServerContextAwareTest {

  @Autowired
  private CompletionProvider completionProvider;

  @Test
  void completionAfterDotExposesMapElementFieldsWithTypes() {
    var dc = mapDoc();
    var items = completionAfter(dc, "\t\tЭлемент.");

    var keyItem = items.stream().filter(item -> "Ключ".equals(item.getLabel()))
      .findFirst().orElseThrow();
    var valueItem = items.stream().filter(item -> "Значение".equals(item.getLabel()))
      .findFirst().orElseThrow();

    // Тип поля из JsDoc должен сохраниться в подсказке (detail), а не пропасть
    // из-за затенения одноимённым бестиповым членом платформы.
    assertThat(keyItem.getDetail()).contains("Строка");
    assertThat(valueItem.getDetail()).contains("Число");
  }

  @Test
  void completionItemCarriesMapElementFieldDescription() {
    var dc = mapDoc();
    var items = completionAfter(dc, "\t\tЭлемент.");

    var keyItem = items.stream().filter(item -> "Ключ".equals(item.getLabel()))
      .findFirst().orElseThrow();
    var documentation = keyItem.getDocumentation();
    var docText = documentation == null ? ""
      : documentation.isRight() ? documentation.getRight().getValue() : documentation.getLeft();
    assertThat(docText).contains("ключ элемента");
  }

  private DocumentContext mapDoc() {
    return TestUtils.getDocumentContextFromFile("./src/test/resources/types/MapElementCompletion.bsl");
  }

  private java.util.List<CompletionItem> completionAfter(DocumentContext dc, String marker) {
    var content = dc.getContent();
    var offset = content.indexOf(marker) + marker.length();
    var lineStart = content.lastIndexOf('\n', offset - 1) + 1;
    var line = content.substring(0, offset).split("\n", -1).length - 1;
    var ch = offset - lineStart;
    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(dc.getUri().toString()));
    params.setPosition(new Position(line, ch));
    return completionProvider.getCompletion(dc, params).getItems();
  }
}
