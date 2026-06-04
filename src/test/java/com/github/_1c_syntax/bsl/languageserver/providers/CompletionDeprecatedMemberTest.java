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
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemTag;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Поведение dot-completion для устаревших членов платформенных типов OneScript:
 * <ul>
 *   <li>устаревший {@code [DeprecatedName]}-алиас другой локали не показывается
 *       (в ru-локали нет англоязычного {@code HTTPЗапрос.GetBodyAsBinary});</li>
 *   <li>видимый устаревший член помечается как deprecated
 *       ({@link CompletionItemTag#Deprecated} либо legacy-флаг).</li>
 * </ul>
 */
@CleanupContextBeforeClassAndAfterClass
class CompletionDeprecatedMemberTest extends AbstractServerContextAwareTest {

  @Autowired
  private CompletionProvider completionProvider;

  @Test
  void httpRequestRuCompletionHidesEnglishDeprecatedAliasesAndDuplicateLatinNames() {
    var content = "Запрос = Новый HTTPЗапрос(\"/api\");\nЗапрос.";
    var labels = labelsAt(content, content.length());

    // Канонические члены в русском написании присутствуют.
    assertThat(labels).contains("Заголовки", "ПолучитьТелоКакДвоичныеДанные");
    // Английская сторона двуязычного члена в ru-локали не дублируется.
    assertThat(labels).doesNotContain("Headers", "GetBodyAsBinaryData");
    // Устаревшие англоязычные алиасы [DeprecatedName] в ru-локали скрыты.
    assertThat(labels).doesNotContain("GetBodyAsBinary", "SetBodyFromBinary");
  }

  @Test
  void deprecatedRussianMemberIsMarkedAndEnglishCounterpartHidden() {
    var content = "СИ = Новый СистемнаяИнформация;\nСИ.";
    var items = itemsAt(content, content.length());
    var labels = items.stream().map(CompletionItem::getLabel).toList();

    // Русское устаревшее имя видно и помечено deprecated.
    var envVars = items.stream()
      .filter(it -> "ПеременныеСреды".equals(it.getLabel()))
      .findFirst()
      .orElseThrow();
    assertThat(isMarkedDeprecated(envVars))
      .as("устаревший член помечается deprecated-тегом/флагом")
      .isTrue();

    // Англоязычное устаревшее написание в ru-локали скрыто.
    assertThat(labels).doesNotContain("EnvironmentVariables");
  }

  private static boolean isMarkedDeprecated(CompletionItem item) {
    return (item.getTags() != null && item.getTags().contains(CompletionItemTag.Deprecated))
      || Boolean.TRUE.equals(item.getDeprecated());
  }

  private List<String> labelsAt(String content, int offset) {
    return itemsAt(content, offset).stream().map(CompletionItem::getLabel).toList();
  }

  private List<CompletionItem> itemsAt(String content, int offset) {
    var dc = TestUtils.getDocumentContext(TestUtils.FAKE_OSCRIPT_DOCUMENT_URI, content);
    var lineStart = content.lastIndexOf('\n', offset - 1) + 1;
    var line = content.substring(0, offset).split("\n").length - 1;
    var ch = offset - lineStart;
    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(dc.getUri().toString()));
    params.setPosition(new Position(line, ch));
    return completionProvider.getCompletion(dc, params).getItems();
  }
}
