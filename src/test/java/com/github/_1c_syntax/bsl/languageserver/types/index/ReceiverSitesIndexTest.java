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
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiverSitesIndexTest extends AbstractServerContextAwareTest {

  @Autowired
  private ReceiverSitesIndex index;

  @Autowired
  private ApplicationEventPublisher eventPublisher;

  @Test
  void groupsSitesByReceiverAndClearsOnEvents() {
    // given — вызовы, присваивание и Свойство с разными приёмниками.
    var documentContext = TestUtils.getDocumentContext("""
      Процедура Тест()
          ТЗ.Колонки.Добавить("Имя");
          Стр.Вставить("Ключ", 1);
          Сообщить("без базового идентификатора");
          Элемент.Вид = 1;
          Если Параметры.Свойство("Ключ", Значение) Тогда
          КонецЕсли;
      КонецПроцедуры
      """);
    var ast = documentContext.getAst();
    var uri = documentContext.getUri();
    var serverContext = documentContext.getServerContext();

    // then — группировка по приёмнику.
    assertAllSites(uri, ast);

    // when/then — каждое из 4 событий чистит индекс по URI: следующий запрос строит его заново
    // (списки — новые экземпляры), и построенный целиком (проверяем все приёмники, не один ключ).
    assertRebuiltAfter(uri, ast, new DocumentContextContentChangedEvent(documentContext));
    assertRebuiltAfter(uri, ast, new ServerContextDocumentClearedEvent(serverContext, documentContext));
    assertRebuiltAfter(uri, ast, new ServerContextDocumentClosedEvent(serverContext, documentContext));
    assertRebuiltAfter(uri, ast, new ServerContextDocumentRemovedEvent(serverContext, uri));
  }

  @Test
  void keepsDocumentOrderForSameReceiver() {
    // given — по два места каждого вида у одного приёмника, вперемешку и вложенно:
    // Свойство и внутри условия, и внутри выражения справа от присваивания.
    var documentContext = TestUtils.getDocumentContext("""
      Процедура Тест()
          ТЗ.Колонки.Добавить("Первая");
          ТЗ.Поле = 1;
          Если Структура.Свойство("А", ТЗ) Тогда
              ТЗ.Очистить();
              ТЗ = Неопределено;
          КонецЕсли;
          Х = Структура.Property("Б", ТЗ);
      КонецПроцедуры
      """);
    var ast = documentContext.getAst();
    var uri = documentContext.getUri();

    // then — места каждого вида в порядке следования в документе.
    assertThat(index.callStatements(uri, ast, "ТЗ")).extracting(call -> call.getStart().getLine())
      .containsExactly(2, 5);
    assertThat(index.assignments(uri, ast, "ТЗ")).extracting(assignment -> assignment.getStart().getLine())
      .containsExactly(3, 6);
    assertThat(index.propertyCalls(uri, ast, "ТЗ")).extracting(call -> call.getStart().getLine())
      .containsExactly(4, 8);
    assertThat(index.assignments(uri, ast, "Х")).hasSize(1);
  }

  @Test
  void propertyCallsTakeOnlyBareVariableAsSecondArgument() {
    // given — второй аргумент не переменная, его нет, или метод не Свойство.
    var documentContext = TestUtils.getDocumentContext("""
      Процедура Тест()
          Если Структура.Свойство("А", Объект.Поле) Тогда
          КонецЕсли;
          Если Структура.Свойство("Б") Тогда
          КонецЕсли;
          Если Структура.Найти("В", Приёмник) Тогда
          КонецЕсли;
      КонецПроцедуры
      """);
    var ast = documentContext.getAst();
    var uri = documentContext.getUri();

    // then
    assertThat(index.propertyCalls(uri, ast, "Объект")).isEmpty();
    assertThat(index.propertyCalls(uri, ast, "Поле")).isEmpty();
    assertThat(index.propertyCalls(uri, ast, "Приёмник")).isEmpty();
  }

  private void assertRebuiltAfter(URI uri, BSLParser.FileContext ast, ApplicationEvent event) {
    var callsBefore = index.callStatements(uri, ast, "ТЗ");
    var assignmentsBefore = index.assignments(uri, ast, "Элемент");
    var propertyCallsBefore = index.propertyCalls(uri, ast, "Значение");

    eventPublisher.publishEvent(event);

    assertThat(index.callStatements(uri, ast, "ТЗ")).as("построено заново").isNotSameAs(callsBefore);
    assertThat(index.assignments(uri, ast, "Элемент")).as("построено заново").isNotSameAs(assignmentsBefore);
    assertThat(index.propertyCalls(uri, ast, "Значение")).as("построено заново").isNotSameAs(propertyCallsBefore);
    assertAllSites(uri, ast);
  }

  private void assertAllSites(URI uri, BSLParser.FileContext ast) {
    assertThat(index.callStatements(uri, ast, "ТЗ")).hasSize(1);
    assertThat(index.callStatements(uri, ast, "СТР")).as("без учёта регистра").hasSize(1);
    assertThat(index.callStatements(uri, ast, "Нет")).isEmpty();
    assertThat(index.assignments(uri, ast, "элемент")).as("без учёта регистра").hasSize(1);
    assertThat(index.assignments(uri, ast, "ТЗ")).isEmpty();
    assertThat(index.propertyCalls(uri, ast, "ЗНАЧЕНИЕ")).as("без учёта регистра").hasSize(1);
    assertThat(index.propertyCalls(uri, ast, "Параметры")).isEmpty();
  }
}
