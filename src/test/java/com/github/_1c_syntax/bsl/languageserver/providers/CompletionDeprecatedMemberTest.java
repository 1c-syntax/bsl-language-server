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
    // given — обращение к члену HTTPЗапрос в ru-локали
    var content = "Запрос = Новый HTTPЗапрос(\"/api\");\nЗапрос.";

    // when
    var labels = labelsAt(content, content.length());

    // then — канонические члены в русском написании есть; английская сторона
    // двуязычного члена не дублируется, устаревшие [DeprecatedName]-алиасы скрыты
    assertThat(labels)
      .contains("Заголовки", "ПолучитьТелоКакДвоичныеДанные")
      .doesNotContain("Headers", "GetBodyAsBinaryData")
      .doesNotContain("GetBodyAsBinary", "SetBodyFromBinary");
  }

  @Test
  void deprecatedRussianMemberIsMarkedAndEnglishCounterpartHidden() {
    // given — обращение к члену СистемнаяИнформация в ru-локали
    var content = "СИ = Новый СистемнаяИнформация;\nСИ.";

    // when
    var items = itemsAt(content, content.length());
    var labels = items.stream().map(CompletionItem::getLabel).toList();

    // then — русское устаревшее имя видно и помечено deprecated
    var envVars = items.stream()
      .filter(it -> "ПеременныеСреды".equals(it.getLabel()))
      .findFirst()
      .orElseThrow();
    assertThat(isMarkedDeprecated(envVars))
      .as("устаревший член помечается deprecated-тегом/флагом")
      .isTrue();
    // ...а англоязычное написание в ru-локали скрыто
    assertThat(labels)
      .contains("ПеременныеСреды")
      .doesNotContain("EnvironmentVariables");
  }

  @Test
  void deprecatedLocalMethodIsMarkedInCompletion() {
    // given — #4006-follow-up: устаревший пользовательский метод (помечен
    // doc-комментарием) тоже должен получать тег deprecated в автокомплите
    var content = "// Устарела. Используйте Актуальную.\n"
      + "Процедура Старая() Экспорт\nКонецПроцедуры\n\n"
      + "Процедура Актуальная() Экспорт\n\tСтар\nКонецПроцедуры";

    // when
    var items = itemsAt(content, content.indexOf("\tСтар") + 5);

    // then
    var old = items.stream()
      .filter(it -> "Старая".equals(it.getLabel()))
      .findFirst()
      .orElseThrow();
    assertThat(isMarkedDeprecated(old))
      .as("устаревший пользовательский метод помечается deprecated")
      .isTrue();
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
