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
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentClearedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentClosedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentRemovedEvent;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class CallStatementByReceiverIndexTest extends AbstractServerContextAwareTest {

  @Autowired
  private CallStatementByReceiverIndex index;

  @Autowired
  private ApplicationEventPublisher eventPublisher;

  @Test
  void groupsCallStatementsByReceiverAndClearsOnEvents() {
    // given — callStatement'ы с разными базовыми идентификаторами.
    var documentContext = TestUtils.getDocumentContext("""
      Процедура Тест()
          ТЗ.Колонки.Добавить("Имя");
          Стр.Вставить("Ключ", 1);
          Сообщить("без базового идентификатора");
      КонецПроцедуры
      """);
    var ast = documentContext.getAst();
    var uri = documentContext.getUri();
    var serverContext = documentContext.getServerContext();

    // then — группировка по базовому идентификатору.
    assertAllReceivers(uri, ast);

    // when/then — каждое из 4 событий чистит индекс по URI; после него
    // индекс пересобирается целиком (проверяем все ресиверы, не один ключ).
    eventPublisher.publishEvent(new DocumentContextContentChangedEvent(documentContext));
    assertAllReceivers(uri, ast);

    eventPublisher.publishEvent(new ServerContextDocumentClearedEvent(serverContext, documentContext));
    assertAllReceivers(uri, ast);

    eventPublisher.publishEvent(new ServerContextDocumentClosedEvent(serverContext, documentContext));
    assertAllReceivers(uri, ast);

    eventPublisher.publishEvent(new ServerContextDocumentRemovedEvent(serverContext, uri));
    assertAllReceivers(uri, ast);
  }

  @Test
  void aggregatesMultipleCallsForSameReceiver() {
    // given — два callStatement'а с одним базовым идентификатором ТЗ.
    var documentContext = TestUtils.getDocumentContext("""
      Процедура Тест()
          ТЗ.Колонки.Добавить("Имя");
          ТЗ.Очистить();
      КонецПроцедуры
      """);
    var ast = documentContext.getAst();
    var uri = documentContext.getUri();

    // then — оба вызова сгруппированы под одним ресивером.
    assertThat(index.byReceiver(uri, ast, "ТЗ")).hasSize(2);
  }

  private void assertAllReceivers(URI uri, BSLParser.FileContext ast) {
    assertThat(index.byReceiver(uri, ast, "ТЗ")).hasSize(1);
    assertThat(index.byReceiver(uri, ast, "СТР")).as("без учёта регистра").hasSize(1);
    assertThat(index.byReceiver(uri, ast, "Нет")).isEmpty();
  }
}
