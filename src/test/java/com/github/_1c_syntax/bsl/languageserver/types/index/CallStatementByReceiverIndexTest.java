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
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.bsl.languageserver.lsp.BSLTextDocumentService;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
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

  @Autowired
  private BSLTextDocumentService textDocumentService;

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

  @Test
  void clearsOnDidCloseFromThreadWithoutWorkspaceContext() throws InterruptedException {
    // given — документ с ресивером ТЗ; запросы к индексу идут в workspace документа.
    var documentContext = TestUtils.getDocumentContext("""
      Процедура Тест()
          ТЗ.Колонки.Добавить("Имя");
      КонецПроцедуры
      """);
    var uri = documentContext.getUri();
    var serverContext = documentContext.getServerContext();
    WorkspaceContextHolder.set(serverContext.getWorkspaceUri());

    assertThat(index.byReceiver(uri, documentContext.getAst(), "ТЗ")).hasSize(1);

    // второй документ без ТЗ — его AST играет роль «свежего» дерева первого после закрытия.
    var otherUri = Absolute.path("src/test/resources/empty-workspace/fake-uri-other.bsl").toUri();
    var otherDocument = TestUtils.getDocumentContext(otherUri, """
      Процедура Тест()
          Сообщить("без ресивера");
      КонецПроцедуры
      """, serverContext);
    var freshAst = otherDocument.getAst();

    // when — textDocument/didClose приходит с потока LSP4J, на котором
    // workspace-контекст не установлен.
    var params = new DidCloseTextDocumentParams(TestUtils.getTextDocumentIdentifier(uri));
    var closer = new Thread(() -> textDocumentService.didClose(params));
    closer.start();
    closer.join();

    // then — событие закрытия дошло до workspace-scoped слушателя: индекс по URI сброшен
    // и пересобирается по переданному AST, а не отдаёт узлы старого дерева.
    assertThat(index.byReceiver(uri, freshAst, "ТЗ")).isEmpty();
  }

  private void assertAllReceivers(URI uri, BSLParser.FileContext ast) {
    assertThat(index.byReceiver(uri, ast, "ТЗ")).hasSize(1);
    assertThat(index.byReceiver(uri, ast, "СТР")).as("без учёта регистра").hasSize(1);
    assertThat(index.byReceiver(uri, ast, "Нет")).isEmpty();
  }
}
