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
package com.github._1c_syntax.bsl.languageserver.types.index;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextContentChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentRemovedEvent;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import java.util.concurrent.CancellationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkspaceSymbolIndexTest extends AbstractServerContextAwareTest {

  private static final CancelChecker NO_CANCEL = () -> {
    // no-op
  };

  @Autowired
  private WorkspaceSymbolIndex index;

  @Autowired
  private ApplicationEventPublisher eventPublisher;

  @Test
  void indexesSupportedSymbolsOnContentChangedEvent() {
    // given
    var documentContext = TestUtils.getDocumentContext("""
      Перем МодульнаяПеременная Экспорт;
      Процедура Тест()
      КонецПроцедуры
      """);

    // when
    eventPublisher.publishEvent(new DocumentContextContentChangedEvent(documentContext));

    // then — метод и модульная переменная попали в индекс
    var methods = index.search("Тест", 10, NO_CANCEL);
    assertThat(methods)
      .anyMatch(entry -> entry.name().equals("Тест"));

    var variables = index.search("МодульнаяПеременная", 10, NO_CANCEL);
    assertThat(variables)
      .anyMatch(entry -> entry.name().equals("МодульнаяПеременная"));
  }

  @Test
  void findsSymbolBySubstringAndSubsequence() {
    // given
    var documentContext = TestUtils.getDocumentContext("""
      Процедура ОбработкаЗаполнения()
      КонецПроцедуры
      """);

    // when
    eventPublisher.publishEvent(new DocumentContextContentChangedEvent(documentContext));

    // then — непрерывная подстрока
    assertThat(index.search("Заполнения", 10, NO_CANCEL))
      .anyMatch(entry -> entry.name().equals("ОбработкаЗаполнения"));

    // then — подпоследовательность (буквы по порядку, но не подряд)
    assertThat(index.search("ОбрЗап", 10, NO_CANCEL))
      .anyMatch(entry -> entry.name().equals("ОбработкаЗаполнения"));
  }

  @Test
  void ranksExactAndPrefixMatchesAboveSubstring() {
    // given — три символа с общей подстрокой «Тест»
    var documentContext = TestUtils.getDocumentContext("""
      Процедура Тест()
      КонецПроцедуры
      Процедура ТестДанных()
      КонецПроцедуры
      Процедура ПерезаписьТеста()
      КонецПроцедуры
      """);

    // when
    eventPublisher.publishEvent(new DocumentContextContentChangedEvent(documentContext));
    var result = index.search("Тест", 10, NO_CANCEL);

    // then — точное совпадение выше префикса, префикс выше внутренней подстроки
    var names = result.stream().map(WorkspaceSymbolIndex.Entry::name).toList();
    assertThat(names.indexOf("Тест")).isLessThan(names.indexOf("ТестДанных"));
    assertThat(names.indexOf("ТестДанных")).isLessThan(names.indexOf("ПерезаписьТеста"));
  }

  @Test
  void limitTruncatesByScoreKeepingMostRelevant() {
    // given — точное совпадение и менее релевантная подпоследовательность
    var documentContext = TestUtils.getDocumentContext("""
      Процедура Альфа()
      КонецПроцедуры
      Процедура АкварельЛаванда()
      КонецПроцедуры
      """);

    // when — лимит 1 должен оставить только самый релевантный (точное совпадение)
    eventPublisher.publishEvent(new DocumentContextContentChangedEvent(documentContext));
    var result = index.search("Альфа", 1, NO_CANCEL);

    // then
    assertThat(result)
      .hasSize(1)
      .first()
      .extracting(WorkspaceSymbolIndex.Entry::name)
      .isEqualTo("Альфа");
  }

  @Test
  void clearOnLifecycleEventRemovesDocumentSymbols() {
    // given
    var documentContext = TestUtils.getDocumentContext("""
      Процедура УникальныйСимвол()
      КонецПроцедуры
      """);
    eventPublisher.publishEvent(new DocumentContextContentChangedEvent(documentContext));
    assertThat(index.search("УникальныйСимвол", 10, NO_CANCEL)).isNotEmpty();

    // when — событие жизненного цикла чистит индекс по URI
    eventPublisher.publishEvent(
      new ServerContextDocumentRemovedEvent(documentContext.getServerContext(), documentContext.getUri()));

    // then
    assertThat(index.search("УникальныйСимвол", 10, NO_CANCEL)).isEmpty();
  }

  @Test
  void emptyQueryReturnsUpToLimit() {
    // given
    var documentContext = TestUtils.getDocumentContext("""
      Процедура Первая()
      КонецПроцедуры
      Процедура Вторая()
      КонецПроцедуры
      Процедура Третья()
      КонецПроцедуры
      """);

    // when
    eventPublisher.publishEvent(new DocumentContextContentChangedEvent(documentContext));
    var result = index.search("", 2, NO_CANCEL);

    // then
    assertThat(result).hasSize(2);
  }

  @Test
  void cancelledCheckerInterruptsSearchEventually() {
    // given — много символов, чтобы пройти порог проверки отмены
    var builder = new StringBuilder();
    for (var i = 0; i < 2000; i++) {
      builder.append("Процедура Символ").append(i).append("()\nКонецПроцедуры\n");
    }
    var documentContext = TestUtils.getDocumentContext(builder.toString());
    eventPublisher.publishEvent(new DocumentContextContentChangedEvent(documentContext));

    CancelChecker cancelChecker = () -> {
      throw new CancellationException();
    };

    // when / then
    assertThatThrownBy(() -> index.search("Символ", 10, cancelChecker))
      .isInstanceOf(CancellationException.class);
  }
}
