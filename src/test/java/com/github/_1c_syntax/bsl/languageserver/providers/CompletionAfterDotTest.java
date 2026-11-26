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
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * dot-completion на разных типах: Структура, Массив, ТЗ, Соответствие,
 * Дата, УникальныйИдентификатор. Проверяет что provider возвращает что-то
 * (не падает).
 */
@CleanupContextBeforeClassAndAfterClass
class CompletionAfterDotTest extends AbstractServerContextAwareTest {

  @Autowired
  private CompletionProvider completionProvider;

  @Test
  void completionAfterStructureDotDoesNotCrash() {
    var augmented = baseContent().replace("УИД = Новый УникальныйИдентификатор;",
      "УИД = Новый УникальныйИдентификатор;\n\tСтр.");
    var result = completeAt(augmented, augmented.lastIndexOf("Стр.") + 4);
    assertThat(result).isNotNull();
  }

  @Test
  void completionAfterArrayDotDoesNotCrash() {
    var augmented = baseContent().replace("УИД = Новый УникальныйИдентификатор;",
      "УИД = Новый УникальныйИдентификатор;\n\tМ.");
    var result = completeAt(augmented, augmented.lastIndexOf("\tМ.") + 3);
    assertThat(result).isNotNull();
  }

  @Test
  void completionAfterValueTableDotDoesNotCrash() {
    var augmented = baseContent().replace("УИД = Новый УникальныйИдентификатор;",
      "УИД = Новый УникальныйИдентификатор;\n\tТЗ.");
    var result = completeAt(augmented, augmented.lastIndexOf("\tТЗ.") + 4);
    assertThat(result).isNotNull();
  }

  @Test
  void completionAfterMapDotDoesNotCrash() {
    var augmented = baseContent().replace("УИД = Новый УникальныйИдентификатор;",
      "УИД = Новый УникальныйИдентификатор;\n\tСо.");
    var result = completeAt(augmented, augmented.lastIndexOf("\tСо.") + 4);
    assertThat(result).isNotNull();
  }

  @Test
  void completionAfterDateDotDoesNotCrash() {
    var augmented = baseContent().replace("УИД = Новый УникальныйИдентификатор;",
      "УИД = Новый УникальныйИдентификатор;\n\tД.");
    var result = completeAt(augmented, augmented.lastIndexOf("\tД.") + 3);
    assertThat(result).isNotNull();
  }

  @Test
  void completionAfterUuidDotDoesNotCrash() {
    var augmented = baseContent().replace("УИД = Новый УникальныйИдентификатор;",
      "УИД = Новый УникальныйИдентификатор;\n\tУИД.");
    var result = completeAt(augmented, augmented.lastIndexOf("\tУИД.") + 5);
    assertThat(result).isNotNull();
  }

  private String baseContent() {
    var dc = TestUtils.getDocumentContextFromFile(
      "./src/test/resources/types/CompletionAfterDot.bsl");
    return dc.getContent();
  }

  private CompletionList completeAt(String content, int offset) {
    var dc = TestUtils.getDocumentContext(content);
    var lineStart = content.lastIndexOf('\n', offset - 1) + 1;
    var line = content.substring(0, offset).split("\n").length - 1;
    var ch = offset - lineStart;
    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(dc.getUri().toString()));
    params.setPosition(new Position(line, ch));
    return completionProvider.getCompletion(dc, params);
  }
}
