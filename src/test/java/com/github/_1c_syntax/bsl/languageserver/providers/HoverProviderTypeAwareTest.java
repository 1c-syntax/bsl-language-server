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
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тесты type-aware fallback'а в {@link HoverProvider}: член платформенного
 * типа / namespace без соответствующего source-defined символа.
 */
class HoverProviderTypeAwareTest extends AbstractServerContextAwareTest {

  private static final String PATH_TO_FILE = "./src/test/resources/types/EnumAccess.bsl";

  @Autowired
  private HoverProvider hoverProvider;

  @Test
  void hoverOnNamespaceIdentifier() {
    initServerContext("./src/test/resources/types", false);
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE, context);
    // Кодировка = КодировкаТекста.UTF8;  // строка 0
    var content = documentContext.getContent();
    var params = new HoverParams();
    params.setPosition(new Position(0, content.indexOf("КодировкаТекста") + 1));

    var hover = hoverProvider.getHover(documentContext, params);

    assertThat(hover).isPresent();
    assertThat(hover.get().getContents().getRight().getValue())
      .contains("КодировкаТекста");
  }

  @Test
  void hoverOnEnumMember() {
    initServerContext("./src/test/resources/types", false);
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE, context);
    var content = documentContext.getContent();
    var params = new HoverParams();
    params.setPosition(new Position(0, content.indexOf("UTF8") + 1));

    var hover = hoverProvider.getHover(documentContext, params);

    assertThat(hover).isPresent();
    var value = hover.get().getContents().getRight().getValue();
    assertThat(value).contains("UTF8");
    assertThat(value).contains("КодировкаТекста");
  }

  @Test
  void hoverOnChainedMethodCall() {
    initServerContext("./src/test/resources/types", false);
    var documentContext = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/ChainedAccessors.os", context);
    var content = documentContext.getContent();
    // курсор внутри "Количество" в строке "Количество = СоздатьМассив().Количество();"
    var idx = content.indexOf(".Количество()");
    assertThat(idx).isGreaterThan(0);
    // line: считаем строку этого индекса
    var prefix = content.substring(0, idx);
    var line = (int) prefix.chars().filter(c -> c == '\n').count();
    var lineStart = prefix.lastIndexOf('\n') + 1;
    var col = idx - lineStart + 2; // +1 чтобы выйти за точку, попасть на 'К'

    var params = new HoverParams();
    params.setPosition(new Position(line, col));

    var hover = hoverProvider.getHover(documentContext, params);

    assertThat(hover).isPresent();
    var value = hover.get().getContents().getRight().getValue();
    assertThat(value).contains("Количество");
    assertThat(value).contains("Массив");
  }
}
