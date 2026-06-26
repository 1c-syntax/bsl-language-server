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
package com.github._1c_syntax.bsl.languageserver.context.computer;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SymbolDescriptionIndexTest extends AbstractServerContextAwareTest {

  private static final String SOURCE = """
    // Описание процедуры.
    //
    // Параметры:
    //  П1 - Строка - описание параметра.
    Процедура Тест(П1) Экспорт
    КонецПроцедуры
    """;

  @Autowired
  private SymbolDescriptionIndex index;

  @Test
  void openedDocumentCachesMethodDescription() {
    var documentContext = TestUtils.getDocumentContext(SOURCE);
    documentContext.getServerContext().openDocument(documentContext, SOURCE, 1);
    var comments = documentContext.getComments();

    var first = index.methodDescription(documentContext, comments);
    var second = index.methodDescription(documentContext, comments);

    assertThat(second).as("у открытого документа описание берётся из кэша").isSameAs(first);
  }

  @Test
  void notOpenedDocumentBypassesCache() {
    var documentContext = TestUtils.getDocumentContext(SOURCE);
    var comments = documentContext.getComments();

    var first = index.methodDescription(documentContext, comments);
    var second = index.methodDescription(documentContext, comments);

    assertThat(second).as("не-открытый документ разбирается каждый раз без кэша").isNotSameAs(first);
  }

  @Test
  void variableDescriptionWorksForOpenedAndNotOpenedDocuments() {
    var varSource = """
      // Описание переменной модуля.
      Перем КэшЗначений Экспорт;
      """;
    // Открытый документ — путь через кэш: повторный вызов отдаёт тот же экземпляр.
    var opened = TestUtils.getDocumentContext(varSource);
    opened.getServerContext().openDocument(opened, varSource, 1);
    var openedFirst = index.variableDescription(opened, opened.getComments(), Optional.empty());
    var openedSecond = index.variableDescription(opened, opened.getComments(), Optional.empty());
    assertThat(openedFirst).as("описание переменной открытого документа получено").isNotNull();
    assertThat(openedSecond).as("у открытого документа описание переменной берётся из кэша")
      .isSameAs(openedFirst);

    // Не-открытый документ — прямой разбор мимо кэша. Проверяем только получение: identity-проверка
    // (isNotSameAs) здесь ненадёжна, т.к. VariableDescription.create для одинакового входа может
    // возвращать общий/интернированный экземпляр (в отличие от MethodDescription).
    var notOpened = TestUtils.getDocumentContext(varSource);
    assertThat(index.variableDescription(notOpened, notOpened.getComments(), Optional.empty()))
      .as("описание переменной не-открытого документа получено напрямую").isNotNull();
  }

  @Test
  void closingDocumentClearsCache() {
    var documentContext = TestUtils.getDocumentContext(SOURCE);
    var serverContext = documentContext.getServerContext();
    serverContext.openDocument(documentContext, SOURCE, 1);
    var comments = documentContext.getComments();
    var cached = index.methodDescription(documentContext, comments);

    // when — закрытие документа публикует ServerContextDocumentClosedEvent.
    serverContext.closeDocument(documentContext);
    serverContext.openDocument(documentContext, SOURCE, 2);
    var afterReopen = index.methodDescription(documentContext, comments);

    // then — кэш был сброшен на закрытии, поэтому описание разобрано заново.
    assertThat(afterReopen).as("закрытие документа сбрасывает его кэш описаний").isNotSameAs(cached);
  }

  @Test
  void removingAndClearingDocumentDropCacheWithoutError() {
    var documentContext = TestUtils.getDocumentContext(SOURCE);
    var serverContext = documentContext.getServerContext();
    serverContext.openDocument(documentContext, SOURCE, 1);
    index.methodDescription(documentContext, documentContext.getComments());

    // Cleared/Removed-события (tryClearDocument закрытого документа и removeDocument) тоже сбрасывают
    // запись индекса по URI. Здесь проверяем, что обработчики отрабатывают без ошибок.
    serverContext.closeDocument(documentContext);
    assertThat(serverContext.tryClearDocument(documentContext)).isTrue();
    serverContext.removeDocument(documentContext.getUri());

    var reopened = serverContext.addDocument(documentContext.getUri());
    serverContext.openDocument(reopened, SOURCE, 1);
    List<org.antlr.v4.runtime.Token> comments = reopened.getComments();
    assertThat(index.methodDescription(reopened, comments)).isNotNull();
  }
}
